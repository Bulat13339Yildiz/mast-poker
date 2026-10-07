package app.mast.poker.core.poker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandEvaluatorTest {

    private fun cat(spec: String) = HandEvaluator.evaluate(cards(spec)).category
    private fun value(spec: String) = HandEvaluator.evaluate(cards(spec))

    @Test
    fun `all 2598960 five-card hands have reference category counts`() {
        val counts = IntArray(HandCategory.entries.size)
        forEachCombination(Deck.full, 5) { counts[HandEvaluator.evaluate5(it).category.ordinal]++ }
        val expected = mapOf(
            HandCategory.ROYAL_FLUSH to 4,
            HandCategory.STRAIGHT_FLUSH to 36,
            HandCategory.FOUR_OF_A_KIND to 624,
            HandCategory.FULL_HOUSE to 3744,
            HandCategory.FLUSH to 5108,
            HandCategory.STRAIGHT to 10200,
            HandCategory.THREE_OF_A_KIND to 54912,
            HandCategory.TWO_PAIR to 123552,
            HandCategory.PAIR to 1098240,
            HandCategory.HIGH_CARD to 1302540,
        )
        expected.forEach { (c, n) -> assertEquals("count of $c", n, counts[c.ordinal]) }
        assertEquals(2598960, counts.sum())
    }

    @Test
    fun `categories are recognised`() {
        assertEquals(HandCategory.ROYAL_FLUSH, cat("As Ks Qs Js Ts"))
        assertEquals(HandCategory.STRAIGHT_FLUSH, cat("9h 8h 7h 6h 5h"))
        assertEquals(HandCategory.STRAIGHT_FLUSH, cat("Ad 2d 3d 4d 5d"))
        assertEquals(HandCategory.FOUR_OF_A_KIND, cat("7c 7d 7h 7s Kd"))
        assertEquals(HandCategory.FULL_HOUSE, cat("Qc Qd Qh 4s 4d"))
        assertEquals(HandCategory.FLUSH, cat("Ac 9c 7c 4c 2c"))
        assertEquals(HandCategory.STRAIGHT, cat("Tc Jd Qh Ks Ad"))
        assertEquals(HandCategory.STRAIGHT, cat("Ac 2d 3h 4s 5d"))
        assertEquals(HandCategory.THREE_OF_A_KIND, cat("8c 8d 8h Ks 2d"))
        assertEquals(HandCategory.TWO_PAIR, cat("Jc Jd 4h 4s 9d"))
        assertEquals(HandCategory.PAIR, cat("Ac Ad 9h 6s 3d"))
        assertEquals(HandCategory.HIGH_CARD, cat("Ac Jd 9h 6s 3d"))
    }

    @Test
    fun `not a straight - wrap around K A 2`() {
        assertEquals(HandCategory.HIGH_CARD, cat("Qc Kd Ah 2s 3d"))
    }

    @Test
    fun `wheel is the lowest straight`() {
        assertTrue(value("Ac 2d 3h 4s 5d") < value("2c 3d 4h 5s 6d"))
        assertEquals(listOf(5), value("Ac 2d 3h 4s 5d").tiebreak)
    }

    @Test
    fun `kickers decide equal pairs`() {
        assertTrue(value("Ac Ad Kh 6s 3d") > value("Ah As Qh Js Td"))
        assertTrue(value("Ac Ad Kh 7s 3d") > value("Ah As Kd 6s 5d"))
    }

    @Test
    fun `two pair compares top pair, then second pair, then kicker`() {
        assertTrue(value("Kc Kd 2h 2s 3d") > value("Qc Qd Jh Js Ad"))
        assertTrue(value("Kc Kd 5h 5s 3d") > value("Kh Ks 4d 4c Ad"))
        assertTrue(value("Kc Kd 5h 5s 9d") > value("Kh Ks 5d 5c 8d"))
    }

    @Test
    fun `full house compares trips first`() {
        assertTrue(value("3c 3d 3h 2s 2d") > value("2c 2h 2s Ac Ad"))
    }

    @Test
    fun `flush compares all five cards`() {
        assertTrue(value("Ac Jc 9c 6c 4c") > value("Ad Jd 9d 6d 3d"))
    }

    @Test
    fun `best five of seven`() {
        // Three pairs on 7 cards → best two pair with the highest kicker.
        val v = value("Ac Ad Kh Ks 5c 5d 9h")
        assertEquals(HandCategory.TWO_PAIR, v.category)
        assertEquals(listOf(14, 13, 9), v.tiebreak)
        // Flush on board, hole cards add a higher flush card.
        val f = HandEvaluator.evaluate(cards("Ah 2c"), cards("Kh 9h 7h 4h 3h"))
        assertEquals(HandCategory.FLUSH, f.category)
        assertEquals(14, f.tiebreak.first())
        // Straight flush beats the made flush found among the 7 cards.
        assertEquals(HandCategory.STRAIGHT_FLUSH, cat("9s 8s 7s 6s 5s As Ks"))
    }

    @Test
    fun `split when board plays`() {
        val r = Showdown.resolve(cards("Ac Kd Qh Js Td"), listOf(cards("2c 3d"), cards("4h 5s")))
        assertTrue(r.isSplit)
        assertEquals(listOf(0, 1), r.winners)
    }

    @Test
    fun `kicker wins on paired board`() {
        val r = Showdown.resolve(cards("Ac Ad 9h 6s 3d"), listOf(cards("Kc 2d"), cards("Qh Js")))
        assertEquals(listOf(0), r.winners)
    }

    @Test
    fun `all best fives returns every equal subset`() {
        val fives = HandEvaluator.allBestFives(cards("Ah Ad As Ac Kh Ks Qd"))
        assertEquals(2, fives.size)
        fives.forEach { assertEquals(HandCategory.FOUR_OF_A_KIND, HandEvaluator.evaluate5(it).category) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicates are rejected`() {
        HandEvaluator.evaluate(cards("Ah Ah Kd Qc Js"))
    }
}
