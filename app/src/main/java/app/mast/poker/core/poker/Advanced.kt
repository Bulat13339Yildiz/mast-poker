package app.mast.poker.core.poker

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/** Bluffing and defending maths, all with the pot measured before the bet. */
object BluffMath {
    /** How often a bluff must work to break even: bet ÷ (pot + bet). */
    fun breakEvenFold(pot: Int, bet: Int): Double = bet.toDouble() / (pot + bet)

    /** Minimum defence frequency: share of hands to continue with so a pure bluff cannot profit. */
    fun mdf(pot: Int, bet: Int): Double = pot.toDouble() / (pot + bet)

    /** EV of a pure bluff (no equity when called) that works [foldFreq] of the time. */
    fun bluffEv(pot: Int, bet: Int, foldFreq: Double): Double = foldFreq * pot - (1 - foldFreq) * bet
}

/** Expected value of a call when the draw is the only way to win. */
object CallEv {
    /** EV in chips of calling [call] into [pot] (pot already includes the bet) with [equity]. */
    fun of(pot: Int, call: Int, equity: Double): Double = equity * (pot + call) - call
}

/** Equity of a hand against a range of hands. */
object RangeEquity {
    /**
     * Equity of [hero] against [villain] on [board] (0..5 cards): a random villain combo
     * that does not collide with known cards, then a random runout.
     */
    fun vsRange(hero: List<Card>, villain: HandRange, board: List<Card> = emptyList(), samples: Int = 3000, random: Random = Random(11)): Double {
        val dead = hero + board
        val combos = villain.combos(dead).map { c -> IntArray(2) { FastEval.index(c[it]) } }
        require(combos.isNotEmpty()) { "Range is empty after removing known cards" }
        val heroIdx = IntArray(2) { FastEval.index(hero[it]) }
        val boardIdx = IntArray(board.size) { FastEval.index(board[it]) }
        val need = 5 - board.size
        val a = IntArray(7)
        val b = IntArray(7)
        val pool = IntArray(52)
        var score = 0.0
        repeat(samples) {
            val v = combos[random.nextInt(combos.size)]
            var n = 0
            for (c in 0 until 52) {
                if (c == heroIdx[0] || c == heroIdx[1] || c == v[0] || c == v[1]) continue
                if (boardIdx.contains(c)) continue
                pool[n++] = c
            }
            for (i in 0 until need) {
                val j = i + random.nextInt(n - i)
                val t = pool[i]; pool[i] = pool[j]; pool[j] = t
            }
            for (i in 0 until 5) {
                val card = if (i < board.size) boardIdx[i] else pool[i - board.size]
                a[2 + i] = card
                b[2 + i] = card
            }
            a[0] = heroIdx[0]; a[1] = heroIdx[1]
            b[0] = v[0]; b[1] = v[1]
            val sa = FastEval.score(a, 7)
            val sb = FastEval.score(b, 7)
            score += if (sa > sb) 1.0 else if (sa == sb) 0.5 else 0.0
        }
        return score / samples
    }

    /** Equity against one random hand (exact table preflop, simulation otherwise). */
    fun vsRandom(hero: List<Card>, board: List<Card> = emptyList(), samples: Int = 3000, random: Random = Random(11)): Double =
        if (board.isEmpty()) PreflopEquity.of(StartingHand.of(hero[0], hero[1]))
        else vsRange(hero, HandRange(HandClasses.all.toSet()), board, samples, random)
}

/**
 * Short-stack push/fold in the small blind. Blinds 0.5/1 BB, effective stack [stackBb]
 * before posting. The big blind calls with [callRange]. Results are in big blinds,
 * relative to the start of the hand.
 */
object PushFold {
    data class Result(val callChance: Double, val equityWhenCalled: Double, val evPush: Double) {
        /** Folding the small blind loses the posted half blind. */
        val evFold: Double get() = -0.5
        val shouldPush: Boolean get() = evPush > evFold
        val margin: Double get() = abs(evPush - evFold)
    }

    fun evaluate(hero: List<Card>, stackBb: Double, callRange: HandRange, samples: Int = 4000, random: Random = Random(5)): Result {
        val calls = callRange.combos(hero).size
        val callChance = calls / 1225.0 // villain holds 2 of the 50 unseen cards: C(50, 2)
        val eq = if (calls == 0) 0.0 else RangeEquity.vsRange(hero, callRange, emptyList(), samples, random)
        // Fold: SB steals the big blind (+1). Call: pot 2S, SB invested S.
        val ev = (1 - callChance) * 1.0 + callChance * (2 * stackBb * eq - stackBb)
        return Result(callChance, eq, ev)
    }
}

/** Results of many hands: normal approximation with a win rate and standard deviation per 100 hands. */
object Variance {
    /** Standard normal CDF (Abramowitz–Stegun 7.1.26 via erf; error < 1.5e-7). */
    fun phi(x: Double): Double {
        val z = abs(x) / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * z)
        val poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
        val erf = 1.0 - poly * exp(-z * z)
        return if (x >= 0) 0.5 * (1.0 + erf) else 0.5 * (1.0 - erf)
    }

    fun expected(winRate: Double, hands: Int): Double = winRate * hands / 100.0

    fun stdDev(sd: Double, hands: Int): Double = sd * sqrt(hands / 100.0)

    /** Chance to be behind after [hands] despite a positive [winRate] (bb/100). */
    fun lossChance(winRate: Double, sd: Double, hands: Int): Double = phi(-expected(winRate, hands) / stdDev(sd, hands))

    /** [paths] random careers of [hands] hands, cumulative result per 100-hand block. */
    fun simulate(paths: Int, hands: Int, winRate: Double, sd: Double, random: Random): List<FloatArray> {
        val blocks = hands / 100
        return List(paths) {
            var total = 0.0
            FloatArray(blocks + 1) { i ->
                if (i > 0) total += winRate + sd * gaussian(random)
                total.toFloat()
            }
        }
    }

    private fun gaussian(random: Random): Double {
        var u: Double
        do u = random.nextDouble() while (u <= 1e-12)
        val v = random.nextDouble()
        return sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }
}

/**
 * Independent Chip Model (Malmuth–Harville): the chance to finish in each place is
 * proportional to the stack among the players still left. Returns each player's share
 * of [prizes] (same units as the prizes).
 */
object Icm {
    fun equities(stacks: List<Double>, prizes: List<Double>): List<Double> {
        val result = DoubleArray(stacks.size)
        fun place(left: List<Int>, rank: Int, chance: Double) {
            if (rank >= prizes.size || left.isEmpty()) return
            val total = left.sumOf { stacks[it] }
            for (i in left) {
                val p = chance * stacks[i] / total
                result[i] += p * prizes[rank]
                place(left - i, rank + 1, p)
            }
        }
        place(stacks.indices.toList(), 0, 1.0)
        return result.toList()
    }
}
