package app.mast.poker.practice.sim

import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.HandDescriber
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.Outs
import app.mast.poker.core.poker.Rank

enum class Strength(val ruName: String) {
    STRONG("сильная рука"),
    MEDIUM("средняя рука"),
    DRAW("дро"),
    WEAK("слабая рука"),
}

/**
 * How a beginner should see their hand after the flop. [monster] marks hands worth
 * raising (two pair with both hole cards, sets, straights and better). [outs] is the
 * number of cards that complete a draw, 0 for made hands.
 */
data class HandRead(val strength: Strength, val label: String, val monster: Boolean = false, val outs: Int = 0)

object HandReader {

    fun read(hole: List<Card>, board: List<Card>): HandRead {
        require(board.size in 3..5)
        val all = hole + board
        val v = HandEvaluator.evaluate(all)
        val boardRanks = board.map { it.rank }
        val top = boardRanks.max()
        val pocketPair = hole[0].rank == hole[1].rank
        val count = all.groupingBy { it.rank }.eachCount()

        // Straights and better that actually use a hole card and beat the board alone.
        if (v.category >= HandCategory.STRAIGHT && v.cards.any { it in hole } && v > boardOnly(board)) {
            return HandRead(Strength.STRONG, HandDescriber.describe(v), monster = true)
        }
        // Sets and trips.
        hole.map { it.rank }.distinct().firstOrNull { (count[it] ?: 0) >= 3 }?.let { r ->
            return HandRead(Strength.STRONG, if (pocketPair) "сет ${r.ruGenPlural}" else "трипс ${r.ruGenPlural}", monster = true)
        }
        // Two pair made with both hole cards.
        if (!pocketPair && hole.all { h -> h.rank in boardRanks }) {
            val (a, b) = hole.map { it.rank }.sortedDescending()
            return HandRead(Strength.STRONG, "две пары: ${a.ruPlural} и ${b.ruPlural}", monster = true)
        }
        if (pocketPair && hole[0].rank > top) {
            return HandRead(Strength.STRONG, "оверпара — ${hole[0].rank.ruPlural} старше всех карт стола")
        }
        val draw = if (board.size < 5) drawOf(hole, board) else null

        val paired = hole.firstOrNull { it.rank in boardRanks }
        if (paired != null && !pocketPair) {
            val kicker = hole.first { it != paired }.rank
            val withDraw = draw?.let { " + ${it.label}" }.orEmpty()
            return when {
                paired.rank == top && kicker >= Rank.TEN ->
                    HandRead(Strength.STRONG, "топ-пара (${paired.rank.ruPlural}) с хорошим кикером — ${kicker.ruName}$withDraw")
                paired.rank == top ->
                    HandRead(Strength.MEDIUM, "топ-пара (${paired.rank.ruPlural}) со слабым кикером — ${kicker.ruName}$withDraw")
                else -> HandRead(Strength.MEDIUM, "средняя пара — ${paired.rank.ruPlural}$withDraw")
            }
        }
        if (pocketPair) {
            return HandRead(Strength.MEDIUM, "карманная пара ${hole[0].rank.ruGenPlural} ниже старшей карты стола")
        }
        if (draw != null) return draw
        val overs = hole.all { it.rank > top }
        return HandRead(Strength.WEAK, if (overs) "две старшие карты без пары" else "ничего — ни пары, ни сильного дро")
    }

    private fun drawOf(hole: List<Card>, board: List<Card>): HandRead? {
        val all = hole + board
        val flushSuit = all.groupingBy { it.suit }.eachCount().entries.firstOrNull { it.value == 4 }?.key
        val flushDraw = flushSuit != null && hole.any { it.suit == flushSuit }
        val straightOuts = Outs.toCategory(hole, board, HandCategory.STRAIGHT)
            .filter { flushSuit == null || it.suit != flushSuit }
        val straightRanks = straightOuts.map { it.rank }.distinct().size
        val straightDraw = straightRanks >= 2
        val outs = Outs.toCategory(hole, board, HandCategory.STRAIGHT).size
        return when {
            flushDraw && straightDraw -> HandRead(Strength.DRAW, "флеш-дро и стрит-дро", outs = outs)
            flushDraw -> HandRead(Strength.DRAW, "флеш-дро", outs = outs)
            straightDraw -> HandRead(Strength.DRAW, "стрит-дро", outs = outs)
            else -> null
        }
    }

    /** The best "hand" the board makes on its own (pairs/trips/quads for 3–4 cards). */
    private fun boardOnly(board: List<Card>): app.mast.poker.core.poker.HandValue {
        if (board.size == 5) return HandEvaluator.evaluate5(board)
        val counts = board.groupingBy { it.rank }.eachCount().values.sortedDescending()
        val category = when {
            counts.first() == 4 -> HandCategory.FOUR_OF_A_KIND
            counts.first() == 3 -> HandCategory.THREE_OF_A_KIND
            counts.count { it == 2 } == 2 -> HandCategory.TWO_PAIR
            counts.first() == 2 -> HandCategory.PAIR
            else -> HandCategory.HIGH_CARD
        }
        return app.mast.poker.core.poker.HandValue(category, listOf(0, 0, 0, 0, 0), board)
    }

    /**
     * Wet = many draws possible: three of a suit, three ranks within a straight's
     * reach, or two of a suit with two close ranks. Rainbow, scattered or paired = dry.
     */
    fun isWet(board: List<Card>): Boolean {
        val suitMax = board.groupingBy { it.suit }.eachCount().values.max()
        val values = board.map { it.rank.value }.toMutableSet()
        if (14 in values) values += 1
        val ranks = values.sorted()
        val threeConnected = ranks.windowed(3).any { it.last() - it.first() <= 4 }
        val closePair = ranks.windowed(2).any { it.last() - it.first() <= 2 }
        return suitMax >= 3 || threeConnected || (suitMax == 2 && closePair)
    }
}
