package app.mast.poker.practice.sim

import app.mast.poker.content.Position
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Deck
import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandDescriber
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.Showdown
import app.mast.poker.core.poker.StartingHand
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

enum class SimStreet(val ruName: String, val boardCards: Int) { PREFLOP("Префлоп", 0), FLOP("Флоп", 3), TURN("Тёрн", 4), RIVER("Ривер", 5) }

data class HeroOption(val act: Act, val label: String)

enum class Who { HERO, VILLAIN, TABLE }

/** One line of the hand history; hero lines carry the coach's verdict. */
sealed interface HistoryEntry {
    data class Street(val street: SimStreet, val cards: List<Card>, val pot: Int) : HistoryEntry
    data class Line(val who: Who, val text: String, val verdict: Verdict? = null) : HistoryEntry
}

/** How the hand reaches the hero. */
enum class Scenario { FOLDED_TO_HERO, VILLAIN_OPENED, VILLAIN_LIMPED }

/** Immutable picture of the table for the UI. */
data class SimView(
    val position: Position,
    val villainSeat: Position,
    val style: VillainStyle,
    val hero: List<Card>,
    val villain: List<Card>,
    val board: List<Card>,
    val pot: Int,
    val toCall: Int,
    val awaitingHero: Boolean,
    val options: List<HeroOption>,
    val lastLog: String,
    val result: SimResult?,
    val verdicts: List<Verdict>,
    val history: List<HistoryEntry>,
)

/** Final outcome of a hand, from the hero's point of view. */
data class SimResult(val heroNet: Int, val pot: Int, val text: String, val showdown: Boolean)

/**
 * One hand of 6-max no-limit hold'em, blinds 1/2, deep stacks. Before the hero acts,
 * either everyone folds, or the opponent opens with a raise, or he limps; the rest of
 * the table folds, so every pot is heads-up. The hero decides, [Coach] grades, and the
 * opponent plays his [VillainStyle].
 */
class HandSimulator(private val random: Random = Random.Default, style: VillainStyle? = null) {

    val style: VillainStyle = style ?: VillainStyle.entries.random(random)
    val scenario: Scenario
    val position: Position
    val villainSeat: Position
    private val deck = Deck.shuffled(random)
    val hero: List<Card>
    val villain: List<Card>
    private val fullBoard: List<Card>

    var street = SimStreet.PREFLOP
        private set
    val board: List<Card> get() = fullBoard.take(street.boardCards)

    var pot = 3
        private set
    private var heroIn = 0
    private var villainIn = 0
    private var heroStreet = 0
    private var villainStreet = 0

    /** Blinds posted by players who then folded. */
    val deadMoney: Int
    var heroAggressor = false
        private set
    private var villainAggressor = false
    var facing = Facing.OPEN
        private set
    var awaitingHero = true
        private set
    var result: SimResult? = null
        private set

    val log = mutableListOf<String>()
    val history = mutableListOf<HistoryEntry>()
    val verdicts = mutableListOf<Verdict>()

    init {
        scenario = pickScenario()
        val (h, v) = pickSeats()
        position = h
        villainSeat = v
        hero = listOf(deck.removeLast(), deck.removeLast())
        villain = dealVillain()
        fullBoard = List(5) { deck.removeLast() }

        heroIn = blindOf(position)
        villainIn = blindOf(villainSeat)
        heroStreet = heroIn
        villainStreet = villainIn
        deadMoney = 3 - heroIn - villainIn
        history += HistoryEntry.Street(SimStreet.PREFLOP, emptyList(), pot)
        table("Блайнды 1/2. Ты — ${position.ruName.lowercase()} (${position.short}), соперник — ${villainSeat.short}.")
        setUpPreflop()
    }

    private val heroFirstPostflop: Boolean get() = PostflopOrder.indexOf(position) < PostflopOrder.indexOf(villainSeat)
    val toCall: Int get() = (villainStreet - heroStreet).coerceAtLeast(0)
    private val startingHand get() = StartingHand.of(hero[0], hero[1])
    private val villainHand get() = StartingHand.of(villain[0], villain[1])

    val heroInvested: Int get() = heroIn
    val villainInvested: Int get() = villainIn

    // --- Set-up -------------------------------------------------------------------------

    private fun pickScenario(): Scenario {
        val roll = random.nextInt(100)
        return if (style == VillainStyle.FISH) {
            when {
                roll < 40 -> Scenario.FOLDED_TO_HERO
                roll < 55 -> Scenario.VILLAIN_OPENED
                else -> Scenario.VILLAIN_LIMPED
            }
        } else {
            if (roll < 50) Scenario.FOLDED_TO_HERO else Scenario.VILLAIN_OPENED
        }
    }

    private fun pickSeats(): Pair<Position, Position> = when (scenario) {
        Scenario.FOLDED_TO_HERO -> PreflopOrder.dropLast(1).random(random) to Position.BB
        Scenario.VILLAIN_OPENED -> {
            val v = PreflopOrder.dropLast(1).random(random)
            PreflopOrder.drop(PreflopOrder.indexOf(v) + 1).random(random) to v
        }
        Scenario.VILLAIN_LIMPED -> {
            val v = listOf(Position.UTG, Position.MP, Position.CO, Position.BTN).random(random)
            val behind = PreflopOrder.drop(PreflopOrder.indexOf(v) + 1).filter { it != Position.SB && it != Position.BB }
            val h = if (behind.isEmpty() || random.nextBoolean()) Position.BB else behind.random(random)
            h to v
        }
    }

    /** The opponent's cards fit his action: a raiser holds his opening range, a limper his limping range. */
    private fun dealVillain(): List<Card> {
        val range = when (scenario) {
            Scenario.FOLDED_TO_HERO -> null
            Scenario.VILLAIN_OPENED -> VillainStyle.top(style.open.coerceAtLeast(0.05))
            Scenario.VILLAIN_LIMPED -> VillainStyle.top(style.limp)
        }
        if (range == null) return listOf(deck.removeLast(), deck.removeLast())
        val combos = range.flatMap { HandClasses.combosOf(it, hero) }
        val pick = combos.random(random)
        deck.removeAll(pick)
        return pick
    }

    private fun blindOf(seat: Position) = when (seat) {
        Position.SB -> 1
        Position.BB -> 2
        else -> 0
    }

    private fun seatsBetween(from: Position?, to: Position): List<Position> {
        val start = if (from == null) 0 else PreflopOrder.indexOf(from) + 1
        return PreflopOrder.subList(start, PreflopOrder.indexOf(to))
    }

    private fun foldLine(seats: List<Position>) {
        if (seats.isEmpty()) return
        val names = seats.joinToString(", ") { it.short }
        table(if (seats.size == 1) "$names сбрасывает." else "$names сбрасывают.")
    }

    private fun setUpPreflop() {
        when (scenario) {
            Scenario.FOLDED_TO_HERO -> {
                foldLine(seatsBetween(null, position))
                facing = Facing.OPEN
            }
            Scenario.VILLAIN_OPENED -> {
                foldLine(seatsBetween(null, villainSeat))
                villainPuts(6 - villainStreet)
                villainAggressor = true
                villainLine("${villainSeat.short} повышает до 6.")
                foldLine(seatsBetween(villainSeat, position))
                facing = Facing.VS_OPEN
            }
            Scenario.VILLAIN_LIMPED -> {
                foldLine(seatsBetween(null, villainSeat))
                villainPuts(2 - villainStreet)
                villainLine("${villainSeat.short} просто уравнивает большой блайнд (лимп).")
                foldLine(seatsBetween(villainSeat, position))
                facing = if (position == Position.BB) Facing.LIMPED_TO_BB else Facing.VS_LIMP
            }
        }
    }

    // --- Options and grading ------------------------------------------------------------

    private val heroOutOfPosition: Boolean get() = heroFirstPostflop
    private fun threeBetTo() = if (heroOutOfPosition) 24 else 18
    private fun reraiseTo(over: Int) = (over * 2.3).roundToInt()
    private fun betSize() = max(2, (pot / 2.0).roundToInt())

    fun options(): List<HeroOption> = when (facing) {
        Facing.OPEN -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"), HeroOption(Act.RAISE, "Рейз до 6"))
        Facing.VS_OPEN -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"), HeroOption(Act.RAISE, "3-бет до ${threeBetTo()}"))
        Facing.VS_LIMP -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.RAISE, "Рейз до 8"))
        Facing.LIMPED_TO_BB -> listOf(HeroOption(Act.CHECK, "Чек"), HeroOption(Act.RAISE, "Рейз до 10"))
        Facing.THREE_BET -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"), HeroOption(Act.RAISE, "4-бет до ${reraiseTo(villainStreet)}"))
        Facing.FOUR_BET -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"))
        Facing.FIRST, Facing.CHECKED_TO -> listOf(HeroOption(Act.CHECK, "Чек"), HeroOption(Act.BET, "Бет ${betSize()}"))
        Facing.BET -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"), HeroOption(Act.RAISE, "Рейз до ${villainStreet * 3}"))
        Facing.RAISE -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"))
    }

    fun view() = SimView(
        position, villainSeat, style, hero, villain, board, pot, toCall, awaitingHero,
        if (awaitingHero && result == null) options() else emptyList(),
        log.last(), result, verdicts.toList(), history.toList(),
    )

    /** Grades and applies the hero's action, then lets the opponent respond. */
    fun act(act: Act): Verdict {
        check(awaitingHero && result == null)
        require(options().any { it.act == act }) { "Illegal $act when $facing" }
        val v = grade(act)
        verdicts += v
        pendingVerdict = v
        apply(act)
        return v
    }

    private var pendingVerdict: Verdict? = null

    private fun grade(act: Act): Verdict = when (facing) {
        Facing.OPEN -> Coach.preflopOpen(startingHand, position, act, style)
        Facing.VS_OPEN -> Coach.preflopVsOpen(startingHand, position, villainSeat, style, act)
        Facing.VS_LIMP, Facing.LIMPED_TO_BB -> Coach.preflopVsLimp(startingHand, position, act)
        Facing.THREE_BET -> Coach.preflopVs3bet(startingHand, act, style)
        Facing.FOUR_BET -> Coach.preflopVs4bet(startingHand, act, style)
        else -> Coach.postflop(
            Coach.Spot(
                street = street.ruName,
                facing = facing,
                read = HandReader.read(hero, board),
                pot = pot,
                toCall = toCall,
                heroAggressor = heroAggressor,
                wet = HandReader.isWet(board),
                river = street == SimStreet.RIVER,
                unseen = 52 - 2 - board.size,
                style = style,
            ),
            act,
        )
    }

    // --- Log and money helpers ----------------------------------------------------------

    private fun table(text: String) {
        log += text
        history += HistoryEntry.Line(Who.TABLE, text)
    }

    private fun villainLine(text: String) {
        log += text
        history += HistoryEntry.Line(Who.VILLAIN, text)
    }

    /** The hero's own move, tagged with the verdict it just received. */
    private fun heroLine(text: String) {
        log += text
        history += HistoryEntry.Line(Who.HERO, text, pendingVerdict)
        pendingVerdict = null
    }

    private fun heroPuts(amount: Int) {
        heroIn += amount; heroStreet += amount; pot += amount
    }

    private fun villainPuts(amount: Int) {
        villainIn += amount; villainStreet += amount; pot += amount
    }

    private fun endStreet() {
        heroStreet = 0
        villainStreet = 0
    }

    private fun finish(net: Int, text: String, showdown: Boolean) {
        log += text
        history += HistoryEntry.Line(Who.TABLE, text)
        result = SimResult(net, pot, text, showdown)
        awaitingHero = false
    }

    private fun heroWins(why: String) = finish(pot - heroIn, why, showdown = false)

    private fun villainWins(why: String) = finish(-heroIn, why, showdown = false)

    private fun foldBehind() {
        val rest = PreflopOrder.filter { it != position && it != villainSeat && PreflopOrder.indexOf(it) > PreflopOrder.indexOf(position) }
        foldLine(rest)
    }

    // --- Flow ---------------------------------------------------------------------------

    private fun apply(act: Act) {
        when (facing) {
            Facing.OPEN -> when (act) {
                Act.FOLD -> {
                    heroLine("Ты сбрасываешь.")
                    villainWins("Банк забирает большой блайнд.")
                }
                Act.CALL -> {
                    heroPuts(toCall)
                    heroLine("Ты уравниваешь (лимп).")
                    villainLine("Большой блайнд чекает.")
                    nextStreet()
                }
                else -> {
                    heroPuts(6 - heroStreet)
                    heroAggressor = true
                    heroLine("Ты повышаешь до 6.")
                    foldBehind()
                    villainVsRaise(threeBetTo = 24)
                }
            }
            Facing.VS_OPEN -> when (act) {
                Act.FOLD -> {
                    heroLine("Ты сбрасываешь.")
                    villainWins("Банк забирает ${villainSeat.short}.")
                }
                Act.CALL -> {
                    heroPuts(toCall)
                    heroLine("Ты уравниваешь.")
                    foldBehind()
                    nextStreet()
                }
                else -> {
                    val to = threeBetTo()
                    heroPuts(to - heroStreet)
                    heroAggressor = true
                    villainAggressor = false
                    heroLine("Ты делаешь 3-бет до $to.")
                    foldBehind()
                    villainVs3bet()
                }
            }
            Facing.VS_LIMP -> when (act) {
                Act.FOLD -> {
                    heroLine("Ты сбрасываешь.")
                    finish(-heroIn, "Ты вышел из раздачи.", showdown = false)
                }
                else -> {
                    heroPuts(8 - heroStreet)
                    heroAggressor = true
                    heroLine("Ты повышаешь до 8 — изоляция.")
                    foldBehind()
                    villainVsRaise(threeBetTo = 24)
                }
            }
            Facing.LIMPED_TO_BB -> when (act) {
                Act.CHECK -> {
                    heroLine("Ты чекаешь — флоп бесплатно.")
                    nextStreet()
                }
                else -> {
                    heroPuts(10 - heroStreet)
                    heroAggressor = true
                    heroLine("Ты повышаешь до 10.")
                    villainVsRaise(threeBetTo = 30)
                }
            }
            Facing.THREE_BET -> when (act) {
                Act.FOLD -> {
                    heroLine("Ты сбрасываешь против 3-бета.")
                    villainWins("Банк у соперника.")
                }
                Act.CALL -> {
                    heroPuts(toCall)
                    heroAggressor = false
                    heroLine("Ты уравниваешь.")
                    nextStreet()
                }
                else -> {
                    val to = reraiseTo(villainStreet)
                    heroPuts(to - heroStreet)
                    heroAggressor = true
                    villainAggressor = false
                    heroLine("Ты делаешь 4-бет до $to.")
                    if (VillainStyle.inTop(villainHand, style.fourBet * 1.5)) {
                        villainPuts(heroStreet - villainStreet)
                        villainLine("Соперник уравнивает.")
                        nextStreet()
                    } else {
                        villainLine("Соперник сбрасывает.")
                        heroWins("Соперник сбросил на твой 4-бет.")
                    }
                }
            }
            Facing.FOUR_BET -> when (act) {
                Act.FOLD -> {
                    heroLine("Ты сбрасываешь против 4-бета.")
                    villainWins("Банк у соперника.")
                }
                else -> {
                    heroPuts(toCall)
                    heroAggressor = false
                    heroLine("Ты уравниваешь.")
                    nextStreet()
                }
            }
            Facing.FIRST -> when (act) {
                Act.CHECK -> {
                    heroLine("Ты чекаешь.")
                    villainActs(afterHeroCheck = true)
                }
                else -> heroBets()
            }
            Facing.CHECKED_TO -> when (act) {
                Act.CHECK -> {
                    heroLine("Ты чекаешь следом.")
                    nextStreet()
                }
                else -> heroBets()
            }
            Facing.BET, Facing.RAISE -> when (act) {
                Act.FOLD -> {
                    heroLine("Ты сбрасываешь.")
                    villainWins("Банк у соперника.")
                }
                Act.CALL -> {
                    heroLine("Ты уравниваешь $toCall.")
                    heroPuts(toCall)
                    nextStreet()
                }
                else -> {
                    val to = villainStreet * 3
                    heroPuts(to - heroStreet)
                    heroLine("Ты повышаешь до $to.")
                    villainVsBet(raise = true)
                }
            }
        }
    }

    private fun heroBets() {
        val size = betSize()
        heroPuts(size)
        heroLine("Ты ставишь $size.")
        villainVsBet(raise = false)
    }

    private fun villainRead() = HandReader.read(villain, board)

    /** The opponent faces the hero's open or isolation raise. */
    private fun villainVsRaise(threeBetTo: Int) {
        val h = villainHand
        val loose = if (style == VillainStyle.FISH) 0.25f else 0.05f
        when {
            VillainStyle.inTop(h, style.threeBet) && random.nextFloat() < 0.9f -> {
                villainPuts(threeBetTo - villainStreet)
                villainAggressor = true
                heroAggressor = false
                villainLine("Соперник повышает до $threeBetTo (3-бет).")
                facing = Facing.THREE_BET
                awaitingHero = true
            }
            VillainStyle.inTop(h, style.defend) || random.nextFloat() < loose -> {
                villainPuts(heroStreet - villainStreet)
                villainLine("Соперник уравнивает.")
                nextStreet()
            }
            else -> {
                villainLine("Соперник сбрасывает.")
                heroWins("Соперник сбросил — банк твой.")
            }
        }
    }

    /** The opener faces the hero's 3-bet. */
    private fun villainVs3bet() {
        val h = villainHand
        when {
            VillainStyle.inTop(h, style.fourBet) -> {
                val to = reraiseTo(heroStreet)
                villainPuts(to - villainStreet)
                villainAggressor = true
                heroAggressor = false
                villainLine("Соперник отвечает 4-бетом до $to.")
                facing = Facing.FOUR_BET
                awaitingHero = true
            }
            VillainStyle.inTop(h, style.callThreeBet) -> {
                villainPuts(heroStreet - villainStreet)
                villainLine("Соперник уравнивает.")
                nextStreet()
            }
            else -> {
                villainLine("Соперник сбрасывает.")
                heroWins("Соперник сбросил на твой 3-бет.")
            }
        }
    }

    /** The opponent acts with no bet in front: bets or checks by hand strength and style. */
    private fun villainActs(afterHeroCheck: Boolean) {
        val r = villainRead()
        var chance = when (r.strength) {
            Strength.STRONG -> style.betStrong
            Strength.DRAW -> style.betDraw
            Strength.MEDIUM -> style.betMedium
            Strength.WEAK -> style.bluff
        }
        if (villainAggressor && street == SimStreet.FLOP) chance = max(chance, style.cbet)
        if (afterHeroCheck && r.strength == Strength.WEAK) chance *= 1.3f
        if (random.nextFloat() < chance) {
            val size = max(2, (pot * listOf(0.5, 0.66, 1.0).random(random)).roundToInt())
            villainPuts(size)
            villainLine("Соперник ставит $size.")
            facing = Facing.BET
            awaitingHero = true
        } else if (afterHeroCheck) {
            villainLine("Соперник тоже чекает.")
            nextStreet()
        } else {
            villainLine("Соперник чекает.")
            facing = Facing.CHECKED_TO
            awaitingHero = true
        }
    }

    /** The opponent faces the hero's bet or raise. */
    private fun villainVsBet(raise: Boolean) {
        val r = villainRead()
        val callAmount = heroStreet - villainStreet
        val need = PotOdds.requiredEquity(pot, callAmount)
        val drawOk = PotOdds.exactHitChance(r.outs, 52 - 2 - board.size, 1) >= need - 0.05
        val big = callAmount > (pot - callAmount) * 0.5
        val canRaise = !raise
        val decision = when (r.strength) {
            Strength.STRONG -> if (canRaise && r.monster && random.nextFloat() < style.raiseMonster) Act.RAISE else Act.CALL
            Strength.MEDIUM -> when {
                raise -> if (random.nextFloat() < style.callMediumBig * 0.6f) Act.CALL else Act.FOLD
                !big -> if (random.nextFloat() < 0.85f) Act.CALL else Act.FOLD
                else -> if (random.nextFloat() < style.callMediumBig) Act.CALL else Act.FOLD
            }
            Strength.DRAW -> when {
                canRaise && random.nextFloat() < style.raiseBluff -> Act.RAISE
                street != SimStreet.RIVER && (drawOk || (style == VillainStyle.FISH && random.nextFloat() < 0.6f)) -> Act.CALL
                else -> Act.FOLD
            }
            Strength.WEAK -> when {
                canRaise && random.nextFloat() < style.raiseBluff -> Act.RAISE
                canRaise && random.nextFloat() < style.callWeak -> Act.CALL
                else -> Act.FOLD
            }
        }
        when (decision) {
            Act.FOLD -> {
                villainLine("Соперник сбрасывает.")
                heroWins("Соперник сбросил — банк твой без вскрытия.")
            }
            Act.CALL -> {
                villainPuts(callAmount)
                villainLine("Соперник уравнивает.")
                nextStreet()
            }
            else -> {
                val to = heroStreet * 3
                villainPuts(to - villainStreet)
                villainLine("Соперник повышает до $to.")
                facing = Facing.RAISE
                awaitingHero = true
            }
        }
    }

    private fun nextStreet() {
        endStreet()
        if (street == SimStreet.RIVER) {
            showdown()
            return
        }
        street = SimStreet.entries[street.ordinal + 1]
        val fresh = board.takeLast(if (street == SimStreet.FLOP) 3 else 1)
        log += "${street.ruName}: ${fresh.joinToString(" ")}"
        history += HistoryEntry.Street(street, fresh, pot)
        if (heroFirstPostflop) {
            facing = Facing.FIRST
            awaitingHero = true
        } else {
            villainActs(afterHeroCheck = false)
        }
    }

    private fun showdown() {
        val r = Showdown.resolve(fullBoard, listOf(hero, villain))
        val heroHand = HandDescriber.describe(r.values[0])
        val villainHand = HandDescriber.describe(r.values[1])
        when {
            r.isSplit -> finish(pot / 2 - heroIn, "Ничья: у обоих $heroHand. Банк делится.", showdown = true)
            r.winners == listOf(0) -> finish(pot - heroIn, "Вскрытие: у тебя $heroHand, у соперника $villainHand. Банк твой!", showdown = true)
            else -> finish(-heroIn, "Вскрытие: у соперника $villainHand, у тебя $heroHand. Банк уходит сопернику.", showdown = true)
        }
    }

    companion object {
        val PreflopOrder = listOf(Position.UTG, Position.MP, Position.CO, Position.BTN, Position.SB, Position.BB)
        val PostflopOrder = listOf(Position.SB, Position.BB, Position.UTG, Position.MP, Position.CO, Position.BTN)
    }
}
