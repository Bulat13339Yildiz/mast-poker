package app.mast.poker.practice

import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.RangeEquity
import app.mast.poker.core.poker.StartingHand
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Push charts from the same model as the push/fold drill. Equity against the callers'
 * range does not depend on the seat or stack, so it is simulated once per range and
 * every chart is then simple arithmetic.
 */
object PushCharts {

    data class Chart(val push: Set<StartingHand>, val combos: Int) {
        val percent: Double get() = combos.toDouble() / HandClasses.TOTAL_COMBOS
    }

    private val equities = ConcurrentHashMap<Int, Map<StartingHand, Double>>()

    /** Equity of every hand class (one representative combo) against the top [callPercent]%. */
    fun equities(callPercent: Int, samples: Int = 1500): Map<StartingHand, Double> = equities.getOrPut(callPercent) {
        val range = HandRange.top(callPercent / 100.0)
        val random = Random(callPercent)
        HandClasses.all.associateWith { h ->
            RangeEquity.vsRange(HandClasses.combosOf(h).first(), range, emptyList(), samples, Random(random.nextInt()))
        }
    }

    fun chart(seat: PushSeat, stackBb: Double, callPercent: Int, eq: Map<StartingHand, Double>): Chart {
        val range = HandRange.top(callPercent / 100.0)
        val push = HandClasses.all.filter { h ->
            val hole = HandClasses.combosOf(h).first()
            val c = range.combos(hole).size / 1225.0
            val e = eq.getValue(h)
            val steal = seat.behind.sum()
            var folding = 1.0
            var ev = 0.0
            for (blind in seat.behind) {
                val chance = folding * c
                folding *= 1 - c
                ev += chance * (e * (2 * stackBb + steal - blind) - stackBb)
            }
            ev += folding * steal
            ev > -seat.heroBlind
        }.toSet()
        return Chart(push, push.sumOf { HandClasses.combos(it) })
    }
}
