package app.mast.poker.content

import app.mast.poker.content.chapters.BubbleExample
import app.mast.poker.core.poker.BluffMath
import app.mast.poker.core.poker.CallEv
import app.mast.poker.core.poker.Combos
import app.mast.poker.core.poker.EquityCalculator
import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.Icm
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.PreflopEquity
import app.mast.poker.core.poker.PushFold
import app.mast.poker.core.poker.RangeEquity
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.cards
import app.mast.poker.practice.BetStreet
import app.mast.poker.practice.Sizing
import app.mast.poker.practice.SizingRules
import app.mast.poker.practice.sim.HandReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

/** Every number the second-level chapters state, recomputed by the engine. */
class Level2ClaimsTest {

    private fun h(n: String) = HandClasses.parse(n)
    private fun pct(x: Double) = (x * 100).roundToInt()

    @Test
    fun `hand classes and combos`() {
        val all = HandClasses.all
        assertEquals(169, all.size)
        assertEquals(13, all.count { it.isPair })
        assertEquals(78, all.count { it.suited })
        assertEquals(78, all.count { !it.isPair && !it.suited })
        assertEquals(312, all.filter { it.suited }.sumOf { HandClasses.combos(it) })
        assertEquals(936, all.filter { !it.isPair && !it.suited }.sumOf { HandClasses.combos(it) })
        assertEquals(1326, all.sumOf { HandClasses.combos(it) })
        assertEquals(16, HandRange.parse("AK").comboCount)
        assertEquals(34, HandRange.parse("QQ+, AK").comboCount)
        // "около 133 комбинаций" in the top 10%.
        assertEquals(133.0, HandRange.top(0.10).comboCount.toDouble(), 3.0)
    }

    @Test
    fun `equity against a random hand`() {
        assertEquals(85, pct(PreflopEquity.of(h("AA"))))
        assertEquals(65, pct(PreflopEquity.of(h("AKo"))))
        assertEquals(34, pct(PreflopEquity.of(h("72o"))))
        assertTrue(PreflopEquity.of(h("76s")) < 0.5)
        assertEquals("AA", PreflopEquity.ranked.first().notation)
    }

    @Test
    fun `range reading examples`() {
        val early = openingRange(SeatGroup.EARLY)
        assertFalse(h("J4o") in early)
        assertFalse(h("72o") in early)
        assertTrue(listOf("QQ", "AKs", "KQs", "77").all { h(it) in early })
        assertTrue(h("K9s") in openingRange(SeatGroup.LATE))
        assertTrue(PreflopChart.shouldOpen(h("AJo"), SeatGroup.LATE))
        // On A♥ 8♦ 3♣ the early range's sets are exactly AA and 88.
        val board = cards("Ah 8d 3c")
        val setPairs = early.filter { it.isPair && board.any { c -> c.rank == it.high } }.map { it.notation }.toSet()
        assertEquals(setPairs, setOf("AA", "88"))
    }

    @Test
    fun `expected value examples`() {
        assertEquals(5.0, 0.5 * 20 - 0.5 * 10, 1e-9)
        assertEquals(5.0, 0.5 * 30 - 0.5 * 20, 1e-9)
        // Flush draw on the turn: 9 of 46, "около 20%", EV of the call "≈ +4".
        val draw = PotOdds.exactHitChance(9, 46, 1)
        assertEquals(20, pct(draw))
        assertEquals(4.0, CallEv.of(pot = 100, call = 20, equity = 0.2), 1e-9)
        assertEquals(3.5, CallEv.of(pot = 100, call = 20, equity = draw), 0.1)
        assertEquals(10.0, CallEv.of(pot = 100, call = 20, equity = 0.25), 1e-9)
        assertEquals(82, pct(RangeEquity.vsRange(cards("Ac Ad"), HandRange.parse("KK"), samples = 40_000, random = Random(3))))
        // Flush draw on the flop to the river: "примерно 35%".
        assertEquals(35, pct(PotOdds.exactHitChance(9, 47, 2)))
    }

    @Test
    fun `bluff and defence tables`() {
        assertEquals(25, pct(BluffMath.breakEvenFold(90, 30)))
        assertEquals(33, pct(BluffMath.breakEvenFold(100, 50)))
        assertEquals(40, pct(BluffMath.breakEvenFold(150, 100)))
        assertEquals(50, pct(BluffMath.breakEvenFold(100, 100)))
        assertEquals(75, pct(BluffMath.mdf(90, 30)))
        assertEquals(67, pct(BluffMath.mdf(100, 50)))
        assertEquals(50, pct(BluffMath.mdf(100, 100)))
        assertEquals(10.0, BluffMath.bluffEv(100, 50, 0.4), 1e-9)
        assertEquals(-5.0, BluffMath.bluffEv(100, 50, 0.3), 1e-9)
        assertEquals(-20.0, BluffMath.bluffEv(100, 50, 0.2), 1e-9)
    }

    @Test
    fun `blockers`() {
        assertEquals(3, Combos.of(h("AA"), cards("As")))
        assertEquals(12, HandRange.parse("AK").combos(cards("As")).size)
        assertEquals(9, Combos.sets(cards("Kc 7d 2h")))
        assertEquals(7, Combos.sets(cards("Kc 7d 2h"), cards("Ks 9d")))
        assertEquals(9, Combos.sets(cards("Qd 8c 3h")))
    }

    @Test
    fun `sizing lesson spots follow the plan`() {
        fun best(hole: String, board: String): Sizing {
            val b = cards(board)
            val street = BetStreet.entries.first { it.boardCards == b.size }
            return SizingRules.best(street, HandReader.read(cards(hole), b), HandReader.isWet(b))
        }
        assertEquals(Sizing.THIRD, best("Ah Qc", "Qs 8h 3d"))
        assertEquals(Sizing.TWO_THIRDS, best("8s 8d", "Jh Th 8c"))
        assertEquals(Sizing.CHECK, best("Ac Kd", "9h 8h 7c"))
        assertFalse(HandReader.isWet(cards("Ks 7d 2c")))
        assertTrue(HandReader.isWet(cards("Jh Th 8c")))
    }

    @Test
    fun `preflop two numbers`() {
        // "около 25%" and "около 36%".
        assertEquals(0.25, RangeEquity.vsRange(cards("Ah Qd"), HandRange.parse("QQ+, AK"), samples = 60_000, random = Random(4)), 0.012)
        assertEquals(0.36, RangeEquity.vsRange(cards("Jh Jd"), HandRange.parse("QQ+, AK"), samples = 60_000, random = Random(5)), 0.012)
        // Big blind against a 2 BB open: pay 1 into 3.5.
        assertEquals(22, pct(1.0 / (3.5 + 1.0)))
        // 3-bet to 9-12 BB, 4-bet 2.2–2.5× of 9 BB is 20–22 BB.
        assertEquals(19.8, 2.2 * 9, 1e-9)
        assertEquals(22.5, 2.5 * 9, 1e-9)
    }

    @Test
    fun `push fold lesson numbers`() {
        val r = PushFold.evaluate(cards("As 7d"), 10.0, HandRange.top(0.20), samples = 100_000, random = Random(6))
        assertEquals(235, (r.callChance * 1225).roundToInt())
        assertEquals(19, pct(r.callChance))
        assertEquals(42, pct(r.equityWhenCalled))
        assertEquals(-1.6, 20 * r.equityWhenCalled - 10, 0.08)
        assertEquals(0.5, r.evPush, 0.04)
        assertTrue(r.shouldPush)

        val trash = PushFold.evaluate(cards("7h 2d"), 10.0, HandRange.top(0.10), samples = 100_000, random = Random(7))
        assertEquals(89, pct(1 - trash.callChance))
        assertEquals(0.3, trash.evPush, 0.08)
        assertTrue(trash.shouldPush)

        val q3 = PushFold.evaluate(cards("Qh 3d"), 15.0, HandRange.top(0.50), samples = 100_000, random = Random(8))
        assertFalse(q3.shouldPush)
    }

    @Test
    fun `bubble maths`() {
        assertEquals(25.0, BubbleExample.before, 1e-9)
        assertEquals(38, BubbleExample.afterDouble.roundToInt())
        assertEquals(65, (BubbleExample.needToCall * 100).roundToInt())
        val eq = Icm.equities(listOf(5000.0, 3000.0, 2000.0), listOf(50.0, 30.0, 20.0))
        assertEquals(100.0, eq.sum(), 1e-9)
        assertTrue(eq[0] > eq[1] && eq[1] > eq[2])
        // Chips are not money: double the chips is less than double the share.
        assertTrue(BubbleExample.afterDouble < 2 * BubbleExample.before)
    }

    @Test
    fun `heads up spot from the EV lesson`() {
        val e = EquityCalculator.headsUp(cards("Ac Ad"), cards("Kc Kd"), samples = 40_000)
        assertEquals(82, pct(e.win + e.tie / 2))
    }
}
