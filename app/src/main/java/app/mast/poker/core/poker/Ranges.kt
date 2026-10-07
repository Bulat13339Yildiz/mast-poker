package app.mast.poker.core.poker

/** The 169 starting-hand classes and their concrete two-card combos. */
object HandClasses {
    val all: List<StartingHand> get() = PreflopChart.allHands

    const val TOTAL_COMBOS = 1326

    fun combos(h: StartingHand): Int = when {
        h.isPair -> 6
        h.suited -> 4
        else -> 12
    }

    /** Concrete combos of [h] that do not use any [dead] card. */
    fun combosOf(h: StartingHand, dead: Collection<Card> = emptySet()): List<List<Card>> {
        val d = dead.toHashSet()
        val out = mutableListOf<List<Card>>()
        if (h.isPair) {
            val cs = Suit.entries.map { Card(h.high, it) }.filter { it !in d }
            for (i in cs.indices) for (j in i + 1 until cs.size) out += listOf(cs[i], cs[j])
        } else {
            for (s1 in Suit.entries) for (s2 in Suit.entries) {
                if ((s1 == s2) != h.suited) continue
                val a = Card(h.high, s1)
                val b = Card(h.low, s2)
                if (a !in d && b !in d) out += listOf(a, b)
            }
        }
        return out
    }

    /** "AKs", "T9o", "77". A two-letter non-pair ("AK") is ambiguous and rejected here. */
    fun parse(notation: String): StartingHand {
        val t = notation.trim()
        val hi = rank(t[0])
        val lo = rank(t[1])
        require(hi >= lo) { "Write the higher rank first: $notation" }
        if (hi == lo) return StartingHand(hi, lo, false)
        val suffix = t.getOrNull(2)?.lowercaseChar()
        require(suffix == 's' || suffix == 'o') { "Missing s/o in $notation" }
        return StartingHand(hi, lo, suffix == 's')
    }

    internal fun rank(c: Char): Rank = Rank.entries.firstOrNull { it.code == c.uppercaseChar() } ?: error("Bad rank '$c'")
}

/** A set of starting-hand classes, e.g. a calling range or "top 15%". */
data class HandRange(val hands: Set<StartingHand>) {
    val comboCount: Int get() = hands.sumOf { HandClasses.combos(it) }
    val percent: Double get() = comboCount.toDouble() / HandClasses.TOTAL_COMBOS

    operator fun contains(h: StartingHand): Boolean = h in hands

    fun combos(dead: Collection<Card> = emptySet()): List<List<Card>> = hands.flatMap { HandClasses.combosOf(it, dead) }

    fun describe(): String = PreflopChart.describe(hands)

    companion object {
        /**
         * The strongest hands by equity against a random hand, adding classes until
         * the range covers at least [fraction] of all 1326 combos.
         */
        fun top(fraction: Double): HandRange {
            val target = fraction * HandClasses.TOTAL_COMBOS
            val picked = mutableSetOf<StartingHand>()
            var combos = 0
            for (h in PreflopEquity.ranked) {
                if (combos >= target - 1e-9) break
                picked += h
                combos += HandClasses.combos(h)
            }
            return HandRange(picked)
        }

        /**
         * Parses "QQ+, AKs, AKo, 22-55, ATs+, A2s-A5s, KQ". A plain "KQ" means both
         * suited and offsuit.
         */
        fun parse(spec: String): HandRange {
            val out = mutableSetOf<StartingHand>()
            spec.split(',').map { it.trim().replace('–', '-') }.filter { it.isNotEmpty() }.forEach { token ->
                when {
                    token.contains('-') -> {
                        val (a, b) = token.split('-').map { it.trim() }
                        val from = expandBase(a)
                        val to = expandBase(b)
                        require(from.size == to.size) { "Mismatched range $token" }
                        from.zip(to).forEach { (x, y) -> out += between(x, y) }
                    }
                    token.endsWith("+") -> expandBase(token.dropLast(1)).forEach { out += plus(it) }
                    else -> out += expandBase(token)
                }
            }
            return HandRange(out)
        }

        private fun expandBase(t: String): List<StartingHand> {
            val hi = HandClasses.rank(t[0])
            val lo = HandClasses.rank(t[1])
            return when {
                hi == lo -> listOf(StartingHand(hi, lo, false))
                t.length == 2 -> listOf(StartingHand(hi, lo, true), StartingHand(hi, lo, false))
                else -> listOf(HandClasses.parse(t))
            }
        }

        /** "QQ+" → QQ, KK, AA; "ATs+" → ATs…AKs (kicker climbs up to just below the high card). */
        private fun plus(h: StartingHand): List<StartingHand> =
            if (h.isPair) Rank.entries.filter { it >= h.high }.map { StartingHand(it, it, false) }
            else Rank.entries.filter { it >= h.low && it < h.high }.map { StartingHand(h.high, it, h.suited) }

        private fun between(a: StartingHand, b: StartingHand): List<StartingHand> {
            if (a.isPair) {
                val (lo, hi) = listOf(a.high, b.high).sorted()
                return Rank.entries.filter { it in lo..hi }.map { StartingHand(it, it, false) }
            }
            require(a.high == b.high && a.suited == b.suited) { "Range must keep the top card: ${a.notation}-${b.notation}" }
            val (lo, hi) = listOf(a.low, b.low).sorted()
            return Rank.entries.filter { it in lo..hi }.map { StartingHand(a.high, it, a.suited) }
        }
    }
}

/** Combinatorics questions: how many ways can a hand be held, given cards we can see. */
object Combos {
    fun of(h: StartingHand, dead: Collection<Card> = emptySet()): Int = HandClasses.combosOf(h, dead).size

    /**
     * Pocket pairs that make a set on [board]. Only ranks that appear once count —
     * a pocket pair to a paired board rank would be quads, not a set.
     */
    fun sets(board: List<Card>, dead: Collection<Card> = emptySet()): Int {
        val seen = (board + dead).toSet()
        return board.groupingBy { it.rank }.eachCount().filterValues { it == 1 }.keys.sumOf { r ->
            val left = Suit.entries.count { Card(r, it) !in seen }
            left * (left - 1) / 2
        }
    }
}
