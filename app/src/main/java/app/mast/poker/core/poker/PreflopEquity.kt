package app.mast.poker.core.poker

/**
 * All-in preflop equity of each of the 169 hand classes against one random hand.
 * Values come from [PreflopEquityData], computed by the simulation in
 * PreflopEquityGeneratorTest (run with MAST_GENERATE=1 to rebuild).
 */
object PreflopEquity {
    val vsRandom: Map<StartingHand, Double> by lazy {
        PreflopEquityData.TABLE.split(',').associate { entry ->
            val (notation, value) = entry.split(':')
            HandClasses.parse(notation) to value.toDouble()
        }
    }

    /** Hand classes from strongest to weakest by [vsRandom]. */
    val ranked: List<StartingHand> by lazy {
        vsRandom.entries.sortedWith(compareByDescending<Map.Entry<StartingHand, Double>> { it.value }.thenBy { it.key.notation }).map { it.key }
    }

    fun of(h: StartingHand): Double = vsRandom.getValue(h)

    /** Position of [h] in the ranking as a percentage of all combos (top 1% … 100%). */
    fun percentile(h: StartingHand): Double {
        var combos = 0
        for (x in ranked) {
            combos += HandClasses.combos(x)
            if (x == h) break
        }
        return combos.toDouble() / HandClasses.TOTAL_COMBOS
    }
}
