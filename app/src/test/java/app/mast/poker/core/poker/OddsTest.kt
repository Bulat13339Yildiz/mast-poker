package app.mast.poker.core.poker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OddsTest {

    @Test
    fun `flush draw on the flop has 9 outs`() {
        val outs = Outs.toCategory(cards("Ah Kh"), cards("7h 2h 9c"), HandCategory.FLUSH)
        assertEquals(9, outs.size)
    }

    @Test
    fun `open-ended straight draw has 8 outs`() {
        val outs = Outs.toCategory(cards("9c 8d"), cards("7h 6s 2c"), HandCategory.STRAIGHT)
        assertEquals(8, outs.size)
    }

    @Test
    fun `gutshot has 4 outs`() {
        val outs = Outs.toCategory(cards("9c 8d"), cards("6h 5s Kc"), HandCategory.STRAIGHT)
        assertEquals(4, outs.size)
    }

    @Test
    fun `flush plus open-ended draw has 15 outs`() {
        // Straight-or-better counts the flush cards too: 9 hearts + 6 off-suit fives and tens.
        val outs = Outs.toCategory(cards("9h 8h"), cards("7h 6c 2h"), HandCategory.STRAIGHT)
        assertEquals(15, outs.size)
    }

    @Test
    fun `set on the flop has 7 outs to a full house or better`() {
        val outs = Outs.toCategory(cards("7c 7d"), cards("7h Ks 2c"), HandCategory.FULL_HOUSE)
        assertEquals(7, outs.size)
    }

    @Test
    fun `pot odds`() {
        assertEquals(0.25, PotOdds.requiredEquity(pot = 150, call = 50), 1e-9)
        assertEquals(36, PotOdds.ruleOfTwoAndFour(outs = 9, cardsToCome = 2))
        assertEquals(18, PotOdds.ruleOfTwoAndFour(outs = 9, cardsToCome = 1))
        assertEquals(0.3497, PotOdds.exactHitChance(outs = 9, unseen = 47, cardsToCome = 2), 1e-3)
        assertEquals(0.1956, PotOdds.exactHitChance(outs = 9, unseen = 46, cardsToCome = 1), 1e-3)
        assertTrue(PotOdds.isProfitableCall(pot = 150, call = 50, equity = 0.35))
    }

    @Test
    fun `aces are a big favourite over kings preflop`() {
        val eq = EquityCalculator.headsUp(cards("Ac Ad"), cards("Kh Ks"), samples = 6000)
        assertEquals(0.82, eq.win, 0.03)
    }

    /** The numbers quoted in the «Кто фаворит» lesson. */
    @Test
    fun `lesson equity claims hold`() {
        fun eq(a: String, b: String): Double {
            val e = EquityCalculator.headsUp(cards(a), cards(b), samples = 30000)
            return e.win + e.tie / 2
        }
        assertEquals(0.57, eq("Qs Qh", "Ad Kc"), 0.025)
        assertEquals(0.74, eq("As Kd", "Ah Qc"), 0.025)
        assertEquals(0.74, eq("As Kd", "Ah Jc"), 0.03)
        assertEquals(0.81, eq("Ks Kd", "9h 9c"), 0.025)
        assertEquals(0.55, eq("7s 7d", "Ah Kc"), 0.025)
    }

    @Test
    fun `turn equity is exact`() {
        // Flush draw vs a set: 9 hearts left, but 2♥ and 3♥ pair the board and give the set a full house.
        val eq = EquityCalculator.headsUp(cards("Ah Kh"), cards("Qc Qd"), cards("Qh 7h 2s 3c"))
        assertEquals(7.0 / 44, eq.win, 1e-9)
        assertEquals(0.0, eq.tie, 1e-9)
    }
}
