package app.mast.poker.core.poker

/**
 * Allocation-free evaluator for 5–7 cards, used by simulations. Returns an Int
 * score; a higher score is a stronger hand. Agrees with [HandEvaluator] on order
 * and category (checked by tests); [HandEvaluator] stays the source for UI details.
 */
object FastEval {

    /** Card index 0..51: rank index (2 → 0 … A → 12) × 4 + suit ordinal. */
    fun index(c: Card): Int = (c.rank.value - 2) * 4 + c.suit.ordinal

    val deck: IntArray = IntArray(52) { it }

    fun category(score: Int): HandCategory = HandCategory.entries[score ushr 20]

    fun score(cards: List<Card>): Int {
        val idx = IntArray(cards.size) { index(cards[it]) }
        return score(idx, idx.size)
    }

    private fun encode(cat: HandCategory, vararg ranks: Int): Int {
        var s = cat.ordinal shl 20
        var shift = 16
        for (r in ranks) {
            s = s or (r shl shift)
            shift -= 4
        }
        return s
    }

    /** Highest rank index of a 5-long run in [mask], 3 for the wheel, -1 if none. */
    private fun straightHigh(mask: Int): Int {
        for (high in 12 downTo 4) if ((mask ushr (high - 4)) and 0x1F == 0x1F) return high
        if (mask and 0x100F == 0x100F) return 3
        return -1
    }

    fun score(cards: IntArray, n: Int): Int {
        val suitMask = IntArray(4)
        val counts = IntArray(13)
        var rankMask = 0
        for (i in 0 until n) {
            val c = cards[i]
            val r = c shr 2
            suitMask[c and 3] = suitMask[c and 3] or (1 shl r)
            counts[r]++
            rankMask = rankMask or (1 shl r)
        }
        for (s in 0 until 4) {
            val m = suitMask[s]
            if (Integer.bitCount(m) >= 5) {
                val sf = straightHigh(m)
                if (sf >= 0) return encode(if (sf == 12) HandCategory.ROYAL_FLUSH else HandCategory.STRAIGHT_FLUSH, sf)
                val top = IntArray(5)
                var k = 0
                var r = 12
                while (k < 5) {
                    if (m and (1 shl r) != 0) top[k++] = r
                    r--
                }
                return encode(HandCategory.FLUSH, top[0], top[1], top[2], top[3], top[4])
            }
        }
        var quad = -1
        var trip1 = -1
        var trip2 = -1
        var pair1 = -1
        var pair2 = -1
        for (r in 12 downTo 0) {
            when (counts[r]) {
                4 -> if (quad < 0) quad = r
                3 -> if (trip1 < 0) trip1 = r else if (trip2 < 0) trip2 = r
                2 -> if (pair1 < 0) pair1 = r else if (pair2 < 0) pair2 = r
            }
        }
        if (quad >= 0) {
            var kicker = -1
            for (r in 12 downTo 0) if (r != quad && counts[r] > 0) { kicker = r; break }
            return encode(HandCategory.FOUR_OF_A_KIND, quad, kicker)
        }
        if (trip1 >= 0 && (trip2 >= 0 || pair1 >= 0)) {
            return encode(HandCategory.FULL_HOUSE, trip1, maxOf(trip2, pair1))
        }
        val st = straightHigh(rankMask)
        if (st >= 0) return encode(HandCategory.STRAIGHT, st)
        if (trip1 >= 0) {
            val k = kickers(counts, 2, trip1, -1)
            return encode(HandCategory.THREE_OF_A_KIND, trip1, k[0], k[1])
        }
        if (pair1 >= 0 && pair2 >= 0) {
            val k = kickers(counts, 1, pair1, pair2)
            return encode(HandCategory.TWO_PAIR, pair1, pair2, k[0])
        }
        if (pair1 >= 0) {
            val k = kickers(counts, 3, pair1, -1)
            return encode(HandCategory.PAIR, pair1, k[0], k[1], k[2])
        }
        val k = kickers(counts, 5, -1, -1)
        return encode(HandCategory.HIGH_CARD, k[0], k[1], k[2], k[3], k[4])
    }

    private fun kickers(counts: IntArray, n: Int, skipA: Int, skipB: Int): IntArray {
        val out = IntArray(n)
        var k = 0
        var r = 12
        while (k < n && r >= 0) {
            if (r != skipA && r != skipB && counts[r] > 0) out[k++] = r
            r--
        }
        return out
    }
}
