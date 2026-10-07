package app.mast.poker.ui.quiz

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mast.poker.content.Question
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.practice.Answer
import app.mast.poker.practice.Feedback
import app.mast.poker.practice.FeedbackBuilder

/** UI state of one question: the answer being built and, once checked, the feedback. */
@Stable
class QuestionState(val question: Question) {
    var answer by mutableStateOf<Answer?>(null)
    var feedback by mutableStateOf<Feedback?>(null)
        private set

    /** Partial order for [Question.OrderHands]; becomes the answer when complete. */
    val partialOrder = mutableStateListOf<HandCategory>()

    /** Cards tapped so far for [Question.PickBestFive]; becomes the answer at five. */
    val selection = mutableStateListOf<Card>()

    fun toggle(card: Card) {
        if (checked) return
        if (card in selection) selection.remove(card) else if (selection.size < 5) selection += card
        answer = if (selection.size == 5) Answer.Cards(selection.toSet()) else null
    }

    /** Hands painted so far for [Question.BuildRange]. */
    var rangeSelection by mutableStateOf<Set<StartingHand>>(emptySet())
        private set

    fun setRange(hands: Set<StartingHand>) {
        if (checked) return
        rangeSelection = hands
        answer = if (hands.isEmpty()) null else Answer.Hands(hands)
    }

    val checked: Boolean get() = feedback != null
    val canCheck: Boolean get() = answer != null && !checked

    fun check(): Feedback = FeedbackBuilder.build(question, answer!!).also { feedback = it }
}
