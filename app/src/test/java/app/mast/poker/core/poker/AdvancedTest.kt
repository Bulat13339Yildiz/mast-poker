package app.mast.poker.core.poker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedTest {

    private fun h(n: String) = HandClasses.parse(n)

    @Test
    fun `range notation`() {
        assertEquals(setOf("QQ", "KK", "AA"), HandRange.parse("QQ+").hands.map { it.notation }.toSet())
        assertEquals(18, HandRange.parse("QQ+").comboCount)
        assertEquals(setOf("ATs", "AJs", "AQs", "AKs"), HandRange.parse("ATs+").hands.map { it.notation }.toSet())
        assertEquals(setOf("22", "33", "44", "55"), HandRange.parse("22-55").hands.map { it.notation }.toSet())
        assertEquals(setOf("A2s", "A3s", "A4s", "A5s"), HandRange.parse("A2s-A5s").hands.map { it.notation }.toSet())
        assertEquals(16, HandRange.parse("KQ").comboCount)
        assertEquals(169, HandClasses.all.size)
        assertEquals(1326, HandClasses.all.sumOf { HandClasses.combos(it) })
    }

    @Test
    fun `combinatorics`() {
        assertEquals(16, HandRange.parse("AK").comboCount)
        assertEquals(4, Combos.of(h("AKs")))
        assertEquals(12, Combos.of(h("AKo")))
        assertEquals(6, Combos.of(h("77")))
        // One ace visible: AK drops to 12 combos, AA to 3.
        assertEquals(12, HandRange.parse("AK").combos(cards("Ah")).size)
        assertEquals(3, Combos.of(h("AA"), cards("Ah")))
        // Sets on a rainbow K-7-2: three ranks × 3 combos.
        assertEquals(9, Combos.sets(cards("Kc 7d 2h")))
        // Holding a king leaves 1 combo of KK.
        assertEquals(7, Combos.sets(cards("Kc 7d 2h"), cards("Ks")))
        // Paired board: K-K-7 → only 77 makes a set.
        assertEquals(3, Combos.sets(cards("Kc Kd 7h")))
    }

    @Test
    fun `top ranges follow equity against a random hand`() {
        val top10 = HandRange.top(0.10)
        assertTrue(top10.percent >= 0.10 && top10.percent < 0.115)
        assertTrue(h("AA") in top10 && h("KK") in top10 && h("AKs") in top10)
        assertFalse(h("72o") in top10)
        assertEquals("AA", PreflopEquity.ranked.first().notation)
        assertEquals(0.852, PreflopEquity.of(h("AA")), 0.006)
        assertEquals(0.823, PreflopEquity.of(h("KK")), 0.006)
        assertEquals(0.670, PreflopEquity.of(h("AKs")), 0.007)
        assertEquals(0.323, PreflopEquity.of(h("32o")), 0.007)
        assertTrue(PreflopEquity.ranked.last().notation in setOf("32o", "42o", "52o"))
    }

    @Test
    fun `bluff maths`() {
        assertEquals(1.0 / 3, BluffMath.breakEvenFold(pot = 100, bet = 50), 1e-9)
        assertEquals(2.0 / 3, BluffMath.mdf(pot = 100, bet = 50), 1e-9)
        assertEquals(0.5, BluffMath.breakEvenFold(pot = 100, bet = 100), 1e-9)
        assertEquals(0.0, BluffMath.bluffEv(pot = 100, bet = 50, foldFreq = 1.0 / 3), 1e-9)
        assertEquals(5.0, CallEv.of(pot = 150, call = 50, equity = 0.275), 1e-9)
    }

    @Test
    fun `range equity`() {
        assertEquals(0.82, RangeEquity.vsRange(cards("Ac Ad"), HandRange.parse("KK"), samples = 8000), 0.02)
        assertEquals(PreflopEquity.of(h("AKo")), RangeEquity.vsRandom(cards("Ah Kd")), 1e-9)
    }

    @Test
    fun `push fold`() {
        val any = HandRange(HandClasses.all.toSet())
        val trash = PushFold.evaluate(cards("7h 2d"), stackBb = 15.0, callRange = any)
        assertEquals(1.0, trash.callChance, 1e-9)
        assertFalse(trash.shouldPush)
        val aces = PushFold.evaluate(cards("Ah Ad"), stackBb = 10.0, callRange = HandRange.top(0.5))
        assertTrue(aces.shouldPush)
        // Against a very tight caller, fold equity alone makes even 72o a profitable push.
        val vsNit = PushFold.evaluate(cards("7h 2d"), stackBb = 10.0, callRange = HandRange.parse("QQ+, AK"))
        assertTrue(vsNit.callChance < 0.03)
        assertTrue(vsNit.shouldPush)
    }

    @Test
    fun `variance`() {
        assertEquals(0.5, Variance.phi(0.0), 1e-7)
        assertEquals(0.975, Variance.phi(1.96), 1e-3)
        assertEquals(0.025, Variance.phi(-1.96), 1e-3)
        // 5 bb/100, SD 90, 10 000 hands: expected +500, σ = 900 → ~29% to be behind.
        assertEquals(500.0, Variance.expected(5.0, 10_000), 1e-9)
        assertEquals(900.0, Variance.stdDev(90.0, 10_000), 1e-9)
        assertEquals(0.289, Variance.lossChance(5.0, 90.0, 10_000), 0.002)
        val paths = Variance.simulate(400, 10_000, 5.0, 90.0, kotlin.random.Random(1))
        val finals = paths.map { it.last().toDouble() }
        assertEquals(500.0, finals.average(), 120.0)
    }
}
