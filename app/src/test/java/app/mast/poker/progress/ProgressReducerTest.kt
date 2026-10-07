package app.mast.poker.progress

import app.mast.poker.content.Concept
import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressReducerTest {

    private fun at(day: Long, hour: Int = 12) = Moment(day, hour, day * 86_400_000L)

    @Test
    fun `levels follow the triangular curve`() {
        assertEquals(1, Levels.levelFor(0))
        assertEquals(1, Levels.levelFor(99))
        assertEquals(2, Levels.levelFor(100))
        assertEquals(3, Levels.levelFor(300))
        assertEquals(5, Levels.levelFor(1000))
        assertEquals(0.5f, Levels.fraction(200), 1e-6f)
        assertEquals("Новичок", Levels.titleFor(1))
        assertEquals("Акула", Levels.titleFor(11))
    }

    @Test
    fun `first lesson gives xp, streak and the first achievement`() {
        val first = Curriculum.lessons.first()
        val (p, reward) = ProgressReducer.completeLesson(UserProgress(), first.id, correct = 4, total = 4, now = at(100))
        assertEquals(20 + 4 * 5 + 10, reward.xp)
        assertEquals(1, p.dayStreak)
        assertTrue(reward.achievements.any { it.id == "first_lesson" })
        assertTrue(reward.achievements.any { it.id == "perfect_lesson" })
        assertEquals(1, p.stats.perfectLessons)
    }

    @Test
    fun `repeating a lesson gives a small fixed reward`() {
        val id = Curriculum.lessons.first().id
        val (p1, _) = ProgressReducer.completeLesson(UserProgress(), id, 2, 4, at(100))
        val (p2, r2) = ProgressReducer.completeLesson(p1, id, 4, 4, at(100))
        assertEquals(ProgressReducer.Xp.LESSON_REPEAT, r2.xp)
        assertEquals(4, p2.lessons.getValue(id).correct)
        assertEquals(0, p2.stats.perfectLessons)
    }

    @Test
    fun `day streak grows on consecutive days and resets after a gap`() {
        var p = UserProgress()
        p = ProgressReducer.finishDrill(p, DrillType.NAME_HAND, DrillMode.PRACTICE, 1, at(10)).first
        p = ProgressReducer.finishDrill(p, DrillType.NAME_HAND, DrillMode.PRACTICE, 1, at(10)).first
        assertEquals(1, p.dayStreak)
        p = ProgressReducer.finishDrill(p, DrillType.NAME_HAND, DrillMode.PRACTICE, 1, at(11)).first
        p = ProgressReducer.finishDrill(p, DrillType.NAME_HAND, DrillMode.PRACTICE, 1, at(12)).first
        assertEquals(3, p.dayStreak)
        val (gap, reward) = ProgressReducer.finishDrill(p, DrillType.NAME_HAND, DrillMode.PRACTICE, 1, at(20))
        assertEquals(1, gap.dayStreak)
        assertEquals(3, gap.bestDayStreak)
        assertTrue(reward.achievements.any { it.id == "comeback" })
    }

    @Test
    fun `visible streak survives until the end of the next day`() {
        val p = UserProgress(dayStreak = 5, lastActiveDay = 40)
        assertEquals(5, ProgressReducer.visibleStreak(p, 40))
        assertEquals(5, ProgressReducer.visibleStreak(p, 41))
        assertEquals(0, ProgressReducer.visibleStreak(p, 42))
    }

    @Test
    fun `daily goal is counted once per day`() {
        var p = UserProgress(dailyGoalXp = 10)
        val (p1, r1) = ProgressReducer.finishDrill(p, DrillType.NAME_HAND, DrillMode.PRACTICE, 6, at(5))
        assertTrue(r1.goalReached)
        val (p2, r2) = ProgressReducer.finishDrill(p1, DrillType.NAME_HAND, DrillMode.PRACTICE, 6, at(5))
        assertFalse(r2.goalReached)
        assertEquals(1, p2.goalsReached)
        p = p2
        assertEquals(24, p.xpOn(5))
    }

    @Test
    fun `answer streak, concept stats and mistakes`() {
        var p = UserProgress()
        repeat(10) { p = ProgressReducer.answer(p, Concept.OUTS, true, AnswerExtras(), at(1)).first }
        assertEquals(10, p.stats.bestAnswerStreak)
        val (wrong, reward) = ProgressReducer.answer(p, Concept.OUTS, false, AnswerExtras(), at(1))
        assertTrue(reward.achievements.isEmpty())
        assertEquals(0, wrong.stats.answerStreak)
        assertEquals(10, wrong.stats.bestAnswerStreak)
        assertEquals(ConceptStat(10, 11), wrong.stats.perConcept[Concept.OUTS])
        assertEquals(Mistake(Concept.OUTS, 0, 1), wrong.mistakes[Concept.OUTS])
        assertTrue("streak_10" in p.achievements)
    }

    @Test
    fun `spaced repetition walks 1-3-7 days and then clears`() {
        var p = UserProgress(mistakes = mapOf(Concept.OUTS to Mistake(Concept.OUTS, 0, 1)))
        val (first, firstReward) = ProgressReducer.finishReview(p, mapOf(Concept.OUTS to true), 3, at(1))
        // Today's queue is empty after the first good review — that already counts as "clean sheet".
        assertTrue(firstReward.achievements.map { it.id }.containsAll(listOf("review_first", "review_clear")))
        p = first
        assertEquals(Mistake(Concept.OUTS, 1, 4), p.mistakes[Concept.OUTS])
        p = ProgressReducer.finishReview(p, mapOf(Concept.OUTS to true), 3, at(4)).first
        assertEquals(Mistake(Concept.OUTS, 2, 11), p.mistakes[Concept.OUTS])
        val cleared = ProgressReducer.finishReview(p, mapOf(Concept.OUTS to true), 3, at(11)).first
        assertTrue(cleared.mistakes.isEmpty())
        assertEquals(3, cleared.stats.reviewsCleared)
    }

    @Test
    fun `failed review resets to one day`() {
        val p = UserProgress(mistakes = mapOf(Concept.OUTS to Mistake(Concept.OUTS, 2, 9)))
        val next = ProgressReducer.finishReview(p, mapOf(Concept.OUTS to false), 0, at(9)).first
        assertEquals(Mistake(Concept.OUTS, 0, 10), next.mistakes[Concept.OUTS])
    }

    @Test
    fun `blitz keeps the best score and hidden night owl unlocks at night`() {
        val (p, reward) = ProgressReducer.finishDrill(UserProgress(), DrillType.WINNER, DrillMode.BLITZ, 31, at(3, hour = 1))
        assertEquals(31, p.stats.blitzBest[DrillType.WINNER])
        val ids = reward.achievements.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("blitz_15", "blitz_30", "night_owl", "first_drill")))
        val (p2, _) = ProgressReducer.finishDrill(p, DrillType.WINNER, DrillMode.BLITZ, 12, at(3))
        assertEquals(31, p2.stats.blitzBest[DrillType.WINNER])
    }

    @Test
    fun `history is trimmed to 90 days`() {
        var p = UserProgress()
        p = ProgressReducer.finishDrill(p, DrillType.NAME_HAND, DrillMode.PRACTICE, 1, at(1)).first
        p = ProgressReducer.finishDrill(p, DrillType.NAME_HAND, DrillMode.PRACTICE, 1, at(200)).first
        assertEquals(setOf(200L), p.xpByDay.keys)
    }
}
