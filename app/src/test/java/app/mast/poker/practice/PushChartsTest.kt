package app.mast.poker.practice

import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandRange
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PushChartsTest {

    private fun h(n: String) = HandClasses.parse(n)

    @Test
    fun `charts agree with the push fold model`() {
        val tight = PushCharts.equities(10)
        val wide = PushCharts.equities(35)
        // Against tight callers even 72o is a shove from the small blind at 10 BB.
        assertTrue(h("72o") in PushCharts.chart(PushSeat.SB, 10.0, 10, tight).push)
        assertFalse(h("72o") in PushCharts.chart(PushSeat.BTN, 15.0, 35, wide).push)
        PushSeat.entries.forEach { seat -> assertTrue(h("AA") in PushCharts.chart(seat, 15.0, 35, wide).push) }
        // Deeper stacks and more players behind both shrink the range.
        val sb = (5..15).map { PushCharts.chart(PushSeat.SB, it.toDouble(), 35, wide).combos }
        assertTrue(sb.zipWithNext().all { (a, b) -> a >= b })
        assertTrue(PushCharts.chart(PushSeat.CO, 10.0, 35, wide).combos <= PushCharts.chart(PushSeat.BTN, 10.0, 35, wide).combos)
        // Clear spots match a precise evaluation.
        listOf("KJo", "A5s", "T8o", "55").forEach { name ->
            val hole = HandClasses.combosOf(h(name)).first()
            val precise = PushSeat.BTN.evaluate(hole, 10.0, HandRange.top(0.35), 40_000, Random(3))
            if (precise.margin > 0.3) assertTrue(name, precise.shouldPush == h(name) in PushCharts.chart(PushSeat.BTN, 10.0, 35, wide).push)
        }
    }
}
