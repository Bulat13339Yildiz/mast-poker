package app.mast.poker.practice

import app.mast.poker.content.Question
import app.mast.poker.core.poker.BluffMath
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Combos
import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.PushFold
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.cards
import app.mast.poker.practice.sim.HandReader
import app.mast.poker.practice.sim.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AdvancedDrillsTest {

    private val drills = AdvancedDrills(Random(17))
    private val exercises = Exercises(DrillGenerator(Random(17)), Random(17))

    private fun distinct(cs: List<Card>) = assertEquals(cs.toString(), cs.size, cs.toSet().size)

    @Test
    fun `range targets come from the chart and the equity ranking`() {
        val early = HandClasses.all.filter { PreflopChart.shouldOpen(it, SeatGroup.EARLY) }.toSet()
        repeat(60) {
            val level = it % 3 + 1
            val t = drills.range(level)
            assertTrue(t.target.isNotEmpty())
            when {
                t.title.contains("ранней") -> assertEquals(early, t.target)
                t.title.startsWith("Топ-") -> {
                    val pct = t.title.removePrefix("Топ-").substringBefore('%').toInt()
                    assertEquals(HandRange.top(pct / 100.0).hands, t.target)
                }
                t.title.startsWith("3-бет") -> assertEquals(HandRange.parse("QQ+, AK").hands, t.target)
            }
        }
    }

    @Test
    fun `range grading tolerates a few cells but not a different range`() {
        val q = exercises.toQuestion(AdvancedTask.RangeTask("Топ-20% рук", HandRange.top(0.20).hands, "")) as Question.BuildRange
        assertTrue(q.isCorrect(q.target))
        val oneMissing = q.target - q.target.first()
        assertTrue(q.isCorrect(oneMissing))
        assertFalse(q.isCorrect(HandRange.top(0.10).hands))
        assertFalse(q.isCorrect(emptySet()))
        val diff = Explainer.rangeDiff(q.target, oneMissing)!!
        assertTrue(diff, diff.startsWith("Пропущены: ${q.target.first().notation}"))
        assertEquals(null, Explainer.rangeDiff(q.target, q.target))
    }

    @Test
    fun `push fold answers survive a precise re-evaluation`() {
        listOf(1, 2, 3).forEach { level ->
            var pushes = 0
            repeat(16) {
                val t = drills.pushFold(level)
                distinct(t.hole)
                assertTrue(t.stackBb in 5.0..15.0)
                val precise = PushFold.evaluate(t.hole, t.stackBb, HandRange.top(t.callPercent / 100.0), samples = 60_000, random = Random(99))
                assertEquals("${t.hole} ${t.stackBb} top ${t.callPercent}: ${t.result} vs $precise", precise.shouldPush, t.result.shouldPush)
                if (t.result.shouldPush) pushes++
            }
            // Both answers must come up, or the drill teaches "always shove".
            assertTrue("level $level pushes $pushes", pushes in 3..13)
        }
    }

    @Test
    fun `push fold known spots`() {
        val wide = HandRange.top(0.5)
        assertFalse(PushFold.evaluate(cards("7h 2d"), 15.0, wide).shouldPush)
        assertTrue(PushFold.evaluate(cards("Ah Ad"), 15.0, wide).shouldPush)
        assertTrue(PushFold.evaluate(cards("Ks 9d"), 8.0, HandRange.top(0.2)).shouldPush)
    }

    @Test
    fun `combo answers match the engine`() {
        repeat(300) {
            val level = it % 3 + 1
            val t = drills.combos(level)
            distinct(t.hole + t.board)
            assertTrue(t.answer in t.options)
            assertEquals(4, t.options.toSet().size)
            assertTrue(t.explanation, t.explanation.contains("${t.answer}"))
            if (t.board.isNotEmpty()) assertEquals(Combos.sets(t.board, t.hole), t.answer)
        }
        assertEquals(3, Combos.of(StartingHand(app.mast.poker.core.poker.Rank.KING, app.mast.poker.core.poker.Rank.KING, false), cards("Ks 4d")))
    }

    @Test
    fun `sizing follows the plan and avoids unclear boards`() {
        val seen = mutableSetOf<Sizing>()
        repeat(240) {
            val level = it % 3 + 1
            val t = drills.sizing(level)
            distinct(t.hole + t.board)
            val read = HandReader.read(t.hole, t.board)
            assertEquals(read, t.read)
            assertFalse(read.strength == Strength.MEDIUM && read.label.contains(" + "))
            val suitMax = t.board.groupingBy { c -> c.suit }.eachCount().values.max()
            if (t.street == BetStreet.FLOP) assertTrue(t.wet || suitMax == 1) else assertTrue(suitMax <= 2)
            if (t.street == BetStreet.RIVER) assertTrue(read.strength != Strength.DRAW)
            seen += t.answer
        }
        assertEquals(Sizing.entries.toSet(), seen)
    }

    @Test
    fun `sizing rules on textbook spots`() {
        fun best(hole: String, board: String): Sizing {
            val b = cards(board)
            val street = BetStreet.entries.first { it.boardCards == b.size }
            return SizingRules.best(street, HandReader.read(cards(hole), b), HandReader.isWet(b))
        }
        assertEquals(Sizing.THIRD, best("As Ks", "Ah 7d 2c")) // top pair, dry
        assertEquals(Sizing.TWO_THIRDS, best("As Ks", "Ah Th 9d")) // top pair, wet
        assertEquals(Sizing.THIRD, best("Qs Jd", "Kh 7d 2c")) // air, dry: small c-bet
        assertEquals(Sizing.CHECK, best("4s 3d", "Jh Th 8h")) // air, wet
        assertEquals(Sizing.TWO_THIRDS, best("Ah 5h", "Kh 9h 2c")) // flush draw
        assertEquals(Sizing.CHECK, best("8s 8d", "Kh Jd 2c")) // pair below the top card
        assertEquals(Sizing.POT, best("7s 7d", "7h Kd 2c 9s 4h")) // set on the river
        assertEquals(Sizing.CHECK, best("Qs Jd", "Ah 7d 2c 9s 4h")) // nothing on the river
    }

    @Test
    fun `bluff maths options and numbers`() {
        repeat(200) {
            val level = it % 3 + 1
            val t = drills.bluffMath(level)
            assertTrue(t.answer in t.options)
            assertEquals(4, t.options.toSet().size)
            assertTrue(t.explanation, t.explanation.contains(t.answer))
        }
        assertEquals("33%", "${Math.round(BluffMath.breakEvenFold(90, 45) * 100)}%")
    }

    @Test
    fun `explanations state the right decision`() {
        repeat(30) {
            val q = exercises.next(app.mast.poker.content.DrillType.PUSH_FOLD, it % 3 + 1) as Question.PushOrFold
            val says = if (q.shouldPush) "ставь всё" else "Пас выгоднее"
            assertTrue(q.explanation, q.explanation.contains(says))
        }
        repeat(60) {
            val q = exercises.next(app.mast.poker.content.DrillType.SIZING, it % 3 + 1) as Question.Choice
            val answer = q.options[q.correct]
            val first = answer.substringBefore(" ·")
            val mentions = when (first) {
                "Чек" -> "чек"
                "Треть банка" -> "трет"
                "Две трети" -> "две трети"
                else -> "весь банк"
            }
            assertTrue("$answer / ${q.explanation}", q.explanation.lowercase().contains(mentions))
        }
    }
}
