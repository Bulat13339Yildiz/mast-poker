package app.mast.poker.core.poker

/**
 * Value of a 5-card hand. [tiebreak] holds rank values in the order they are compared,
 * so two hands of the same category compare lexicographically.
 */
data class HandValue(
    val category: HandCategory,
    val tiebreak: List<Int>,
    val cards: List<Card>,
) : Comparable<HandValue> {
    override fun compareTo(other: HandValue): Int {
        if (category != other.category) return category.compareTo(other.category)
        for (i in tiebreak.indices) {
            val c = tiebreak[i].compareTo(other.tiebreak[i])
            if (c != 0) return c
        }
        return 0
    }

    fun sameStrength(other: HandValue): Boolean = compareTo(other) == 0
}

object HandEvaluator {

    fun evaluate5(hand: List<Card>): HandValue {
        require(hand.size == 5) { "Need exactly 5 cards, got ${hand.size}" }
        val values = IntArray(5) { hand[it].rank.value }.sortedArrayDescending()
        val flush = hand.all { it.suit == hand[0].suit }
        val straightHigh = straightHigh(values)

        if (flush && straightHigh != null) {
            val cat = if (straightHigh == 14) HandCategory.ROYAL_FLUSH else HandCategory.STRAIGHT_FLUSH
            return HandValue(cat, listOf(straightHigh), orderStraight(hand, straightHigh))
        }

        val groups = values.toList().groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        val shape = groups.map { it.value }
        val groupRanks = groups.map { it.key }
        val ordered = orderByGroups(hand, groupRanks)

        return when {
            shape == listOf(4, 1) -> HandValue(HandCategory.FOUR_OF_A_KIND, groupRanks, ordered)
            shape == listOf(3, 2) -> HandValue(HandCategory.FULL_HOUSE, groupRanks, ordered)
            flush -> HandValue(HandCategory.FLUSH, values.toList(), ordered)
            straightHigh != null -> HandValue(HandCategory.STRAIGHT, listOf(straightHigh), orderStraight(hand, straightHigh))
            shape == listOf(3, 1, 1) -> HandValue(HandCategory.THREE_OF_A_KIND, groupRanks, ordered)
            shape == listOf(2, 2, 1) -> HandValue(HandCategory.TWO_PAIR, groupRanks, ordered)
            shape == listOf(2, 1, 1, 1) -> HandValue(HandCategory.PAIR, groupRanks, ordered)
            else -> HandValue(HandCategory.HIGH_CARD, values.toList(), ordered)
        }
    }

    /** Best 5-card hand out of 5..7 cards. */
    fun evaluate(cards: List<Card>): HandValue {
        require(cards.size in 5..7) { "Need 5..7 cards, got ${cards.size}" }
        require(cards.toSet().size == cards.size) { "Duplicate cards: $cards" }
        if (cards.size == 5) return evaluate5(cards)
        var best: HandValue? = null
        forEachCombination(cards, 5) { combo ->
            val v = evaluate5(combo)
            if (best == null || v > best!!) best = v
        }
        return best!!
    }

    fun evaluate(hole: List<Card>, board: List<Card>): HandValue = evaluate(hole + board)

    /** Every 5-card subset of [cards] whose value equals the best value. */
    fun allBestFives(cards: List<Card>): List<List<Card>> {
        val best = evaluate(cards)
        val result = mutableListOf<List<Card>>()
        forEachCombination(cards, 5) { combo ->
            if (evaluate5(combo).sameStrength(best)) result += combo.toList()
        }
        return result
    }

    private fun straightHigh(desc: IntArray): Int? {
        if (desc.distinct().size != 5) return null
        if (desc[0] - desc[4] == 4) return desc[0]
        if (desc[0] == 14 && desc[1] == 5 && desc[4] == 2) return 5
        return null
    }

    private fun orderStraight(hand: List<Card>, high: Int): List<Card> {
        val wheel = high == 5
        return hand.sortedByDescending { if (wheel && it.rank == Rank.ACE) 1 else it.rank.value }
    }

    private fun orderByGroups(hand: List<Card>, groupRanks: List<Int>): List<Card> =
        hand.sortedWith(compareBy<Card> { groupRanks.indexOf(it.rank.value) }.thenBy { it.suit.ordinal })
}

/** Calls [action] with each k-combination of [items]. The list passed is reused — copy it to keep. */
inline fun <T> forEachCombination(items: List<T>, k: Int, action: (List<T>) -> Unit) {
    val n = items.size
    if (k > n) return
    val idx = IntArray(k) { it }
    val buffer = ArrayList<T>(k).apply { repeat(k) { i -> add(items[i]) } }
    while (true) {
        for (i in 0 until k) buffer[i] = items[idx[i]]
        action(buffer)
        var i = k - 1
        while (i >= 0 && idx[i] == n - k + i) i--
        if (i < 0) return
        idx[i]++
        for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
    }
}
