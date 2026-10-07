package app.mast.poker.practice

import app.mast.poker.content.DrillType
import app.mast.poker.content.Question
import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.PushFold
import app.mast.poker.core.poker.RangeEquity
import app.mast.poker.core.poker.cards
import app.mast.poker.practice.sim.Act
import app.mast.poker.practice.sim.Facing
import app.mast.poker.practice.sim.Quality
import app.mast.poker.practice.sim.Strength
import app.mast.poker.practice.sim.Verdict
import app.mast.poker.progress.Leak
import app.mast.poker.progress.LeakEvent
import app.mast.poker.progress.Leaks
import app.mast.poker.progress.UserProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ReadingDrillsTest {

    private val reading = ReadingDrills(Random(23))
    private val exercises = Exercises(DrillGenerator(Random(23)), Random(23))

    @Test
    fun `hand reading keeps exactly the hands the model says`() {
        repeat(24) {
            val level = it % 3 + 1
            val t = reading.readHand(level)
            assertTrue(t.target.isNotEmpty())
            assertTrue(t.start.containsAll(t.target))
            assertTrue(t.target.size < t.start.size)
            assertEquals(if (level == 3) 4 else 3, t.board.size)
            t.start.forEach { h ->
                val r = reading.classify(h, t.board) ?: return@forEach
                assertEquals("$h on ${t.board}", reading.keeps(t.action, r), h in t.target)
            }
        }
    }

    @Test
    fun `classification follows the hand reader`() {
        val board = cards("Kh 8d 3c")
        assertEquals(Strength.STRONG, reading.classify(HandClasses.parse("KK"), board)!!.strength) // set
        assertEquals(Strength.STRONG, reading.classify(HandClasses.parse("AKo"), board)!!.strength) // top pair, ace kicker
        assertEquals(Strength.MEDIUM, reading.classify(HandClasses.parse("99"), board)!!.strength) // pair below the king
        assertEquals(Strength.WEAK, reading.classify(HandClasses.parse("AQo"), board)!!.strength)
        // Starting the grid from the opener's range, the question's answer is gradeable.
        val q = exercises.next(DrillType.READ_HAND, 1) as Question.BuildRange
        assertTrue(q.start.isNotEmpty() && q.board.size == 3)
        assertTrue(q.isCorrect(q.target))
        assertFalse(q.isCorrect(q.start))
    }

    @Test
    fun `range equity answers are the nearest option by a clear margin`() {
        repeat(30) {
            val level = it % 3 + 1
            val t = reading.rangeEquity(level)
            assertTrue(t.answer in t.options)
            assertEquals(4, t.options.toSet().size)
            assertTrue(abs(t.equity * 100 - t.answer) <= 2.0)
            val precise = RangeEquity.vsRange(t.hole, HandRange.top(t.percent / 100.0), t.board, samples = 40_000, random = Random(it))
            val nearest = t.options.minBy { o -> abs(precise * 100 - o) }
            assertEquals("${t.hole} ${t.board} top ${t.percent}: $precise", t.answer, nearest)
        }
    }

    @Test
    fun `push fold from the button counts both blinds`() {
        val hole = cards("7h 2d")
        val r = PushSeat.BTN.evaluate(hole, 10.0, HandRange.top(0.10), 20_000, Random(1))
        assertEquals(1.5, r.steal, 1e-9)
        assertEquals(0.0, r.evFold, 1e-9)
        assertEquals(r.foldAll * 1.5 + r.callChance * r.netWhenCalled, r.evPush, 1e-9)
        // The small blind spot reproduces the heads-up model exactly.
        val sb = PushSeat.SB.evaluate(cards("As 7d"), 10.0, HandRange.top(0.2), 4000, Random(5))
        val old = PushFold.evaluate(cards("As 7d"), 10.0, HandRange.top(0.2), 4000, Random(5))
        assertEquals(old.evPush, sb.evPush, 1e-9)
        assertEquals(old.callChance, sb.callChance, 1e-9)
        // More players behind means more chances to get called.
        val co = PushSeat.CO.evaluate(hole, 10.0, HandRange.top(0.10), 20_000, Random(1))
        assertTrue(co.foldAll < r.foldAll)
    }

    @Test
    fun `leaks come from the direction of the mistake`() {
        fun v(facing: Facing, chosen: Act, best: Act, q: Quality, street: String = "Флоп") = Verdict(street, chosen, q, best, "", facing)
        assertEquals(listOf(LeakEvent(Leak.RIVER_OVERCALL, true)), LeakDetector.fromVerdict(v(Facing.BET, Act.CALL, Act.FOLD, Quality.MISTAKE, "Ривер")))
        assertEquals(listOf(LeakEvent(Leak.OVERCALL, false)), LeakDetector.fromVerdict(v(Facing.BET, Act.FOLD, Act.FOLD, Quality.BEST)))
        assertEquals(listOf(LeakEvent(Leak.TIGHT_PREFLOP, false), LeakEvent(Leak.LIMPING, true)), LeakDetector.fromVerdict(v(Facing.OPEN, Act.CALL, Act.RAISE, Quality.MISTAKE, "Префлоп")))
        // An acceptable alternative is not a leak.
        assertEquals(listOf(LeakEvent(Leak.PASSIVE, false)), LeakDetector.fromVerdict(v(Facing.CHECKED_TO, Act.CHECK, Act.BET, Quality.OK)))
        val pf = exercises.next(DrillType.PUSH_FOLD, 1) as Question.PushOrFold
        val wrongWay = Answer.Decision(!pf.shouldPush)
        val event = LeakDetector.fromAnswer(pf, wrongWay, correct = false).single()
        assertTrue(event.mistake)
        assertEquals(if (pf.shouldPush) Leak.PUSH_TIGHT else Leak.PUSH_LOOSE, event.leak)
    }

    @Test
    fun `active leaks need enough spots`() {
        var p = UserProgress()
        repeat(5) { p = Leaks.record(p, listOf(LeakEvent(Leak.OVERCALL, true))) }
        assertTrue(Leaks.active(p).isEmpty())
        p = Leaks.record(p, listOf(LeakEvent(Leak.OVERCALL, false)))
        assertEquals(Leak.OVERCALL, Leaks.active(p).single().first)
        repeat(30) { p = Leaks.record(p, listOf(LeakEvent(Leak.OVERCALL, false))) }
        assertTrue(Leaks.active(p).isEmpty())
    }
}
