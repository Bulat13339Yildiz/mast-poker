package app.mast.poker.practice.sim

import app.mast.poker.content.Position
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Deck
import app.mast.poker.core.poker.HandDescriber
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.Showdown
import app.mast.poker.core.poker.StartingHand
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** One player at the session table; [style] is null for the hero. */
class TablePlayer(
    val seat: Int,
    val style: VillainStyle?,
    val position: Position,
    val cards: List<Card>,
    var stack: Int,
) {
    var inHand = true
    var street = 0
    var total = 0
    var acted = false
    var lastAction: String? = null
    val isHero: Boolean get() = style == null
    val name: String get() = if (isHero) "Ты" else position.short
}

/** What the table looks like for the UI. */
data class TableView(
    val players: List<PlayerView>,
    val board: List<Card>,
    val pot: Int,
    val toCall: Int,
    val heroStack: Int,
    val options: List<HeroOption>,
    val lastLog: String,
    val result: SimResult?,
    val verdicts: List<Verdict>,
    val history: List<HistoryEntry>,
    val heroPosition: Position,
)

data class PlayerView(
    val seat: Int,
    val position: Position,
    val style: VillainStyle?,
    val stack: Int,
    val inHand: Boolean,
    val cards: List<Card>?,
    val action: String?,
)

/**
 * A full 6-max hand for the session mode: blinds 1/2, five bots with their styles and the
 * hero, whose stack carries over between hands. Bots act preflop by their ranges and after
 * the flop by hand strength, so pots can be three- or four-way. Bets never exceed what the
 * hero can still put in, so there are no side pots.
 */
class TableHand(
    private val random: Random,
    heroStack: Int,
    /** Table seat that has the button this hand; seat 0 is the hero. */
    buttonSeat: Int,
    /** Bot styles by table seat 1..5 (index 0 unused). */
    styles: List<VillainStyle?>,
) {
    private val deck = Deck.shuffled(random)
    val players: List<TablePlayer>
    private val fullBoard: List<Card>
    var street = SimStreet.PREFLOP
        private set
    val board: List<Card> get() = fullBoard.take(street.boardCards)
    var pot = 0
        private set
    private var currentBet = 0
    private var raises = 0
    private var limpers = 0
    private var callersAfterRaise = 0
    private var aggressor: TablePlayer? = null
    private var queue = ArrayDeque<TablePlayer>()
    var facing = Facing.OPEN
        private set
    var awaitingHero = false
        private set
    var result: SimResult? = null
        private set
    val log = mutableListOf<String>()
    val history = mutableListOf<HistoryEntry>()
    val verdicts = mutableListOf<Verdict>()
    private var pendingVerdict: Verdict? = null

    val hero: TablePlayer get() = players.first { it.isHero }
    private val heroStartStack = heroStack

    init {
        val cycle = listOf(Position.BTN, Position.SB, Position.BB, Position.UTG, Position.MP, Position.CO)
        players = (0 until 6).map { seat ->
            val position = cycle[Math.floorMod(seat - buttonSeat, 6)]
            TablePlayer(seat, if (seat == 0) null else styles[seat], position, listOf(deck.removeLast(), deck.removeLast()), if (seat == 0) heroStack else 1000)
        }.sortedBy { HandSimulator.PreflopOrder.indexOf(it.position) }
        fullBoard = List(5) { deck.removeLast() }
        post(players.first { it.position == Position.SB }, 1)
        post(players.first { it.position == Position.BB }, 2)
        history += HistoryEntry.Street(SimStreet.PREFLOP, emptyList(), pot)
        line(Who.TABLE, "Блайнды 1/2. Ты — ${hero.position.ruName.lowercase()} (${hero.position.short}), стек ${hero.stack + hero.total}.")
        currentBet = 2
        queue = ArrayDeque(players)
        advance()
    }

    // --- Money and log ---------------------------------------------------------------------

    private fun post(p: TablePlayer, amount: Int) {
        val a = min(amount, p.stack)
        p.stack -= a
        p.street += a
        p.total += a
        pot += a
    }

    /** The most anyone may have in front of them this street while the hero is still in. */
    private val cap: Int get() = if (hero.inHand) hero.street + hero.stack else Int.MAX_VALUE

    private fun line(who: Who, text: String) {
        log += text
        history += HistoryEntry.Line(who, text, if (who == Who.HERO) pendingVerdict.also { pendingVerdict = null } else null)
    }

    private fun label(p: TablePlayer) = if (p.isHero) "Ты" else "${p.position.short} (${p.style!!.ruName.lowercase()})"

    private val active: List<TablePlayer> get() = players.filter { it.inHand }
    val toCall: Int get() = min(currentBet - hero.street, hero.stack).coerceAtLeast(0)
    private val opponents: Int get() = active.count { !it.isHero }

    // --- Flow ------------------------------------------------------------------------------

    /** Runs bots until it is the hero's turn or the hand is over. */
    private fun advance() {
        while (result == null) {
            if (!hero.inHand) {
                finish(-hero.total, "Ты вышел из раздачи.", showdown = false)
                return
            }
            if (active.size == 1) {
                finish(pot - hero.total, "Все сбросили — банк твой.", showdown = false)
                return
            }
            val next = queue.removeFirstOrNull()
            if (next == null) {
                nextStreet()
                continue
            }
            if (!next.inHand || next.stack == 0) continue
            if (next.acted && next.street >= currentBet) continue
            if (next.isHero) {
                if (hero.stack == 0) { hero.acted = true; continue }
                queue.addFirst(next)
                facing = heroFacing()
                awaitingHero = true
                return
            }
            botActs(next)
        }
    }

    private fun requeueAfter(raiser: TablePlayer) {
        val order = if (street == SimStreet.PREFLOP) players else postflopOrder()
        val i = order.indexOf(raiser)
        queue = ArrayDeque((order.drop(i + 1) + order.take(i)).filter { it.inHand })
    }

    private fun postflopOrder() = players.sortedBy { HandSimulator.PostflopOrder.indexOf(it.position) }

    private fun nextStreet() {
        if (street == SimStreet.RIVER || hero.stack == 0 && active.count { it.stack > 0 } <= 1) {
            // Run out the remaining cards when nobody can bet any more.
            if (street != SimStreet.RIVER && hero.stack == 0) {
                while (street != SimStreet.RIVER) dealStreet()
            }
            showdown()
            return
        }
        dealStreet()
        queue = ArrayDeque(postflopOrder().filter { it.inHand })
    }

    private fun dealStreet() {
        players.forEach { it.street = 0; it.acted = false; it.lastAction = null }
        currentBet = 0
        raises = 0
        street = SimStreet.entries[street.ordinal + 1]
        val fresh = board.takeLast(if (street == SimStreet.FLOP) 3 else 1)
        log += "${street.ruName}: ${fresh.joinToString(" ")}"
        history += HistoryEntry.Street(street, fresh, pot)
    }

    private fun finish(net: Int, text: String, showdown: Boolean) {
        line(Who.TABLE, text)
        result = SimResult(net, pot, text, showdown)
        awaitingHero = false
    }

    private fun showdown() {
        val live = active
        val r = Showdown.resolve(fullBoard, live.map { it.cards })
        val winners = r.winners.map { live[it] }
        val share = pot / winners.size
        val heroWins = hero in winners
        val heroHand = HandDescriber.describe(r.values[live.indexOf(hero)])
        val best = HandDescriber.describe(r.values[r.winners.first()])
        val text = when {
            heroWins && winners.size > 1 -> "Ничья: у тебя $heroHand. Банк делится."
            heroWins -> "Вскрытие: у тебя $heroHand — банк твой!"
            else -> "Вскрытие: у ${label(winners.first())} $best, у тебя $heroHand."
        }
        finish(if (heroWins) share - hero.total else -hero.total, text, showdown = true)
    }

    // --- Hero ------------------------------------------------------------------------------

    private fun heroFacing(): Facing {
        if (street != SimStreet.PREFLOP) {
            val behindHeroActed = currentBet > 0
            return when {
                currentBet == 0 && postflopOrder().filter { it.inHand }.first() == hero -> Facing.FIRST
                currentBet == 0 -> Facing.CHECKED_TO
                hero.street > 0 && behindHeroActed -> Facing.RAISE
                else -> Facing.BET
            }
        }
        return when {
            raises == 0 && limpers == 0 -> Facing.OPEN
            raises == 0 -> if (hero.position == Position.BB) Facing.LIMPED_TO_BB else Facing.VS_LIMP
            hero.street > 2 && raises >= 3 -> Facing.FOUR_BET
            hero.street > 2 -> Facing.THREE_BET
            raises == 1 -> Facing.VS_OPEN
            else -> Facing.THREE_BET
        }
    }

    private fun raiseTo(): Int = when (facing) {
        Facing.OPEN -> 6
        Facing.VS_LIMP -> 8 + 2 * (limpers - 1)
        Facing.LIMPED_TO_BB -> 10 + 2 * (limpers - 1)
        Facing.VS_OPEN -> (if (outOfPosition()) 4 else 3) * currentBet + currentBet * callersAfterRaise
        else -> (currentBet * if (street == SimStreet.PREFLOP) 2.3 else 3.0).roundToInt()
    }.coerceAtMost(cap)

    private fun outOfPosition(): Boolean {
        val raiser = aggressor ?: return false
        return HandSimulator.PostflopOrder.indexOf(hero.position) < HandSimulator.PostflopOrder.indexOf(raiser.position)
    }

    private fun betSize() = max(2, (pot / 2.0).roundToInt()).coerceAtMost(hero.stack)

    fun options(): List<HeroOption> {
        val call = toCall
        val allInCall = call == hero.stack && call > 0
        val callLabel = if (allInCall) "Колл (олл-ин) $call" else "Колл $call"
        fun raiseOption(prefix: String): HeroOption? {
            val to = raiseTo()
            if (to <= currentBet || hero.stack <= call) return null
            return HeroOption(Act.RAISE, if (to == cap) "Олл-ин $to" else "$prefix $to")
        }
        return when (facing) {
            Facing.OPEN -> listOfNotNull(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, if (call == 0) "Чек" else "Колл $call"), raiseOption("Рейз до"))
            Facing.VS_LIMP -> listOfNotNull(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $call"), raiseOption("Рейз до"))
            Facing.LIMPED_TO_BB -> listOfNotNull(HeroOption(Act.CHECK, "Чек"), raiseOption("Рейз до"))
            Facing.VS_OPEN -> listOfNotNull(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, callLabel), raiseOption("3-бет до"))
            Facing.THREE_BET -> listOfNotNull(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, callLabel), raiseOption("4-бет до"))
            Facing.FOUR_BET -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, callLabel))
            Facing.FIRST, Facing.CHECKED_TO -> listOfNotNull(HeroOption(Act.CHECK, "Чек"), betSize().takeIf { it > 0 }?.let { HeroOption(Act.BET, if (it == hero.stack) "Олл-ин $it" else "Бет $it") })
            Facing.BET -> listOfNotNull(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, callLabel), raiseOption("Рейз до"))
            Facing.RAISE -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, callLabel))
        }
    }

    fun act(act: Act): Verdict {
        check(awaitingHero && result == null)
        require(options().any { it.act == act }) { "Illegal $act when $facing" }
        val v = grade(act)
        verdicts += v
        pendingVerdict = v
        queue.removeFirstOrNull()
        awaitingHero = false
        val h = hero
        when (act) {
            Act.FOLD -> {
                h.inHand = false
                line(Who.HERO, "Ты сбрасываешь.")
            }
            Act.CHECK -> line(Who.HERO, "Ты чекаешь.")
            Act.CALL -> {
                val call = toCall
                post(h, call)
                line(Who.HERO, if (call == 0) "Ты чекаешь." else if (h.stack == 0) "Ты уравниваешь $call — олл-ин." else "Ты уравниваешь $call.")
                if (street == SimStreet.PREFLOP && raises == 0) limpers++
            }
            Act.BET -> {
                val size = betSize()
                post(h, size)
                currentBet = h.street
                aggressor = h
                line(Who.HERO, if (h.stack == 0) "Ты ставишь олл-ин $size." else "Ты ставишь $size.")
                requeueAfter(h)
            }
            Act.RAISE -> {
                val to = raiseTo()
                post(h, to - h.street)
                currentBet = h.street
                raises++
                aggressor = h
                callersAfterRaise = 0
                line(Who.HERO, if (h.stack == 0) "Ты идёшь олл-ин: $to." else "Ты повышаешь до $to.")
                requeueAfter(h)
            }
        }
        h.acted = true
        advance()
        return v
    }

    private fun grade(act: Act): Verdict {
        val hand = StartingHand.of(hero.cards[0], hero.cards[1])
        val villainStyle = aggressor?.style ?: players.filter { it.inHand && !it.isHero }.firstNotNullOfOrNull { it.style } ?: VillainStyle.TAG
        return when (facing) {
            Facing.OPEN -> Coach.preflopOpen(hand, hero.position, act, players.first { it.position == Position.BB }.style)
            Facing.VS_OPEN -> Coach.preflopVsOpen(hand, hero.position, aggressor?.position ?: Position.UTG, villainStyle, act, callers = callersAfterRaise)
            Facing.VS_LIMP, Facing.LIMPED_TO_BB -> Coach.preflopVsLimp(hand, hero.position, act)
            Facing.THREE_BET -> if (hero.street <= 2) Coach.preflopVsColdThreeBet(hand, act) else Coach.preflopVs3bet(hand, act, villainStyle)
            Facing.FOUR_BET -> Coach.preflopVs4bet(hand, act, villainStyle)
            else -> Coach.postflop(
                Coach.Spot(
                    street = street.ruName,
                    facing = facing,
                    read = HandReader.read(hero.cards, board),
                    pot = pot,
                    toCall = toCall,
                    heroAggressor = aggressor == hero,
                    wet = HandReader.isWet(board),
                    river = street == SimStreet.RIVER,
                    unseen = 52 - 2 - board.size,
                    style = if (opponents == 1) villainStyle else null,
                    opponents = opponents,
                ),
                act,
            )
        }
    }

    // --- Bots ------------------------------------------------------------------------------

    private fun hand(p: TablePlayer) = StartingHand.of(p.cards[0], p.cards[1])

    private fun botActs(p: TablePlayer) {
        val style = p.style!!
        if (street == SimStreet.PREFLOP) botPreflop(p, style) else botPostflop(p, style)
        p.acted = true
    }

    private fun botFold(p: TablePlayer) {
        p.inHand = false
        p.lastAction = "Пас"
        // Folds before the hero acts are summarised; the rest are worth a line.
        line(Who.VILLAIN, "${label(p)} сбрасывает.")
    }

    private fun botCall(p: TablePlayer) {
        val call = min(currentBet - p.street, p.stack)
        post(p, call)
        p.lastAction = if (call == 0) "Чек" else "Колл"
        line(Who.VILLAIN, if (call == 0) "${label(p)} чекает." else "${label(p)} уравнивает $call.")
    }

    private fun botRaise(p: TablePlayer, to: Int, verb: String, bet: Boolean = false) {
        val target = min(to, cap)
        if (target <= currentBet) {
            botCall(p)
            return
        }
        post(p, target - p.street)
        currentBet = p.street
        if (!bet) raises++
        aggressor = p
        callersAfterRaise = 0
        p.lastAction = if (bet) "Бет" else verb.replaceFirstChar { it.uppercase() }
        line(Who.VILLAIN, if (bet) "${label(p)} ставит $target." else "${label(p)} $verb до $target.")
        requeueAfter(p)
    }

    private fun botPreflop(p: TablePlayer, s: VillainStyle) {
        val h = hand(p)
        val blind = p.position == Position.SB || p.position == Position.BB
        when {
            raises == 0 && limpers == 0 && currentBet == 2 && p.position == Position.BB -> {
                p.lastAction = "Чек"
                line(Who.VILLAIN, "${label(p)} чекает.")
            }
            raises == 0 && limpers == 0 -> when {
                VillainStyle.inTop(h, s.open) -> botRaise(p, 6, "повышает")
                VillainStyle.inTop(h, s.limp) -> { botCall(p); limpers++; p.lastAction = "Лимп" }
                else -> botFold(p)
            }
            raises == 0 -> when {
                p.position == Position.BB && VillainStyle.inTop(h, s.threeBet * 2) -> botRaise(p, 10 + 2 * (limpers - 1), "повышает")
                p.position == Position.BB -> { p.lastAction = "Чек"; line(Who.VILLAIN, "${label(p)} чекает.") }
                s.wild && VillainStyle.inTop(h, s.open * 0.6) -> botRaise(p, 8 + 2 * (limpers - 1), "повышает")
                VillainStyle.inTop(h, s.limp) || (blind && VillainStyle.inTop(h, 0.3)) -> { botCall(p); limpers++; p.lastAction = "Лимп" }
                else -> botFold(p)
            }
            raises == 1 -> when {
                VillainStyle.inTop(h, s.threeBet) -> botRaise(p, currentBet * if (blind) 4 else 3, "делает 3-бет")
                VillainStyle.inTop(h, if (blind || s == VillainStyle.FISH) s.defend else s.defend * 0.5) -> { botCall(p); callersAfterRaise++ }
                else -> botFold(p)
            }
            raises == 2 -> when {
                VillainStyle.inTop(h, s.fourBet) -> botRaise(p, (currentBet * 2.3).roundToInt(), "делает 4-бет")
                VillainStyle.inTop(h, s.callThreeBet) -> botCall(p)
                else -> botFold(p)
            }
            else -> if (VillainStyle.inTop(h, s.fourBet)) botCall(p) else botFold(p)
        }
    }

    private fun botPostflop(p: TablePlayer, s: VillainStyle) {
        val r = HandReader.read(p.cards, board)
        val multiway = active.size > 2
        val call = min(currentBet - p.street, p.stack)
        if (call == 0) {
            var chance = when (r.strength) {
                Strength.STRONG -> s.betStrong
                Strength.DRAW -> s.betDraw
                Strength.MEDIUM -> s.betMedium
                Strength.WEAK -> s.bluff
            }
            if (aggressor == p && street == SimStreet.FLOP) chance = max(chance, s.cbet)
            if (multiway && r.strength != Strength.STRONG) chance *= 0.45f
            if (random.nextFloat() < chance && cap > 0) {
                val size = max(2, (pot * listOf(0.5, 0.66, 1.0).random(random)).roundToInt())
                botRaise(p, size, "ставит", bet = true)
            } else {
                p.lastAction = "Чек"
                line(Who.VILLAIN, "${label(p)} чекает.")
            }
            return
        }
        val need = PotOdds.requiredEquity(pot, call)
        val drawOk = PotOdds.exactHitChance(r.outs, 52 - 2 - board.size, 1) >= need - 0.05
        val big = call > (pot - call) * 0.5
        val mediumCall = if (multiway) s.callMediumBig * 0.6f else s.callMediumBig
        val decision = when (r.strength) {
            Strength.STRONG -> if (r.monster && random.nextFloat() < s.raiseMonster * 0.6f) Act.RAISE else Act.CALL
            Strength.MEDIUM -> when {
                !big -> if (random.nextFloat() < 0.8f) Act.CALL else Act.FOLD
                else -> if (random.nextFloat() < mediumCall) Act.CALL else Act.FOLD
            }
            Strength.DRAW -> if (street != SimStreet.RIVER && (drawOk || (s == VillainStyle.FISH && random.nextFloat() < 0.6f))) Act.CALL else Act.FOLD
            Strength.WEAK -> if (!multiway && random.nextFloat() < s.callWeak) Act.CALL else Act.FOLD
        }
        when (decision) {
            Act.FOLD -> botFold(p)
            Act.CALL -> botCall(p)
            else -> botRaise(p, currentBet * 3, "повышает")
        }
    }

    // --- View ------------------------------------------------------------------------------

    fun view(): TableView {
        val showdown = result?.showdown == true
        return TableView(
            players = players.sortedBy { it.seat }.map { p ->
                PlayerView(p.seat, p.position, p.style, p.stack, p.inHand, if (p.isHero || (showdown && p.inHand)) p.cards else null, p.lastAction)
            },
            board = board,
            pot = pot,
            toCall = toCall,
            heroStack = hero.stack,
            options = if (awaitingHero && result == null) options() else emptyList(),
            lastLog = log.last(),
            result = result,
            verdicts = verdicts.toList(),
            history = history.toList(),
            heroPosition = hero.position,
        )
    }

    /** The hero's stack after the hand. */
    val heroStackAfter: Int get() = heroStartStack + (result?.heroNet ?: 0)
}
