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
        val remaining = Deck.without(hero + villain + board)
        val need = 5 - board.size
        var win = 0
        var tie = 0
        var total = 0
        fun score(runout: List<Card>) {
            val full = board + runout
            val h = HandEvaluator.evaluate(hero + full)
            val v = HandEvaluator.evaluate(villain + full)
            val c = h.compareTo(v)
            if (c > 0) win++ else if (c == 0) tie++
            total++
        }
        if (need <= 2) {
            if (need == 0) score(emptyList()) else forEachCombination(remaining, need) { score(it) }
        } else {
            val pool = remaining.toMutableList()
            repeat(samples) {
                for (i in 0 until need) {
                    val j = i + random.nextInt(pool.size - i)
                    val t = pool[i]; pool[i] = pool[j]; pool[j] = t
                }
                score(pool.subList(0, need))
            }
        }
        return Equity(win.toDouble() / total, tie.toDouble() / total)
    }
}
