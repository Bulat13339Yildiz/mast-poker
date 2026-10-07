package app.mast.poker.core.poker

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class FastEvalTest {

    @Test
    fun `agrees with the reference evaluator on category and order`() {
        val rnd = Random(3)
        repeat(60_000) {
            val deck = Deck.shuffled(rnd)
            val a = deck.subList(0, 7).toList()
            val b = deck.subList(7, 14).toList()
            val ra = HandEvaluator.evaluate(a)
            val rb = HandEvaluator.evaluate(b)
            val fa = FastEval.score(a)
            val fb = FastEval.score(b)
            assertEquals("$a", ra.category, FastEval.category(fa))
            assertEquals("$a vs $b", Integer.signum(ra.compareTo(rb)), Integer.signum(fa.compareTo(fb)))
        }
    }

    @Test
    fun `five card category counts match`() {
        val counts = IntArray(HandCategory.entries.size)
        val idx = IntArray(5)
        val all = Deck.full
        forEachCombination(all, 5) { hand ->
            for (i in 0 until 5) idx[i] = FastEval.index(hand[i])
            counts[FastEval.score(idx, 5) ushr 20]++
        }
        assertEquals(4, counts[HandCategory.ROYAL_FLUSH.ordinal])
        assertEquals(36, counts[HandCategory.STRAIGHT_FLUSH.ordinal])
        assertEquals(624, counts[HandCategory.FOUR_OF_A_KIND.ordinal])
        assertEquals(3744, counts[HandCategory.FULL_HOUSE.ordinal])
        assertEquals(5108, counts[HandCategory.FLUSH.ordinal])
        assertEquals(10200, counts[HandCategory.STRAIGHT.ordinal])
        assertEquals(54912, counts[HandCategory.THREE_OF_A_KIND.ordinal])
        assertEquals(123552, counts[HandCategory.TWO_PAIR.ordinal])
        assertEquals(1098240, counts[HandCategory.PAIR.ordinal])
        assertEquals(1302540, counts[HandCategory.HIGH_CARD.ordinal])
    }

    @Test
    fun `tricky hands`() {
        fun s(spec: String) = FastEval.score(cards(spec))
        // Wheel is the lowest straight; three pairs play the top two with the best kicker.
        assert(s("Ac 2d 3h 4s 5d Kc Qh") < s("2c 3d 4h 5s 6d Kc Qh"))
        assertEquals(HandCategory.TWO_PAIR, FastEval.category(s("Ac Ad Kh Ks 5c 5d 9h")))
        assert(s("Ac Ad Kh Ks 5c 5d 9h") < s("Ac Ad Kh Ks 5c 5d Qh"))
        // Two trips make a full house with the higher trips on top.
        assert(s("8c 8d 8h 4s 4d 4h 2c") > s("7c 7d 7h As Ad Kh 2c"))
    }
}
