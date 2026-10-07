package app.mast.poker.progress

import app.mast.poker.content.Concept
import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import app.mast.poker.practice.Challenges
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ProgressionTest {

    private fun at(day: Long, hour: Int = 12) = Moment(day, hour, day * 86_400_000L)

    @Test
    fun `progress saved by version 1 loads unchanged`() {
        // Exactly what 1.0.0 wrote: no ratings, exams, puzzle or quests.
        val v1 = """
            {"onboarded":true,"dailyGoalXp":60,"xp":1250,
             "lessons":{"basics_deck":{"correct":3,"total":3,"perfect":true,"completedAt":1},"hands_ladder":{"correct":2,"total":3,"perfect":false,"completedAt":2}},
             "xpByDay":{"20000":40,"20001":80},"dayStreak":2,"bestDayStreak":5,"lastActiveDay":20001,"goalsReached":3,
             "achievements":{"first_lesson":1,"days_3":2},
             "stats":{"answersCorrect":40,"answersTotal":50,"answerStreak":3,"bestAnswerStreak":12,
                      "perConcept":{"HAND_RANKINGS":{"correct":10,"total":12},"POT_ODDS":{"correct":3,"total":5}},
                      "drillsPlayed":{"NAME_HAND":4,"HAND_SIM":1},"blitzBest":{"NAME_HAND":17},
                      "perfectLessons":1,"royalsSpotted":1,"wheelsSpotted":2,"reviewsDone":1,"reviewsCleared":0},
             "mistakes":{"OUTS":{"concept":"OUTS","level":1,"dueDay":20003}},
             "settings":{"haptics":false,"reducedMotion":true}}
        """.trimIndent()
        val p = runBlocking { ProgressSerializer.readFrom(ByteArrayInputStream(v1.encodeToByteArray())) }
        assertTrue(p.onboarded)
        assertEquals(1250, p.xp)
        assertEquals(setOf("basics_deck", "hands_ladder"), p.lessons.keys)
        assertEquals(80, p.xpOn(20001))
        assertEquals(2, p.dayStreak)
        assertEquals(setOf("first_lesson", "days_3"), p.achievements.keys)
        assertEquals(12, p.stats.perConcept.getValue(Concept.HAND_RANKINGS).total)
        assertEquals(4, p.stats.drillsPlayed[DrillType.NAME_HAND])
        assertEquals(17, p.stats.blitzBest[DrillType.NAME_HAND])
        assertEquals(20003L, p.mistakes.getValue(Concept.OUTS).dueDay)
        assertFalse(p.settings.haptics)
        // New things start from scratch.
        assertTrue(p.ratings.isEmpty() && p.exams.isEmpty())
        assertEquals(Rating.START, p.rating(Concept.OUTS))
        assertEquals(PuzzleState(), p.puzzle)
        // And the old progress keeps working with the new reducer.
        val (next, _) = ProgressReducer.answer(p, Concept.OUTS, correct = true, AnswerExtras(), at(20002))
        assertEquals(3, next.dayStreak)
        assertTrue(next.rating(Concept.OUTS) > Rating.START)
    }

    @Test
    fun `version 1 lessons keep their place at the start of the course`() {
        val v1 = listOf(
            "basics_deck", "basics_goal", "basics_holdem",
        )
        assertEquals(v1, Curriculum.lessons.take(3).map { it.id })
        assertEquals(32, Curriculum.chapters.filter { it.level == 1 }.sumOf { it.lessons.size })
        assertEquals("ranges_think", Curriculum.lessons[32].id)
    }

    @Test
    fun `elo rewards hard questions more`() {
        assertEquals(0.5, Rating.expected(1000, 1000), 1e-9)
        assertEquals(1012, Rating.update(1000, 1000, correct = true))
        assertEquals(988, Rating.update(1000, 1000, correct = false))
        assertTrue(Rating.update(1000, 1400, correct = true) - 1000 > Rating.update(1000, 1000, correct = true) - 1000)
        assertEquals(1, Rating.baseLevel(1000))
        assertEquals(2, Rating.baseLevel(1150))
        assertEquals(3, Rating.baseLevel(1350))
        val levels = List(1000) { Rating.level(1000, kotlin.random.Random(it)) }
        assertTrue(levels.all { it in 1..2 })
        assertTrue(levels.count { it == 1 } > 600)
    }

    @Test
    fun `exams need six of eight and pay once in full`() {
        assertTrue(ProgressReducer.examPassed(6, 8))
        assertFalse(ProgressReducer.examPassed(5, 8))
        val (failed, r0) = ProgressReducer.finishExam(UserProgress(), "basics", 5, 8, at(10))
        assertEquals(0, r0.xp - r0.quests.size * Quests.XP)
        assertFalse(failed.exams.getValue("basics").passed)
        val (passed, r1) = ProgressReducer.finishExam(failed, "basics", 7, 8, at(10))
        assertEquals(ProgressReducer.Xp.EXAM_FIRST_PASS, r1.xp - r1.quests.size * Quests.XP)
        assertTrue(r1.achievements.any { it.id == "exam_first" })
        val (again, r2) = ProgressReducer.finishExam(passed, "basics", 6, 8, at(11))
        assertEquals(ProgressReducer.Xp.EXAM_REPEAT_PASS, r2.xp - r2.quests.size * Quests.XP)
        assertEquals(7, again.exams.getValue("basics").best)
        val (perfect, r3) = ProgressReducer.finishExam(again, "basics", 8, 8, at(12))
        assertTrue(r3.achievements.any { it.id == "exam_perfect" })
        assertEquals(8, perfect.exams.getValue("basics").best)
    }

    @Test
    fun `daily puzzle counts once a day and builds a streak`() {
        var p = UserProgress()
        val (a, r1) = ProgressReducer.solvePuzzle(p, correct = true, now = at(100))
        assertEquals(ProgressReducer.Xp.PUZZLE_SOLVED, r1.xp - r1.quests.size * Quests.XP)
        val (b, r2) = ProgressReducer.solvePuzzle(a, correct = true, now = at(100))
        assertEquals(0, r2.xp)
        assertEquals(1, b.puzzle.solved)
        p = ProgressReducer.solvePuzzle(b, correct = true, now = at(101)).first
        assertEquals(2, p.puzzle.streak)
        assertEquals(2, ProgressReducer.visiblePuzzleStreak(p, 102))
        assertEquals(0, ProgressReducer.visiblePuzzleStreak(p, 103))
        p = ProgressReducer.solvePuzzle(p, correct = false, now = at(102)).first
        assertEquals(0, p.puzzle.streak)
        assertEquals(2, p.puzzle.bestStreak)
        p = ProgressReducer.solvePuzzle(p, correct = true, now = at(103)).first
        assertEquals(1, p.puzzle.streak)
    }

    @Test
    fun `weeks start on monday`() {
        // 1970-01-05 (epoch day 4) was a Monday.
        assertEquals(Quests.week(4), Quests.week(10))
        assertEquals(Quests.week(4) + 1, Quests.week(11))
        assertEquals(4L, Quests.weekStart(Quests.week(7)))
        // 2026-10-05 is a Monday.
        val monday = java.time.LocalDate.of(2026, 10, 5).toEpochDay()
        assertEquals(monday, Quests.weekStart(Quests.week(monday + 3)))
    }

    @Test
    fun `quests pay once and roll over with the week`() {
        val day = java.time.LocalDate.of(2026, 10, 5).toEpochDay()
        var p = ProgressReducer.completeLesson(UserProgress(), Curriculum.lessons[0].id, 3, 3, at(day)).first
        assertEquals(Quests.week(day), p.quests.week)
        assertEquals(3, p.quests.ids.size)
        // Answer until the 60-answers quest (if picked) or any counter quest completes.
        var paid = 0
        repeat(70) { i ->
            val (n, r) = ProgressReducer.answer(p, Concept.HAND_RANKINGS, correct = true, AnswerExtras(), at(day + 1))
            paid += r.quests.size
            p = n
            if (i == 69 && "answers" in p.quests.ids) assertTrue("answers" in p.quests.done)
        }
        assertEquals(p.quests.done.size, paid)
        assertEquals(paid, p.quests.totalDone)
        // Next week: fresh quests, nothing done yet, total kept.
        val (next, _) = ProgressReducer.answer(p, Concept.HAND_RANKINGS, correct = true, AnswerExtras(), at(day + 7))
        assertEquals(Quests.week(day + 7), next.quests.week)
        assertTrue(next.quests.done.isEmpty() || next.quests.done.all { id -> Quests.progress(next, Quests.byId(id)!!) >= Quests.byId(id)!!.target })
        assertEquals(paid + next.quests.done.size, next.quests.totalDone)
    }

    @Test
    fun `quests a player cannot do are not offered`() {
        val fresh = UserProgress()
        val ids = (0L..60L).flatMap { w -> Quests.pick(fresh, w).map { it.id } }.toSet()
        assertFalse("sim" in ids)
        assertFalse("exam" in ids)
        assertFalse("reviews" in ids)
        assertTrue(ids.containsAll(listOf("answers", "lessons", "xp")))
    }

    @Test
    fun `every chapter can hold an exam`() {
        Curriculum.chapters.forEach { c ->
            val qs = Challenges.exam(c, kotlin.random.Random(1))
            assertEquals(c.id, Challenges.EXAM_SIZE, qs.size)
            assertEquals(c.id, qs.size, qs.toSet().size)
        }
    }

    @Test
    fun `the daily puzzle is stable for the day`() {
        val all = Curriculum.lessons.map { it.id }.toSet()
        assertEquals(null, Challenges.puzzle(1, emptySet()))
        val a = Challenges.puzzle(20_000, all)!!
        val b = Challenges.puzzle(20_000, all)!!
        assertEquals(a.first, b.first)
        assertEquals(a.second.prompt, b.second.prompt)
        assertEquals(a.first, Challenges.puzzleType(20_000, all))
        val types = (20_000L until 20_060L).map { Challenges.puzzleType(it, all) }.toSet()
        assertTrue(types.size >= 6)
        assertFalse(DrillType.HAND_SIM in types || DrillType.RANGES in types)
    }
}
