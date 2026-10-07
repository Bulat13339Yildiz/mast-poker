package app.mast.poker.practice

import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.Outs
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.Showdown
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.cards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DrillGeneratorTest {

    private val gen = DrillGenerator(Random(42))

    private fun distinct(cards: List<Card>) = assertEquals(cards.toString(), cards.size, cards.toSet().size)

    @Test
    fun `every category can be dealt in 5 and 7 cards`() {
        HandCategory.entries.forEach { c ->
            listOf(5, 7).forEach { n ->
                val dealt = gen.dealCategory(c, n)
                assertEquals(n, dealt.size)
                distinct(dealt)
                assertEquals(c, HandEvaluator.evaluate(dealt).category)
            }
        }
    }

    @Test
    fun `name hand tasks are consistent`() {
        repeat(300) {
            val t = gen.nameHand()
            distinct(t.hole + t.board)
            assertEquals(HandEvaluator.evaluate(t.hole + t.board).category, t.answer)
            assertTrue(t.answer in t.options)
            assertEquals(4, t.options.toSet().size)
        }
    }

    @Test
    fun `winner tasks are consistent`() {
        repeat(200) {
            val t = gen.winner()
            distinct(t.board + t.hands.flatten())
            assertEquals(Showdown.resolve(t.board, t.hands).winners, t.winners)
        }
    }

    @Test
    fun `best five example is accepted`() {
        repeat(200) {
            val t = gen.bestFive()
            distinct(t.hole + t.board)
            assertTrue(t.isCorrect(t.example.toSet()))
            assertFalse(t.isCorrect((t.hole + t.board).take(4).toSet()))
        }
    }

    @Test
    fun `outs tasks match the engine`() {
        repeat(120) {
            val t = gen.outs()
            distinct(t.hole + t.board)
            assertEquals(Outs.toCategory(t.hole, t.board, t.target).size, t.answer)
            assertTrue(t.answer in t.options)
            assertEquals(4, t.options.toSet().size)
        }
    }

    @Test
    fun `pot odds tasks are never coin flips`() {
        repeat(300) {
            val t = gen.potOdds()
            assertTrue(abs(t.estimatedEquity / 100.0 - t.requiredEquity) >= 0.04)
            assertTrue(abs(t.exactChance - t.requiredEquity) >= 0.03)
            assertEquals(t.estimatedEquity / 100.0 >= t.requiredEquity, t.shouldCall)
            assertTrue(t.bet > 0 && t.potBeforeBet > 0)
        }
    }

    @Test
    fun `preflop tasks follow the chart`() {
        repeat(300) {
            val t = gen.preflop()
            distinct(t.hole)
            assertEquals(PreflopChart.shouldOpen(t.hand, t.position.seatGroup), t.shouldOpen)
        }
    }

    @Test
    fun `equity tasks have a clear favourite`() {
        repeat(40) {
            val t = gen.equity()
            distinct(t.hands.flatten() + t.board)
            assertTrue(t.board.size in listOf(0, 3, 4))
            assertTrue(abs(t.equity - 0.5) >= 0.08)
        }
    }

    @Test
    fun `beginner chart sanity`() {
        fun h(spec: String) = cards(spec).let { StartingHand.of(it[0], it[1]) }
        assertTrue(PreflopChart.shouldOpen(h("Ac Ad"), SeatGroup.EARLY))
        assertTrue(PreflopChart.shouldOpen(h("Ah Qd"), SeatGroup.EARLY))
        assertFalse(PreflopChart.shouldOpen(h("Ah Jd"), SeatGroup.EARLY))
        assertTrue(PreflopChart.shouldOpen(h("Ah Jd"), SeatGroup.MIDDLE))
        assertFalse(PreflopChart.shouldOpen(h("7h 2d"), SeatGroup.LATE))
        assertTrue(PreflopChart.shouldOpen(h("2h 2d"), SeatGroup.LATE))
        assertFalse(PreflopChart.shouldOpen(h("2h 2d"), SeatGroup.EARLY))
        assertTrue(PreflopChart.shouldOpen(h("7h 6h"), SeatGroup.LATE))
        assertFalse(PreflopChart.shouldOpen(h("7h 6d"), SeatGroup.LATE))
        assertEquals("AKs", h("Ah Kh").notation)
        assertEquals("T9o", h("9c Td").notation)
        assertEquals("77", h("7c 7d").notation)
    }
}
