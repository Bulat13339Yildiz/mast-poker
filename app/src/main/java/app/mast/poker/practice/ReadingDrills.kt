package app.mast.poker.practice

import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Deck
import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.RangeEquity
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.practice.sim.HandReader
import app.mast.poker.practice.sim.Strength
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** What the opponent did after the flop, and which hands play that way in the simple model. */
enum class ReadAction(val story: String, val keeps: String) {
    BIG_BET("На флопе он ставит крупно", "так играют сильные руки и дро"),
    CHECK("На флопе он чекает", "так играют средние и слабые руки"),
    TWO_BETS("Он ставит на флопе и ещё раз на тёрне", "так играют сильные руки и сильные дро — от 8 аутов"),
}

/** A hand class as it plays on a board: the strength most of its combos have there. */
data class ClassRead(val strength: Strength, val bigDraw: Boolean)

sealed interface ReadingTask : DrillTask {
    data class ReadHand(
        val seat: SeatGroup,
        val action: ReadAction,
        val board: List<Card>,
        val start: Set<StartingHand>,
        val target: Set<StartingHand>,
        val reads: Map<StartingHand, ClassRead>,
    ) : ReadingTask

    data class EquityVsRange(
        val hole: List<Card>,
        val board: List<Card>,
        val percent: Int,
        val equity: Double,
        val answer: Int,
        val options: List<Int>,
    ) : ReadingTask
}

class ReadingDrills(private val random: Random) {

    private fun chart(seat: SeatGroup) = HandClasses.all.filter { PreflopChart.shouldOpen(it, seat) }.toSet()

    /** How a hand class plays on [board]: by majority of its combos that fit next to the board. */
    fun classify(h: StartingHand, board: List<Card>): ClassRead? {
        val combos = HandClasses.combosOf(h, board)
        if (combos.isEmpty()) return null
        val reads = combos.map { HandReader.read(it, board) }
        val strength = reads.groupingBy { it.strength }.eachCount().maxBy { it.value }.key
        val big = reads.count { it.strength == Strength.DRAW && it.outs >= 8 } * 2 > reads.size
        return ClassRead(strength, big)
    }

    fun keeps(action: ReadAction, r: ClassRead): Boolean = when (action) {
        ReadAction.BIG_BET -> r.strength == Strength.STRONG || r.strength == Strength.DRAW
        ReadAction.CHECK -> r.strength == Strength.MEDIUM || r.strength == Strength.WEAK
        ReadAction.TWO_BETS -> r.strength == Strength.STRONG || (r.strength == Strength.DRAW && r.bigDraw)
    }

    fun readHand(level: Int): ReadingTask.ReadHand {
        val action = when (level) {
            1 -> ReadAction.BIG_BET
            2 -> ReadAction.CHECK
            else -> ReadAction.TWO_BETS
        }
        val seats = if (level == 1) listOf(SeatGroup.EARLY, SeatGroup.MIDDLE) else SeatGroup.entries
        repeat(300) {
            val seat = seats.random(random)
            val start = chart(seat)
            val deck = Deck.shuffled(random)
            val board = List(if (action == ReadAction.TWO_BETS) 4 else 3) { deck.removeLast() }
            val reads = start.mapNotNull { h -> classify(h, board)?.let { h to it } }.toMap()
            val target = reads.filter { keeps(action, it.value) }.keys
            // Worth a question only when the action really narrows the range.
            if (target.size >= 3 && target.size <= start.size * 0.7) {
                return ReadingTask.ReadHand(seat, action, board, start, target, reads)
            }
        }
        error("No hand-reading spot found")
    }

    /** "How much equity does this hand have against the top X%?" with options 10 points apart. */
    fun rangeEquity(level: Int): ReadingTask.EquityVsRange {
        val percents = when (level) { 1 -> listOf(5, 10, 20); 2 -> listOf(15, 30, 50); else -> listOf(10, 20, 30) }
        repeat(200) {
            val pct = percents.random(random)
            val range = HandRange.top(pct / 100.0)
            val deck = Deck.shuffled(random)
            val hole = listOf(deck.removeLast(), deck.removeLast())
            val board = if (level == 3) List(3) { deck.removeLast() } else emptyList()
            if (range.combos(hole + board).isEmpty()) return@repeat
            val eq = RangeEquity.vsRange(hole, range, board, samples = 8000, random = Random(random.nextInt()))
            val answer = (eq * 20).roundToInt() * 5
            // Keep clear of the halfway points between options.
            if (abs(eq * 100 - answer) > 2.0 || answer !in 10..90) return@repeat
            val near = listOf(-20, -10, 10, 20).map { answer + it }.filter { it in 5..95 }.shuffled(random)
            val far = listOf(-30, 30, -40, 40).map { answer + it }.filter { it in 5..95 }
            val wrong = (near + far).take(3)
            return ReadingTask.EquityVsRange(hole, board, pct, eq, answer, (wrong + answer).sorted())
        }
        error("No range-equity spot found")
    }
}
