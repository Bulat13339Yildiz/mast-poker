package app.mast.poker.practice.sim

import app.mast.poker.content.Position
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Deck
import app.mast.poker.core.poker.HandDescriber
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.Rank
import app.mast.poker.core.poker.Showdown
import app.mast.poker.core.poker.StartingHand
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

enum class SimStreet(val ruName: String, val boardCards: Int) { PREFLOP("Префлоп", 0), FLOP("Флоп", 3), TURN("Тёрн", 4), RIVER("Ривер", 5) }

data class HeroOption(val act: Act, val label: String)

/** Immutable picture of the table for the UI. */
data class SimView(
    val position: Position,
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
)

/** Final outcome of a hand, from the hero's point of view. */
data class SimResult(val heroNet: Int, val pot: Int, val text: String, val showdown: Boolean)

/**
 * One hand of 6-max no-limit hold'em where everyone folds to the hero, heads-up
 * against the big blind. Blinds 1/2, deep stacks. The hero decides, [Coach] grades,
 * a simple bot plays the villain.
 */
class HandSimulator(private val random: Random = Random.Default) {

    val position: Position = listOf(Position.UTG, Position.MP, Position.CO, Position.BTN, Position.SB).random(random)
    private val deck = Deck.shuffled(random)
    val hero: List<Card> = listOf(deck.removeLast(), deck.removeLast())
    val villain: List<Card> = listOf(deck.removeLast(), deck.removeLast())
    private val fullBoard: List<Card> = List(5) { deck.removeLast() }

    var street = SimStreet.PREFLOP
        private set
    val board: List<Card> get() = fullBoard.take(street.boardCards)

    var pot = 3
        private set
    private var heroIn = if (position == Position.SB) 1 else 0
    private var villainIn = 2
    private var heroStreet = heroIn
    private var villainStreet = 2
    var heroAggressor = false
        private set
    var facing = Facing.OPEN
        private set
    var awaitingHero = true
        private set
    var result: SimResult? = null
        private set

    val log = mutableListOf("Блайнды 1/2. До тебя все сбросили — ты ${position.ruName.lowercase()} (${position.short}).")
    val verdicts = mutableListOf<Verdict>()

    private val heroFirstPostflop get() = position == Position.SB
    val toCall: Int get() = (villainStreet - heroStreet).coerceAtLeast(0)
    private val startingHand get() = StartingHand.of(hero[0], hero[1])

    fun options(): List<HeroOption> = when (facing) {
        Facing.OPEN -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"), HeroOption(Act.RAISE, "Рейз до 6"))
        Facing.THREE_BET -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"), HeroOption(Act.RAISE, "Рейз до 45"))
        Facing.FIRST, Facing.CHECKED_TO -> listOf(HeroOption(Act.CHECK, "Чек"), HeroOption(Act.BET, "Бет ${betSize()}"))
        Facing.BET -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"), HeroOption(Act.RAISE, "Рейз до ${villainStreet * 3}"))
        Facing.RAISE -> listOf(HeroOption(Act.FOLD, "Фолд"), HeroOption(Act.CALL, "Колл $toCall"))
    }

    private fun betSize() = max(2, (pot / 2.0).roundToInt())

    val heroInvested: Int get() = heroIn
    val villainInvested: Int get() = villainIn

    fun view() = SimView(
        position, hero, villain, board, pot, toCall, awaitingHero,
        if (awaitingHero && result == null) options() else emptyList(),
        log.last(), result, verdicts.toList(),
    )

    /** Grades and applies the hero's action, then lets the villain respond. */
    fun act(act: Act): Verdict {
        check(awaitingHero && result == null)
        require(options().any { it.act == act }) { "Illegal $act when $facing" }
        val v = grade(act)
        verdicts += v
        apply(act)
        return v
    }

    private fun grade(act: Act): Verdict = when (facing) {
        Facing.OPEN -> Coach.preflopOpen(startingHand, position, act)
        Facing.THREE_BET -> Coach.preflopVs3bet(startingHand, act)
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
            ),
            act,
        )
    }

    // --- Money helpers ---------------------------------------------------------------

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

    private fun heroWins(why: String) {
        result = SimResult(pot - heroIn, pot, why, showdown = false)
        awaitingHero = false
    }

    private fun villainWins(why: String) {
        result = SimResult(-heroIn, pot, why, showdown = false)
        awaitingHero = false
    }

    // --- Flow ---------------------------------------------------------------------------

    private fun apply(act: Act) {
        when (facing) {
            Facing.OPEN -> when (act) {
                Act.FOLD -> villainWins("Ты сбросил карты. Банк забирает большой блайнд.")
                Act.CALL -> {
                    heroPuts(toCall)
                    log += "Ты уравниваешь (лимп). Большой блайнд не повышает."
                    nextStreet()
                }
                else -> {
                    heroPuts(6 - heroStreet)
                    heroAggressor = true
                    log += "Ты повышаешь до 6."
                    villainVsOpen()
                }
            }
            Facing.THREE_BET -> when (act) {
                Act.FOLD -> villainWins("Ты сбросил против 3-бета. Банк у соперника.")
                Act.CALL -> {
                    heroPuts(toCall)
                    heroAggressor = false
                    log += "Ты уравниваешь."
                    nextStreet()
                }
                else -> {
                    heroPuts(45 - heroStreet)
                    log += "Ты повышаешь до 45."
                    val vh = StartingHand.of(villain[0], villain[1])
                    if ((vh.isPair && vh.high >= Rank.KING) || (vh.high == Rank.ACE && vh.low == Rank.KING)) {
                        villainPuts(45 - villainStreet)
                        log += "Соперник уравнивает."
                        heroAggressor = true
                        nextStreet()
                    } else {
                        log += "Соперник сбрасывает."
                        heroWins("Соперник сбросил на твой рейз.")
                    }
                }
            }
            Facing.FIRST -> when (act) {
                Act.CHECK -> {
                    log += "Ты чекаешь."
                    villainActs(afterHeroCheck = true)
                }
                else -> heroBets()
            }
            Facing.CHECKED_TO -> when (act) {
                Act.CHECK -> {
                    log += "Ты чекаешь следом."
                    nextStreet()
                }
                else -> heroBets()
            }
            Facing.BET, Facing.RAISE -> when (act) {
                Act.FOLD -> villainWins("Ты сбросил карты. Банк у соперника.")
                Act.CALL -> {
                    log += "Ты уравниваешь ${toCall}."
                    heroPuts(toCall)
                    nextStreet()
                }
                else -> {
                    val to = villainStreet * 3
                    heroPuts(to - heroStreet)
                    log += "Ты повышаешь до $to."
                    villainVsBet(raise = true)
                }
            }
        }
    }

    private fun heroBets() {
        val size = betSize()
        heroPuts(size)
        log += "Ты ставишь $size."
        villainVsBet(raise = false)
    }

    private fun villainRead() = HandReader.read(villain, board)

    private fun villainVsOpen() {
        val h = StartingHand.of(villain[0], villain[1])
        val premium = (h.isPair && h.high >= Rank.QUEEN) || (h.high == Rank.ACE && h.low == Rank.KING)
        val defends = h.isPair || h.suited || (h.high >= Rank.TEN && h.low >= Rank.TEN) ||
            (h.high == Rank.ACE && h.low >= Rank.SEVEN) || (h.high.value - h.low.value == 1 && h.low >= Rank.FIVE)
        when {
            premium && random.nextFloat() < 0.85f -> {
                villainPuts(18 - villainStreet)
                log += "Большой блайнд повышает до 18 (3-бет)."
                facing = Facing.THREE_BET
            }
            defends || random.nextFloat() < 0.2f -> {
                villainPuts(6 - villainStreet)
                log += "Большой блайнд уравнивает."
                nextStreet()
            }
            else -> {
                log += "Большой блайнд сбрасывает."
                heroWins("Соперник сбросил на твой рейз — блайнды твои.")
            }
        }
    }

    /** Villain to act with no bet in front: bets or checks by hand strength. */
    private fun villainActs(afterHeroCheck: Boolean) {
        val r = villainRead()
        val betChance = when (r.strength) {
            Strength.STRONG -> 0.75f
            Strength.DRAW -> 0.45f
            Strength.MEDIUM -> 0.3f
            Strength.WEAK -> if (afterHeroCheck) 0.3f else 0.18f
        }
        if (random.nextFloat() < betChance) {
            val size = max(2, (pot * listOf(0.5, 0.66, 1.0).random(random)).roundToInt())
            villainPuts(size)
            log += "Соперник ставит $size."
            facing = Facing.BET
            awaitingHero = true
        } else if (afterHeroCheck) {
            log += "Соперник тоже чекает."
            nextStreet()
        } else {
            log += "Соперник чекает."
            facing = Facing.CHECKED_TO
            awaitingHero = true
        }
    }

    /** Villain facing the hero's bet or raise. */
    private fun villainVsBet(raise: Boolean) {
        val r = villainRead()
        val need = PotOdds.requiredEquity(pot, heroStreet - villainStreet)
        val drawOk = PotOdds.exactHitChance(r.outs, 52 - 2 - board.size, 1) >= need - 0.05
        val callAmount = heroStreet - villainStreet
        val decision = when (r.strength) {
            Strength.STRONG -> if (!raise && r.monster && random.nextFloat() < 0.35f) Act.RAISE else Act.CALL
            Strength.MEDIUM -> if (raise) (if (random.nextFloat() < 0.3f) Act.CALL else Act.FOLD)
            else if (callAmount <= pot * 0.4 || random.nextFloat() < 0.4f) Act.CALL else Act.FOLD
            Strength.DRAW -> if (drawOk && street != SimStreet.RIVER) Act.CALL else Act.FOLD
            Strength.WEAK -> if (!raise && random.nextFloat() < 0.15f) Act.CALL else Act.FOLD
        }
        when (decision) {
            Act.FOLD -> {
                log += "Соперник сбрасывает."
                heroWins("Соперник сбросил — банк твой без вскрытия.")
            }
            Act.CALL -> {
                villainPuts(callAmount)
                log += "Соперник уравнивает."
                nextStreet()
            }
            else -> {
                val to = heroStreet * 3
                villainPuts(to - villainStreet)
                log += "Соперник повышает до $to."
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
        log += "${street.ruName}: ${board.takeLast(if (street == SimStreet.FLOP) 3 else 1).joinToString(" ")}"
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
        val (net, text) = when {
            r.isSplit -> (pot / 2 - heroIn) to "Ничья: у обоих $heroHand. Банк делится."
            r.winners == listOf(0) -> (pot - heroIn) to "Вскрытие: у тебя $heroHand, у соперника $villainHand. Банк твой!"
            else -> (-heroIn) to "Вскрытие: у соперника $villainHand, у тебя $heroHand. Банк уходит сопернику."
        }
        log += text
        result = SimResult(net, pot, text, showdown = true)
        awaitingHero = false
    }
}
