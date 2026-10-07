package app.mast.poker.content

import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.cards
import kotlin.random.Random

@DslMarker
annotation class LessonDsl

@LessonDsl
class LessonBuilder(private val lessonId: String) {
    private val steps = mutableListOf<Step>()

    fun theory(title: String, body: String, visual: Visual? = null) {
        steps += Step.Theory(title, body.trimIndent(), visual)
    }

    fun remember(text: String, visual: Visual? = null) {
        steps += Step.Remember(text.trimIndent(), visual)
    }

    /** Multiple choice. [correct] is listed first here; options are shuffled with a stable seed. */
    fun choice(
        prompt: String,
        correct: String,
        vararg wrong: String,
        explanation: String,
        concept: Concept,
        visual: Visual? = null,
    ) {
        val options = (listOf(correct) + wrong).shuffled(Random(seed(prompt)))
        steps += Step.Ask(Question.Choice(prompt, options, options.indexOf(correct), explanation.trimIndent(), concept, visual))
    }

    fun nameHand(
        prompt: String,
        hole: String,
        board: String,
        options: List<HandCategory>,
        explanation: String,
        concept: Concept = Concept.HAND_RANKINGS,
    ) {
        val shuffled = options.shuffled(Random(seed(prompt + board)))
        steps += Step.Ask(Question.NameHand(prompt, cardsOrEmpty(hole), cards(board), shuffled, explanation.trimIndent(), concept))
    }

    fun winner(prompt: String, board: String, vararg hands: String, explanation: String, concept: Concept = Concept.SHOWDOWN) {
        steps += Step.Ask(Question.PickWinner(prompt, cards(board), hands.map(::cards), explanation.trimIndent(), concept))
    }

    fun bestFive(prompt: String, hole: String, board: String, explanation: String) {
        steps += Step.Ask(Question.PickBestFive(prompt, cards(hole), cards(board), explanation.trimIndent()))
    }

    fun order(prompt: String, categories: List<HandCategory>, explanation: String) {
        val shuffled = categories.shuffled(Random(seed(prompt + categories)))
            .let { if (it == it.sorted()) it.reversed() else it }
        steps += Step.Ask(Question.OrderHands(prompt, shuffled, explanation.trimIndent()))
    }

    fun outs(prompt: String, hole: String, board: String, target: HandCategory, options: List<Int>, explanation: String) {
        steps += Step.Ask(Question.CountOuts(prompt, cards(hole), cards(board), target, options.sorted(), explanation.trimIndent()))
    }

    fun callOrFold(prompt: String, pot: Int, call: Int, outs: Int, cardsToCome: Int, explanation: String) {
        steps += Step.Ask(Question.CallOrFold(prompt, pot, call, outs, cardsToCome, explanation.trimIndent()))
    }

    private fun seed(s: String) = (lessonId + s).hashCode()
    private fun cardsOrEmpty(spec: String) = if (spec.isBlank()) emptyList() else cards(spec)

    fun build(): List<Step> = steps.toList()
}

fun lesson(
    id: String,
    title: String,
    subtitle: String,
    minutes: Int,
    unlocksDrill: DrillType? = null,
    block: LessonBuilder.() -> Unit,
): Lesson = Lesson(id, title, subtitle, minutes, LessonBuilder(id).apply(block).build(), unlocksDrill)

fun hand(hole: String, board: String, showBest: Boolean = false, caption: String? = null) =
    Visual.Hand(if (hole.isBlank()) emptyList() else cards(hole), if (board.isBlank()) emptyList() else cards(board), showBest, caption)

fun cardRow(spec: String, highlight: String = "", caption: String? = null) =
    Visual.Cards(cards(spec), if (highlight.isBlank()) emptySet() else cards(highlight).toSet(), caption)

fun duel(board: String, vararg hands: String) = Visual.Duel(cards(board), hands.map(::cards))
