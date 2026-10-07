package app.mast.poker.practice

import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import app.mast.poker.content.Question
import app.mast.poker.content.Step
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GraderTest {

    private val allLessonQuestions = Curriculum.lessons.flatMap { l -> l.steps.filterIsInstance<Step.Ask>().map { it.question } }

    @Test
    fun `the revealed answer is always graded correct`() {
        allLessonQuestions.forEach { q -> assertTrue(q.prompt, Grader.isCorrect(q, Grader.correctAnswer(q))) }
    }

    @Test
    fun `generated exercises are gradeable for every drill`() {
        val ex = Exercises(DrillGenerator(Random(3)), Random(3))
        DrillType.entries.filter { it != DrillType.HAND_SIM }.forEach { type ->
            repeat(25) {
                val q = ex.next(type)
                assertTrue("$type ${q.prompt}", Grader.isCorrect(q, Grader.correctAnswer(q)))
                assertTrue(q.explanation.isNotBlank())
            }
        }
    }

    @Test
    fun `wrong answers are rejected`() {
        allLessonQuestions.filterIsInstance<Question.Choice>().forEach { q ->
            val wrong = (q.options.indices - q.correct).first()
            assertFalse(Grader.isCorrect(q, Answer.Pick(wrong)))
        }
        allLessonQuestions.filterIsInstance<Question.CallOrFold>().forEach { q ->
            assertFalse(Grader.isCorrect(q, Answer.Decision(!q.shouldCall)))
        }
    }

    @Test
    fun `split must be answered as split`() {
        val q = allLessonQuestions.filterIsInstance<Question.PickWinner>().first { it.winners.size > 1 }
        assertFalse(Grader.isCorrect(q, Answer.Pick(0)))
        assertTrue(Grader.isCorrect(q, Answer.Pick(q.hands.size)))
    }

    @Test
    fun `review pulls questions for any concept`() {
        val ex = Exercises(DrillGenerator(Random(5)), Random(5))
        app.mast.poker.content.Concept.entries.forEach { c ->
            val qs = ex.forReview(c)
            assertTrue("$c", qs.isNotEmpty())
        }
    }
}
