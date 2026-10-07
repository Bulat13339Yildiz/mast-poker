package app.mast.poker.content

import app.mast.poker.core.poker.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentIntegrityTest {

    private val lessons = Curriculum.lessons
    private fun questions() = lessons.flatMap { l -> l.steps.filterIsInstance<Step.Ask>().map { l.id to it.question } }

    private fun assertDistinct(where: String, cards: List<Card>) =
        assertEquals("duplicate cards in $where: $cards", cards.size, cards.toSet().size)

    @Test
    fun `ids are unique`() {
        assertEquals(lessons.size, lessons.map { it.id }.toSet().size)
        assertEquals(Curriculum.chapters.size, Curriculum.chapters.map { it.id }.toSet().size)
        assertEquals((1..Curriculum.chapters.size).toList(), Curriculum.chapters.map { it.number })
    }

    @Test
    fun `every lesson teaches and then asks`() {
        lessons.forEach { l ->
            assertTrue("${l.id} has no theory", l.steps.first() is Step.Theory)
            assertTrue("${l.id} has no questions", l.questionCount >= 2)
            assertTrue("${l.id} minutes", l.minutes in 1..10)
        }
    }

    @Test
    fun `each drill is unlocked by at most one lesson`() {
        DrillType.entries.forEach { type ->
            assertTrue("$type unlocked twice", lessons.count { it.unlocksDrill == type } <= 1)
        }
    }

    @Test
    fun `questions are well formed`() {
        questions().forEach { (lessonId, q) ->
            val where = "$lessonId: ${q.prompt}"
            assertTrue("empty explanation in $where", q.explanation.isNotBlank())
            when (q) {
                is Question.Choice -> {
                    assertTrue(where, q.options.size >= 2)
                    assertEquals(where, q.options.size, q.options.toSet().size)
                    assertTrue(where, q.correct in q.options.indices)
                }
                is Question.NameHand -> {
                    assertTrue(where, (q.hole + q.board).size in 5..7)
                    assertDistinct(where, q.hole + q.board)
                    assertTrue("answer ${q.answer} missing in $where", q.answer in q.options)
                    assertEquals(where, q.options.size, q.options.toSet().size)
                }
                is Question.PickWinner -> {
                    assertEquals(where, 5, q.board.size)
                    assertDistinct(where, q.board + q.hands.flatten())
                    assertTrue(where, q.hands.all { it.size == 2 })
                }
                is Question.PickBestFive -> {
                    assertEquals(where, 7, (q.hole + q.board).size)
                    assertDistinct(where, q.hole + q.board)
                }
                is Question.OrderHands -> {
                    assertEquals(where, q.categories.size, q.categories.toSet().size)
                    assertTrue(where, q.categories != q.answer)
                }
                is Question.CountOuts -> {
                    assertDistinct(where, q.hole + q.board)
                    assertTrue("answer ${q.answer} missing in $where", q.answer in q.options)
                    assertTrue(where, q.answer > 0)
                }
                is Question.CallOrFold -> {
                    assertTrue(where, q.pot > 0 && q.call > 0 && q.outs > 0 && q.cardsToCome in 1..2)
                }
                is Question.OpenOrFold -> assertDistinct(where, q.hole)
                is Question.Favourite -> assertDistinct(where, q.hands.flatten() + q.board)
            }
        }
    }

    @Test
    fun `visuals use real cards`() {
        lessons.flatMap { l -> l.steps.map { l.id to it } }.forEach { (id, step) ->
            val visual = when (step) {
                is Step.Theory -> step.visual
                is Step.Remember -> step.visual
                is Step.Ask -> (step.question as? Question.Choice)?.visual
            }
            when (visual) {
                is Visual.Cards -> {
                    assertDistinct(id, visual.cards)
                    assertTrue(id, visual.cards.containsAll(visual.highlight))
                }
                is Visual.Hand -> {
                    assertDistinct(id, visual.hole + visual.board)
                    assertTrue(id, visual.board.size <= 5)
                }
                is Visual.Duel -> assertDistinct(id, visual.board + visual.hands.flatten())
                else -> Unit
            }
        }
    }

    @Test
    fun `stated explanation matches the engine for winner questions`() {
        // Split questions must say so, single-winner questions must not claim a split.
        questions().map { it.second }.filterIsInstance<Question.PickWinner>().forEach { q ->
            val saysSplit = q.explanation.contains("делится") || q.explanation.contains("ничья", ignoreCase = true)
            assertEquals("${q.prompt}: ${q.explanation}", q.winners.size > 1, saysSplit)
        }
    }
}
