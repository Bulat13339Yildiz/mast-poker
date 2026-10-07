package app.mast.poker.core.poker

/** Two hole cards reduced to what matters preflop: ranks and whether they are suited. */
data class StartingHand(val high: Rank, val low: Rank, val suited: Boolean) {
    val isPair: Boolean get() = high == low

    /** Poker notation: "AKs", "T9o", "77". */
    val notation: String
        get() = if (isPair) "${high.code}${low.code}" else "${high.code}${low.code}${if (suited) 's' else 'o'}"

    val ruDescription: String
        get() = when {
            isPair -> "пара: ${high.ruPlural}"
            suited -> "${high.ruName} и ${low.ruName}, одной масти"
            else -> "${high.ruName} и ${low.ruName}, разных мастей"
        }

    companion object {
        fun of(a: Card, b: Card): StartingHand {
            val (hi, lo) = if (a.rank >= b.rank) a to b else b to a
            return StartingHand(hi.rank, lo.rank, hi.suit == lo.suit && hi.rank != lo.rank)
        }
    }
}

enum class SeatGroup(val ruName: String, val fromPhrase: String) {
    EARLY("Ранняя позиция", "с любой позиции"),
    MIDDLE("Средняя позиция", "со средней позиции и позже"),
    LATE("Поздняя позиция", "с поздней позиции — катоффа или баттона"),
}

/**
 * A deliberately simple, tight opening chart for a beginner at a 6-max table:
 * which hands to open-raise when everyone before you folded.
 */
object PreflopChart {

    /** All 169 starting hands. */
    val allHands: List<StartingHand> = Rank.entries.flatMap { hi ->
        Rank.entries.filter { it <= hi }.flatMap { lo ->
            if (hi == lo) listOf(StartingHand(hi, lo, false)) else listOf(StartingHand(hi, lo, true), StartingHand(hi, lo, false))
        }
    }

    /** Hands that become playable exactly at [seat] (not playable from an earlier seat group). */
    fun addedAt(seat: SeatGroup): List<StartingHand> = allHands.filter { h ->
        shouldOpen(h, seat) && (seat.ordinal == 0 || !shouldOpen(h, SeatGroup.entries[seat.ordinal - 1]))
    }

    /**
     * Compact range text built from the chart itself, e.g.
     * "пары AA–77; одномастные AKs–ATs, KQs; разномастные AKo–AQo".
     */
    fun describe(hands: Collection<StartingHand>): String {
        val parts = mutableListOf<String>()
        val pairs = hands.filter { it.isPair }.map { it.high }.sortedDescending()
        if (pairs.isNotEmpty()) parts += "пары " + runs(pairs) { r -> "${r.code}${r.code}" }.joinToString(", ")
        for ((suited, label) in listOf(true to "одномастные", false to "разномастные")) {
            val group = hands.filter { !it.isPair && it.suited == suited }
            if (group.isEmpty()) continue
            val bits = group.groupBy { it.high }.toSortedMap(compareByDescending { it }).flatMap { (high, hs) ->
                runs(hs.map { it.low }.sortedDescending()) { lo -> "${high.code}${lo.code}${if (suited) 's' else 'o'}" }
            }
            parts += "$label " + bits.joinToString(", ")
        }
        return parts.joinToString("; ")
    }

    /** Collapses descending ranks into runs: [K, Q, J, 9] → "AKs–AJs", "A9s". */
    private fun runs(desc: List<Rank>, name: (Rank) -> String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < desc.size) {
            var j = i
            while (j + 1 < desc.size && desc[j + 1].value == desc[j].value - 1) j++
            out += if (i == j) name(desc[i]) else "${name(desc[i])}–${name(desc[j])}"
            i = j + 1
        }
        return out
    }

    fun shouldOpen(hand: StartingHand, seat: SeatGroup): Boolean = when (seat) {
        SeatGroup.EARLY -> early(hand)
        SeatGroup.MIDDLE -> early(hand) || middle(hand)
        SeatGroup.LATE -> early(hand) || middle(hand) || late(hand)
    }

    private fun StartingHand.v(r: Rank) = r.value

    private fun early(h: StartingHand): Boolean = with(h) {
        when {
            isPair -> v(high) >= 7
            suited -> (high == Rank.ACE && v(low) >= 10) || (high == Rank.KING && low == Rank.QUEEN)
            else -> high == Rank.ACE && v(low) >= 12
        }
    }

    private fun middle(h: StartingHand): Boolean = with(h) {
        when {
            isPair -> v(high) >= 5
            suited -> (high == Rank.ACE && v(low) >= 9) ||
                (high == Rank.KING && v(low) >= 10) ||
                (high == Rank.QUEEN && v(low) >= 10) ||
                (high == Rank.JACK && low == Rank.TEN)
            else -> (high == Rank.ACE && v(low) >= 11) || (high == Rank.KING && low == Rank.QUEEN)
        }
    }

    private fun late(h: StartingHand): Boolean = with(h) {
        when {
            isPair -> true
            suited -> high == Rank.ACE ||
                (high == Rank.KING && v(low) >= 9) ||
                (high == Rank.QUEEN && v(low) >= 9) ||
                (high == Rank.JACK && v(low) >= 9) ||
                (v(high) - v(low) == 1 && v(low) >= 5)
            else -> (high == Rank.ACE && v(low) >= 9) ||
                (high == Rank.KING && v(low) >= 10) ||
                (high == Rank.QUEEN && v(low) >= 10) ||
                (high == Rank.JACK && low == Rank.TEN)
        }
    }
}
