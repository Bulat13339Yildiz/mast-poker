package app.mast.poker.content

import app.mast.poker.core.poker.Deck
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.HandEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** The numbers shown to the player about how often things happen. */
class FrequencyClaimsTest {

    @Test
    fun `ladder frequencies sum to one`() {
        assertEquals(1.0, Reference.ladder.sumOf { it.frequency }, 1e-9)
        assertEquals("1 на 31 000", Reference.ladder.first { it.category == HandCategory.ROYAL_FLUSH }.odds.replace(' ', ' '))
        assertEquals("1 на 600", Reference.ladder.first { it.category == HandCategory.FOUR_OF_A_KIND }.odds)
    }

    @Test
    fun `seven card frequencies match a simulation`() {
        val rnd = Random(2026)
        val n = 600_000
        val counts = IntArray(HandCategory.entries.size)
        val deck = Deck.full.toMutableList()
        repeat(n) {
            for (i in 0 until 7) {
                val j = i + rnd.nextInt(52 - i)
                val t = deck[i]; deck[i] = deck[j]; deck[j] = t
            }
            counts[HandEvaluator.evaluate(deck.subList(0, 7)).category.ordinal]++
        }
        Reference.ladder.forEach { ex ->
            val observed = counts[ex.category.ordinal].toDouble() / n
            val sigma = sqrt(ex.frequency * (1 - ex.frequency) / n)
            assertTrue(
                "${ex.category}: claimed ${ex.frequency}, observed $observed",
                abs(observed - ex.frequency) <= 5 * sigma + 1e-5,
            )
        }
    }

    @Test
    fun `lesson probability claims`() {
        fun c(n: Int, k: Int): Double = (0 until k).fold(1.0) { acc, i -> acc * (n - i) / (i + 1) }
        // "Карманная пара собирает сет на флопе примерно один раз из восьми".
        val set = 1 - c(48, 3) / c(50, 3)
        assertEquals(1.0 / 8, set, 0.01)
        // "Две разные карты собирают хотя бы пару на флопе примерно в трети случаев".
        val pair = 1 - c(44, 3) / c(50, 3)
        assertEquals(1.0 / 3, pair, 0.015)
        // "Флеш-дро на флопе: точно 35% к риверу; на тёрне — около 20%".
        assertEquals(0.35, app.mast.poker.core.poker.PotOdds.exactHitChance(9, 47, 2), 0.005)
        assertEquals(0.20, app.mast.poker.core.poker.PotOdds.exactHitChance(9, 46, 1), 0.005)
    }
}
