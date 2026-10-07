package app.mast.poker.practice

import app.mast.poker.content.Question
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.progress.AnswerExtras

sealed interface Answer {
    /** Index into the question's options (or hands; for PickWinner `hands.size` means split). */
    data class Pick(val index: Int) : Answer
    data class Cards(val selected: Set<Card>) : Answer
    data class Order(val order: List<HandCategory>) : Answer
    /** Call / open-raise / shove = true, fold = false. */
    data class Decision(val yes: Boolean) : Answer
    /** Hands marked on the 13×13 grid. */
    data class Hands(val selected: Set<StartingHand>) : Answer
}

object Grader {

    fun isCorrect(q: Question, a: Answer): Boolean = when (q) {
        is Question.Choice -> a is Answer.Pick && a.index == q.correct
        is Question.NameHand -> a is Answer.Pick && q.options.getOrNull(a.index) == q.answer
        is Question.PickWinner -> a is Answer.Pick && if (q.winners.size > 1) a.index == q.hands.size else q.winners == listOf(a.index)
        is Question.PickBestFive -> a is Answer.Cards && q.isCorrect(a.selected)
        is Question.OrderHands -> a is Answer.Order && a.order == q.answer
        is Question.CountOuts -> a is Answer.Pick && q.options.getOrNull(a.index) == q.answer
        is Question.CallOrFold -> a is Answer.Decision && a.yes == q.shouldCall
        is Question.OpenOrFold -> a is Answer.Decision && a.yes == q.shouldOpen
        is Question.Favourite -> a is Answer.Pick && a.index == q.answer
        is Question.BuildRange -> a is Answer.Hands && q.isCorrect(a.selected)
        is Question.PushOrFold -> a is Answer.Decision && a.yes == q.shouldPush
    }

    /** The answer that would be correct — used to reveal it after a mistake. */
    fun correctAnswer(q: Question): Answer = when (q) {
        is Question.Choice -> Answer.Pick(q.correct)
        is Question.NameHand -> Answer.Pick(q.options.indexOf(q.answer))
        is Question.PickWinner -> Answer.Pick(if (q.winners.size > 1) q.hands.size else q.winners.first())
        is Question.PickBestFive -> Answer.Cards(HandEvaluator.evaluate(q.hole + q.board).cards.toSet())
        is Question.OrderHands -> Answer.Order(q.answer)
        is Question.CountOuts -> Answer.Pick(q.options.indexOf(q.answer))
        is Question.CallOrFold -> Answer.Decision(q.shouldCall)
        is Question.OpenOrFold -> Answer.Decision(q.shouldOpen)
        is Question.Favourite -> Answer.Pick(q.answer)
        is Question.BuildRange -> Answer.Hands(q.target)
        is Question.PushOrFold -> Answer.Decision(q.shouldPush)
    }

    /** Rare hands recognised correctly count towards hidden achievements. */
    fun extras(q: Question, correct: Boolean): AnswerExtras {
        if (!correct || q !is Question.NameHand) return AnswerExtras()
        val v = HandEvaluator.evaluate(q.hole + q.board)
        val wheel = (v.category == HandCategory.STRAIGHT || v.category == HandCategory.STRAIGHT_FLUSH) && v.tiebreak.first() == 5
        return AnswerExtras(royal = v.category == HandCategory.ROYAL_FLUSH, wheel = wheel)
    }
}
