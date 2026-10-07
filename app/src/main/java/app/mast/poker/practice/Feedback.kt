package app.mast.poker.practice

import app.mast.poker.content.Question
import kotlin.math.abs

/**
 * What the player sees after answering: a headline, the explanation of the right
 * answer and — when wrong — why the chosen answer does not work.
 */
data class Feedback(
    val correct: Boolean,
    val headline: String,
    val explanation: String,
    val whyNot: String? = null,
)

object FeedbackBuilder {

    private val goodHeadlines = listOf("Верно!", "В масть!", "Точно!", "Именно так!")
    private val badHeadlines = listOf("Не совсем", "Почти", "Разберём")

    fun build(q: Question, a: Answer): Feedback {
        val correct = Grader.isCorrect(q, a)
        val pool = if (correct) goodHeadlines else badHeadlines
        val headline = pool[abs(q.prompt.hashCode() + q.explanation.length) % pool.size]
        // Generated questions already carry the engine's text; don't repeat it.
        val extra = if (correct) null else whyNot(q, a)?.takeUnless { q.explanation.contains(it) }
        return Feedback(correct, headline, q.explanation, extra)
    }

    private fun whyNot(q: Question, a: Answer): String? = when (q) {
        is Question.NameHand -> (a as? Answer.Pick)?.let { q.options.getOrNull(it.index) }
            ?.let { Explainer.whyNot(it, q.hole, q.board) }?.ifBlank { null }
            ?.let { why ->
                // Author-written lessons get the engine's anatomy too; generated ones already show it.
                val anatomy = Explainer.anatomy(q.hole, q.board)
                if (q.explanation.contains(anatomy.lineSequence().first())) why else "$why\n$anatomy"
            }
        is Question.PickWinner -> Explainer.winner(q.board, q.hands, listOf("Игрок 1", "Игрок 2"))
        is Question.PickBestFive -> (a as? Answer.Cards)?.let { Explainer.bestFive(q.hole, q.board, it.selected) }
        is Question.OrderHands -> "Правильный порядок: " + q.answer.joinToString(" → ") { it.ruName } + "."
        is Question.CountOuts -> Explainer.outs(q.hole, q.board, q.target)
        is Question.CallOrFold -> Explainer.potOdds(q.pot, q.call, q.outs, q.cardsToCome)
        is Question.Favourite, is Question.OpenOrFold, is Question.Choice -> null
    }
}
