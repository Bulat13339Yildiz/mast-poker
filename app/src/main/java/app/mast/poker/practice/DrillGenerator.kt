package app.mast.poker.practice

import app.mast.poker.content.DrillType
import app.mast.poker.content.Position
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Deck
import app.mast.poker.core.poker.EquityCalculator
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.Outs
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.Rank
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.Showdown
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.Suit
import kotlin.math.abs
import kotlin.random.Random

sealed interface DrillTask {
    data class NameHand(val hole: List<Card>, val board: List<Card>, val answer: HandCategory, val options: List<HandCategory>) : DrillTask
    data class Winner(val board: List<Card>, val hands: List<List<Card>>, val winners: List<Int>) : DrillTask
    data class BestFive(val hole: List<Card>, val board: List<Card>, val category: HandCategory) : DrillTask {
        fun isCorrect(selection: Set<Card>): Boolean =
            selection.size == 5 &&
                HandEvaluator.evaluate5(selection.toList()).sameStrength(HandEvaluator.evaluate(hole + board))
        val example: List<Card> get() = HandEvaluator.evaluate(hole + board).cards
    }
    data class OutsTask(
        val hole: List<Card>,
        val board: List<Card>,
        val target: HandCategory,
        val draw: String,
        val outs: List<Card>,
        val options: List<Int>,
    ) : DrillTask {
        val answer: Int get() = outs.size
    }
    data class PotOddsTask(
        val potBeforeBet: Int,
        val bet: Int,
        val outs: Int,
        val cardsToCome: Int,
    ) : DrillTask {
        val pot: Int get() = potBeforeBet + bet
        val requiredEquity: Double get() = PotOdds.requiredEquity(pot, bet)
        val estimatedEquity: Int get() = PotOdds.ruleOfTwoAndFour(outs, cardsToCome)
        val exactChance: Double get() = PotOdds.exactHitChance(outs, if (cardsToCome == 2) 47 else 46, cardsToCome)
        val shouldCall: Boolean get() = exactChance >= requiredEquity
    }
    data class PreflopTask(val hole: List<Card>, val position: Position, val shouldOpen: Boolean) : DrillTask {
        val hand: StartingHand get() = StartingHand.of(hole[0], hole[1])
    }
    /** [equity] is the first hand's share of the pot (wins + half the ties). */
    data class EquityTask(val hands: List<List<Card>>, val board: List<Card>, val equity: Double) : DrillTask {
        val favourite: Int get() = if (equity > 0.5) 0 else 1
    }
}

val Position.seatGroup: SeatGroup
    get() = when (this) {
        Position.UTG -> SeatGroup.EARLY
        Position.MP, Position.SB, Position.BB -> SeatGroup.MIDDLE
        Position.CO, Position.BTN -> SeatGroup.LATE
    }

class DrillGenerator(private val random: Random = Random.Default) {

    private val advanced = AdvancedDrills(random)

    /** [level] 1..3 sets the difficulty of the second-level drills; the basic ones ignore it. */
    fun next(type: DrillType, level: Int = random.nextInt(1, 4)): DrillTask = when (type) {
        DrillType.NAME_HAND -> nameHand()
        DrillType.WINNER -> winner()
        DrillType.BEST_FIVE -> bestFive()
        DrillType.OUTS -> outs()
        DrillType.POT_ODDS -> potOdds()
        DrillType.PREFLOP -> preflop()
        DrillType.EQUITY -> equity()
        DrillType.RANGES -> advanced.range(level)
        DrillType.COMBOS -> advanced.combos(level)
        DrillType.BLUFF_MATH -> advanced.bluffMath(level)
        DrillType.SIZING -> advanced.sizing(level)
        DrillType.PUSH_FOLD -> advanced.pushFold(level)
        DrillType.HAND_SIM -> error("The hand simulator has its own engine")
    }

    fun equity(): DrillTask.EquityTask {
        while (true) {
            val deck = Deck.shuffled(random)
            val a = listOf(deck.removeLast(), deck.removeLast())
            val b = listOf(deck.removeLast(), deck.removeLast())
            val street = random.nextInt(3)
            val board = List(listOf(0, 3, 4)[street]) { deck.removeLast() }
            val e = EquityCalculator.headsUp(a, b, board, samples = 1500, random = random)
            val share = e.win + e.tie / 2
            if (abs(share - 0.5) >= 0.08) return DrillTask.EquityTask(listOf(a, b), board, share)
        }
    }

    fun nameHand(): DrillTask.NameHand {
        val target = HandCategory.entries.random(random)
        val seven = random.nextInt(100) < 60
        val all = dealCategory(target, if (seven) 7 else 5)
        val answer = HandEvaluator.evaluate(all).category
        val hole = if (all.size == 7) all.take(2) else emptyList()
        val board = if (all.size == 7) all.drop(2) else all
        return DrillTask.NameHand(hole, board, answer, categoryOptions(answer))
    }

    fun winner(): DrillTask.Winner {
        val wantClose = random.nextInt(100) < 55
        repeat(200) {
            val deck = Deck.shuffled(random)
            val a = listOf(deck.removeLast(), deck.removeLast())
            val b = listOf(deck.removeLast(), deck.removeLast())
            val board = List(5) { deck.removeLast() }
            val r = Showdown.resolve(board, listOf(a, b))
            val boringBoard = r.values.all { it.category == HandCategory.HIGH_CARD }
            val close = r.values[0].category == r.values[1].category
            if (!boringBoard && (!wantClose || close)) return DrillTask.Winner(board, listOf(a, b), r.winners)
        }
        val board = cardsOf("Ah Kd 9c 4s 2h")
        val hands = listOf(cardsOf("As Qc"), cardsOf("Ac Jd"))
        return DrillTask.Winner(board, hands, Showdown.resolve(board, hands).winners)
    }

    fun bestFive(): DrillTask.BestFive {
        val pool = HandCategory.entries.filter { it >= HandCategory.PAIR }
        val all = dealCategory(pool.random(random), 7)
        return DrillTask.BestFive(all.take(2), all.drop(2), HandEvaluator.evaluate(all).category)
    }

    fun outs(): DrillTask.OutsTask {
        val kind = OutsKind.entries.random(random)
        val onTurn = random.nextBoolean()
        repeat(6000) {
            val deal = kind.deal(random, onTurn) ?: return@repeat
            val (hole, board) = deal
            val outs = Outs.toCategory(hole, board, kind.target)
            if (outs.isEmpty() || !kind.accepts(outs.size)) return@repeat
            return DrillTask.OutsTask(hole, board, kind.target, kind.ruName, outs, intOptions(outs.size, listOf(2, 4, 6, 7, 8, 9, 12, 15)))
        }
        val hole = cardsOf("Ah Kh")
        val board = cardsOf("7h 2h 9c")
        val outs = Outs.toCategory(hole, board, HandCategory.FLUSH)
        return DrillTask.OutsTask(hole, board, HandCategory.FLUSH, OutsKind.FLUSH_DRAW.ruName, outs, intOptions(outs.size, listOf(4, 8, 9, 12)))
    }

    fun potOdds(): DrillTask.PotOddsTask {
        while (true) {
            val potBefore = (4 + random.nextInt(27)) * 10
            val fraction = listOf(0.33, 0.5, 0.66, 1.0, 1.5).random(random)
            val bet = ((potBefore * fraction) / 5).toInt().coerceAtLeast(2) * 5
            val outs = listOf(4, 8, 9, 12, 15).random(random)
            val toCome = if (random.nextInt(100) < 65) 1 else 2
            val task = DrillTask.PotOddsTask(potBefore, bet, outs, toCome)
            val rule = task.estimatedEquity / 100.0
            val need = task.requiredEquity
            // Only clear spots where the quick rule and the exact maths agree.
            val agree = (rule >= need) == (task.exactChance >= need)
            if (agree && abs(rule - need) >= 0.04 && abs(task.exactChance - need) >= 0.03) return task
        }
    }

    fun preflop(): DrillTask.PreflopTask {
        val position = listOf(Position.UTG, Position.MP, Position.CO, Position.BTN, Position.SB).random(random)
        val playableBias = random.nextBoolean()
        repeat(200) {
            val deck = Deck.shuffled(random)
            val hole = listOf(deck.removeLast(), deck.removeLast())
            val hand = StartingHand.of(hole[0], hole[1])
            val open = PreflopChart.shouldOpen(hand, position.seatGroup)
            val playableSomewhere = PreflopChart.shouldOpen(hand, SeatGroup.LATE)
            if (!playableBias || playableSomewhere) return DrillTask.PreflopTask(hole, position, open)
        }
        val hole = cardsOf("Ah Kd")
        return DrillTask.PreflopTask(hole, position, true)
    }

    // --- dealing helpers -------------------------------------------------------------

    /** Deal [size] cards (5 or 7) whose best hand is exactly [target]. First two are the hole cards. */
    fun dealCategory(target: HandCategory, size: Int): List<Card> {
        repeat(400) {
            val five = core(target) ?: return@repeat
            if (HandEvaluator.evaluate5(five).category != target) return@repeat
            val rest = Deck.without(five).shuffled(random)
            val all = (five + rest.take(size - 5)).shuffled(random)
            if (HandEvaluator.evaluate(all).category == target) return all
        }
        error("Could not deal $target")
    }

    private fun core(target: HandCategory): List<Card>? {
        val suits = Suit.entries
        fun anySuit() = suits.random(random)
        fun distinctRanks(n: Int, exclude: Set<Rank> = emptySet()) =
            (Rank.entries - exclude).shuffled(random).take(n)
        return when (target) {
            HandCategory.ROYAL_FLUSH -> anySuit().let { s -> listOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE).map { Card(it, s) } }
            HandCategory.STRAIGHT_FLUSH -> {
                val s = anySuit()
                straightRanks(5 + random.nextInt(9)).map { Card(it, s) }
            }
            HandCategory.FOUR_OF_A_KIND -> {
                val r = Rank.entries.random(random)
                suits.map { Card(r, it) } + Card(distinctRanks(1, setOf(r))[0], anySuit())
            }
            HandCategory.FULL_HOUSE -> {
                val (a, b) = distinctRanks(2)
                suits.shuffled(random).take(3).map { Card(a, it) } + suits.shuffled(random).take(2).map { Card(b, it) }
            }
            HandCategory.FLUSH -> {
                val s = anySuit()
                distinctRanks(5).map { Card(it, s) }
            }
            HandCategory.STRAIGHT -> straightRanks(5 + random.nextInt(10)).map { Card(it, anySuit()) }
            HandCategory.THREE_OF_A_KIND -> {
                val r = Rank.entries.random(random)
                suits.shuffled(random).take(3).map { Card(r, it) } + distinctRanks(2, setOf(r)).map { Card(it, anySuit()) }
            }
            HandCategory.TWO_PAIR -> {
                val (a, b, k) = distinctRanks(3)
                suits.shuffled(random).take(2).map { Card(a, it) } + suits.shuffled(random).take(2).map { Card(b, it) } + Card(k, anySuit())
            }
            HandCategory.PAIR -> {
                val r = Rank.entries.random(random)
                suits.shuffled(random).take(2).map { Card(r, it) } + distinctRanks(3, setOf(r)).map { Card(it, anySuit()) }
            }
            HandCategory.HIGH_CARD -> distinctRanks(5).map { Card(it, anySuit()) }
        }
    }

    private fun straightRanks(high: Int): List<Rank> =
        if (high == 5) listOf(Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE)
        else (high - 4..high).map { Rank.of(it) }

    private fun categoryOptions(answer: HandCategory): List<HandCategory> {
        val near = HandCategory.entries.filter { it != answer && abs(it.ordinal - answer.ordinal) <= 3 }.shuffled(random)
        return (near.take(3) + answer).shuffled(random)
    }

    private fun intOptions(answer: Int, pool: List<Int>): List<Int> {
        val distractors = (pool + listOf(answer - 1, answer + 1, answer + 2, answer - 2, answer * 2))
            .filter { it > 0 && it != answer }.distinct().shuffled(random).take(3)
        return (distractors + answer).sorted()
    }

    private fun cardsOf(spec: String) = app.mast.poker.core.poker.cards(spec)

    private enum class OutsKind(val ruName: String, val target: HandCategory) {
        FLUSH_DRAW("Флеш-дро", HandCategory.FLUSH),
        OPEN_ENDED("Двусторонний стрит-дро", HandCategory.STRAIGHT),
        GUTSHOT("Гатшот", HandCategory.STRAIGHT),
        SET_TO_BOAT("Сет", HandCategory.FULL_HOUSE),
        TWO_PAIR_TO_BOAT("Две пары", HandCategory.FULL_HOUSE),
        COMBO_DRAW("Флеш-дро + стрит-дро", HandCategory.STRAIGHT);

        fun accepts(n: Int): Boolean = when (this) {
            FLUSH_DRAW -> n == 9
            OPEN_ENDED -> n == 8
            GUTSHOT -> n == 4
            SET_TO_BOAT -> n in 7..10
            TWO_PAIR_TO_BOAT -> n == 4
            COMBO_DRAW -> n == 15
        }

        /** Random deal of the right shape; acceptance is checked by counting real outs. */
        fun deal(random: Random, onTurn: Boolean): Pair<List<Card>, List<Card>>? {
            val deck = Deck.shuffled(random)
            val hole = listOf(deck.removeLast(), deck.removeLast())
            val board = List(if (onTurn) 4 else 3) { deck.removeLast() }
            val all = hole + board
            val now = HandEvaluator.evaluate(all).category
            val suitMax = all.groupingBy { it.suit }.eachCount().values.max()
            val ok = when (this) {
                FLUSH_DRAW -> now == HandCategory.HIGH_CARD && suitMax == 4
                OPEN_ENDED -> now == HandCategory.HIGH_CARD && suitMax <= 2 && hasFourInARow(all)
                GUTSHOT -> now == HandCategory.HIGH_CARD && suitMax <= 2 && !hasFourInARow(all)
                COMBO_DRAW -> now == HandCategory.HIGH_CARD && suitMax == 4
                SET_TO_BOAT -> now == HandCategory.THREE_OF_A_KIND && hole[0].rank == hole[1].rank
                TWO_PAIR_TO_BOAT -> now == HandCategory.TWO_PAIR &&
                    hole.all { h -> board.any { it.rank == h.rank } } && hole[0].rank != hole[1].rank
            }
            return if (ok) hole to board else null
        }

        /** Four consecutive ranks, counting the ace both high and low. */
        private fun hasFourInARow(cards: List<Card>): Boolean {
            val values = cards.map { it.rank.value }.toMutableSet()
            if (14 in values) values += 1
            return (1..11).any { start -> (start until start + 4).all { it in values } }
        }
    }
}
