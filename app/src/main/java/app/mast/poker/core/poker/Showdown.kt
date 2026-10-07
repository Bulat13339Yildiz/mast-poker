package app.mast.poker.core.poker

data class ShowdownResult(
    val values: List<HandValue>,
    val winners: List<Int>,
) {
    val isSplit: Boolean get() = winners.size > 1
}

object Showdown {
    fun resolve(board: List<Card>, holes: List<List<Card>>): ShowdownResult {
        require(board.size == 5) { "Showdown needs a full board" }
        val values = holes.map { HandEvaluator.evaluate(it + board) }
        val best = values.max()
        val winners = values.indices.filter { values[it].sameStrength(best) }
        return ShowdownResult(values, winners)
    }
}
