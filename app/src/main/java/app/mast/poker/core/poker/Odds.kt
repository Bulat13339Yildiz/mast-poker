package app.mast.poker.core.poker

import kotlin.random.Random

object Outs {
    /**
     * Unseen cards that, dealt as the next card, give a hand of at least [target]
     * when the current hand is weaker than [target].
     */
    fun toCategory(hole: List<Card>, board: List<Card>, target: HandCategory): List<Card> {
        require(board.size in 3..4) { "Outs are counted on the flop or turn" }
        val known = hole + board
        if (HandEvaluator.evaluate(known).category >= target) return emptyList()
        return Deck.without(known).filter { HandEvaluator.evaluate(known + it).category >= target }
    }

    /** Unseen cards that make the hand strictly stronger in category. */
    fun improving(hole: List<Card>, board: List<Card>): List<Card> {
        require(board.size in 3..4)
        val known = hole + board
        val now = HandEvaluator.evaluate(known).category
        return Deck.without(known).filter { HandEvaluator.evaluate(known + it).category > now }
    }
}

object PotOdds {
    /** Share of the final pot the caller must invest: call / (pot + call). */
    fun requiredEquity(pot: Int, call: Int): Double = call.toDouble() / (pot + call)

    /** "Rule of 2 and 4": rough % to hit with [cardsToCome] cards left. */
    fun ruleOfTwoAndFour(outs: Int, cardsToCome: Int): Int = outs * if (cardsToCome >= 2) 4 else 2

    /** Exact chance to hit at least one of [outs] among [unseen] cards over [cardsToCome] draws. */
    fun exactHitChance(outs: Int, unseen: Int, cardsToCome: Int): Double {
        var missAll = 1.0
        for (i in 0 until cardsToCome) missAll *= (unseen - outs - i).toDouble() / (unseen - i)
        return 1.0 - missAll
    }

    fun isProfitableCall(pot: Int, call: Int, equity: Double): Boolean = equity >= requiredEquity(pot, call)
}

data class Equity(val win: Double, val tie: Double) {
    val lose: Double get() = 1.0 - win - tie
}

object EquityCalculator {
    /**
     * Heads-up equity of [hero] vs [villain]. Exact enumeration when two or fewer
     * board cards remain, Monte Carlo with [samples] runouts otherwise.
     */
    fun headsUp(
        hero: List<Card>,
        villain: List<Card>,
        board: List<Card> = emptyList(),
        samples: Int = 4000,
        random: Random = Random(7),
    ): Equity {
        val remaining = Deck.without(hero + villain + board).map(FastEval::index).toIntArray()
        val need = 5 - board.size
        val a = IntArray(7)
        val b = IntArray(7)
        a[0] = FastEval.index(hero[0]); a[1] = FastEval.index(hero[1])
        b[0] = FastEval.index(villain[0]); b[1] = FastEval.index(villain[1])
        board.forEachIndexed { i, c -> a[2 + i] = FastEval.index(c); b[2 + i] = a[2 + i] }
        var win = 0
        var tie = 0
        var total = 0
        fun score() {
            val c = FastEval.score(a, 7).compareTo(FastEval.score(b, 7))
            if (c > 0) win++ else if (c == 0) tie++
            total++
        }
        val first = 2 + board.size
        when {
            need == 0 -> score()
            need == 1 -> for (x in remaining) { a[first] = x; b[first] = x; score() }
            need == 2 -> for (i in remaining.indices) for (j in i + 1 until remaining.size) {
                a[first] = remaining[i]; b[first] = remaining[i]
                a[first + 1] = remaining[j]; b[first + 1] = remaining[j]
                score()
            }
            else -> {
                val pool = remaining.copyOf()
                repeat(samples) {
                    for (i in 0 until need) {
                        val j = i + random.nextInt(pool.size - i)
                        val t = pool[i]; pool[i] = pool[j]; pool[j] = t
                        a[first + i] = pool[i]; b[first + i] = pool[i]
                    }
                    score()
                }
            }
        }
        return Equity(win.toDouble() / total, tie.toDouble() / total)
    }
}
