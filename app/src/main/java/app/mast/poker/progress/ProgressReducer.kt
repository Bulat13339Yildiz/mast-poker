package app.mast.poker.progress

import app.mast.poker.achievements.Achievement
import app.mast.poker.achievements.Achievements
import app.mast.poker.achievements.CheckContext
import app.mast.poker.content.Concept
import app.mast.poker.content.DrillType

/** "Now" as the reducer sees it: local epoch day, hour, and wall-clock millis. */
data class Moment(val day: Long, val hour: Int, val millis: Long)

data class Reward(
    val xp: Int = 0,
    val levelBefore: Int,
    val levelAfter: Int,
    val achievements: List<Achievement> = emptyList(),
    val goalReached: Boolean = false,
    val dayStreak: Int = 0,
    /** Weekly quests completed by this action (their XP is included in [xp]). */
    val quests: List<Quest> = emptyList(),
) {
    val leveledUp: Boolean get() = levelAfter > levelBefore
}

enum class DrillMode { PRACTICE, BLITZ }

/** Flags a single answer can carry for rare-hand achievements. */
data class AnswerExtras(val royal: Boolean = false, val wheel: Boolean = false)

/**
 * Pure state transitions for [UserProgress]. Every public function returns the new
 * progress and the [Reward] the UI should celebrate.
 */
object ProgressReducer {

    object Xp {
        const val LESSON_BASE = 20
        const val LESSON_PER_CORRECT = 5
        const val LESSON_PERFECT_BONUS = 10
        const val LESSON_REPEAT = 5
        const val DRILL_PER_CORRECT = 2
        const val BLITZ_PER_CORRECT = 3
        const val REVIEW_PER_CORRECT = 4
        const val EXAM_FIRST_PASS = 60
        const val EXAM_REPEAT_PASS = 10
        const val PUZZLE_SOLVED = 25
        const val PUZZLE_TRIED = 5
    }

    /** Share of correct answers an exam needs: 6 of 8. */
    fun examPassed(correct: Int, total: Int): Boolean = total > 0 && correct * 4 >= total * 3

    private val reviewIntervals = listOf(1L, 3L, 7L)

    fun completeOnboarding(p: UserProgress, dailyGoalXp: Int): UserProgress =
        p.copy(onboarded = true, dailyGoalXp = dailyGoalXp)

    fun answer(
        p: UserProgress,
        concept: Concept,
        correct: Boolean,
        extras: AnswerExtras,
        now: Moment,
        difficulty: Int = Rating.difficulty(1),
    ): Pair<UserProgress, Reward> {
        val s = p.stats
        val streak = if (correct) s.answerStreak + 1 else 0
        val conceptStat = s.perConcept[concept] ?: ConceptStat()
        val stats = s.copy(
            answersCorrect = s.answersCorrect + if (correct) 1 else 0,
            answersTotal = s.answersTotal + 1,
            answerStreak = streak,
            bestAnswerStreak = maxOf(s.bestAnswerStreak, streak),
            perConcept = s.perConcept + (concept to conceptStat.copy(
                correct = conceptStat.correct + if (correct) 1 else 0,
                total = conceptStat.total + 1,
            )),
            royalsSpotted = s.royalsSpotted + if (correct && extras.royal) 1 else 0,
            wheelsSpotted = s.wheelsSpotted + if (correct && extras.wheel) 1 else 0,
        )
        val rating = Rating.update(p.rating(concept), difficulty, correct)
        var next = p.copy(stats = stats, ratings = p.ratings + (concept to rating))
        if (!correct) next = next.copy(mistakes = next.mistakes + (concept to Mistake(concept, 0, now.day)))
        return finish(p, next, 0, now)
    }

    fun completeLesson(p: UserProgress, lessonId: String, correct: Int, total: Int, now: Moment): Pair<UserProgress, Reward> {
        val perfect = correct == total
        val repeat = lessonId in p.lessons
        val xp = if (repeat) Xp.LESSON_REPEAT
        else Xp.LESSON_BASE + Xp.LESSON_PER_CORRECT * correct + if (perfect && total > 0) Xp.LESSON_PERFECT_BONUS else 0
        val previous = p.lessons[lessonId]
        val record = if (previous == null || correct > previous.correct) LessonRecord(correct, total, perfect, now.millis) else previous
        val next = p.copy(
            lessons = p.lessons + (lessonId to record),
            stats = p.stats.copy(
                perfectLessons = p.stats.perfectLessons + if (perfect && !repeat && total > 0) 1 else 0,
                lessonsCompleted = p.stats.lessonsCompleted + 1,
            ),
        )
        return finish(p, next, xp, now)
    }

    fun finishDrill(p: UserProgress, type: DrillType, mode: DrillMode, correct: Int, now: Moment): Pair<UserProgress, Reward> {
        val s = p.stats
        val stats = s.copy(
            drillsPlayed = s.drillsPlayed + (type to (s.drillsPlayed[type] ?: 0) + 1),
            blitzBest = if (mode == DrillMode.BLITZ) s.blitzBest + (type to maxOf(s.blitzBest[type] ?: 0, correct)) else s.blitzBest,
            blitzPlayed = s.blitzPlayed + if (mode == DrillMode.BLITZ) 1 else 0,
        )
        val perCorrect = if (mode == DrillMode.BLITZ) Xp.BLITZ_PER_CORRECT else Xp.DRILL_PER_CORRECT
        return finish(p, p.copy(stats = stats), correct * perCorrect, now)
    }

    /** [results]: concept → whether the review of that concept went well. */
    fun finishReview(p: UserProgress, results: Map<Concept, Boolean>, correctAnswers: Int, now: Moment): Pair<UserProgress, Reward> {
        var mistakes = p.mistakes
        for ((concept, ok) in results) {
            val m = mistakes[concept] ?: continue
            mistakes = if (ok) {
                if (m.level >= reviewIntervals.lastIndex) mistakes - concept
                else mistakes + (concept to m.copy(level = m.level + 1, dueDay = now.day + reviewIntervals[m.level + 1]))
            } else {
                mistakes + (concept to m.copy(level = 0, dueDay = now.day + reviewIntervals[0]))
            }
        }
        val cleared = p.dueMistakes(now.day).isNotEmpty() && mistakes.values.none { it.dueDay <= now.day }
        val stats = p.stats.copy(
            reviewsDone = p.stats.reviewsDone + 1,
            reviewsCleared = p.stats.reviewsCleared + if (cleared) 1 else 0,
        )
        return finish(p, p.copy(mistakes = mistakes, stats = stats), correctAnswers * Xp.REVIEW_PER_CORRECT, now)
    }

    fun finishExam(p: UserProgress, chapterId: String, correct: Int, total: Int, now: Moment): Pair<UserProgress, Reward> {
        val passed = examPassed(correct, total)
        val previous = p.exams[chapterId]
        val firstPass = passed && previous?.passed != true
        val record = if (previous == null || correct > previous.best) {
            ExamRecord(correct, total, passed || previous?.passed == true, now.millis)
        } else {
            previous.copy(passed = previous.passed || passed)
        }
        val xp = when {
            firstPass -> Xp.EXAM_FIRST_PASS
            passed -> Xp.EXAM_REPEAT_PASS
            else -> 0
        }
        val stats = p.stats.copy(
            examsPassed = p.stats.examsPassed + if (passed) 1 else 0,
            perfectExams = p.stats.perfectExams + if (passed && correct == total) 1 else 0,
        )
        return finish(p, p.copy(exams = p.exams + (chapterId to record), stats = stats), xp, now)
    }

    /** The day's puzzle: only the first attempt of a day counts. */
    fun solvePuzzle(p: UserProgress, correct: Boolean, now: Moment): Pair<UserProgress, Reward> {
        if (p.puzzle.lastDay == now.day) return finish(p, p, 0, now)
        val prev = p.puzzle
        val streak = when {
            !correct -> 0
            prev.lastDay == now.day - 1 && prev.lastCorrect -> prev.streak + 1
            else -> 1
        }
        val puzzle = PuzzleState(
            lastDay = now.day,
            lastCorrect = correct,
            streak = streak,
            bestStreak = maxOf(prev.bestStreak, streak),
            solved = prev.solved + if (correct) 1 else 0,
        )
        return finish(p, p.copy(puzzle = puzzle), if (correct) Xp.PUZZLE_SOLVED else Xp.PUZZLE_TRIED, now)
    }

    /** Puzzle streak as shown today: broken after a missed day or a wrong answer. */
    fun visiblePuzzleStreak(p: UserProgress, today: Long): Int {
        val last = p.puzzle.lastDay ?: return 0
        return if (p.puzzle.lastCorrect && today - last <= 1) p.puzzle.streak else 0
    }

    fun updateSettings(p: UserProgress, settings: Settings): UserProgress = p.copy(settings = settings)

    /** Applies XP and the daily streak, then checks achievements against the result. */
    private fun finish(before: UserProgress, changed: UserProgress, actionXp: Int, now: Moment): Pair<UserProgress, Reward> {
        val daysAway = before.lastActiveDay?.let { now.day - it } ?: 0
        var p = touchDay(changed, now.day)
        // A new week starts its quests from the counters as they were before this action.
        val week = Quests.week(now.day)
        if (p.quests.week != week) p = p.copy(quests = Quests.roll(before, week))
        val completed = p.quests.ids.mapNotNull(Quests::byId)
            .filter { it.id !in p.quests.done && Quests.progress(p, it) >= it.target }
        if (completed.isNotEmpty()) {
            p = p.copy(quests = p.quests.copy(done = p.quests.done + completed.map { it.id }, totalDone = p.quests.totalDone + completed.size))
        }
        val xp = actionXp + completed.size * Quests.XP
        val goalBefore = p.xpOn(now.day) >= p.dailyGoalXp
        if (xp > 0) {
            val history = (p.xpByDay + (now.day to p.xpOn(now.day) + xp))
                .filterKeys { it > now.day - UserProgress.HISTORY_DAYS }
            p = p.copy(xp = p.xp + xp, xpByDay = history)
        }
        val goalNow = !goalBefore && p.xpOn(now.day) >= p.dailyGoalXp
        if (goalNow) p = p.copy(goalsReached = p.goalsReached + 1)

        val unlocked = Achievements.newlyUnlocked(p, CheckContext(now.hour, daysAway))
        if (unlocked.isNotEmpty()) p = p.copy(achievements = p.achievements + unlocked.associate { it.id to now.millis })

        return p to Reward(
            xp = xp,
            levelBefore = before.level,
            levelAfter = p.level,
            achievements = unlocked,
            goalReached = goalNow,
            dayStreak = p.dayStreak,
            quests = completed,
        )
    }

    private fun touchDay(p: UserProgress, today: Long): UserProgress {
        val last = p.lastActiveDay
        if (last == today) return p
        val streak = if (last == today - 1) p.dayStreak + 1 else 1
        return p.copy(dayStreak = streak, bestDayStreak = maxOf(p.bestDayStreak, streak), lastActiveDay = today)
    }

    /** Streak as shown today: it is broken if the last active day was before yesterday. */
    fun visibleStreak(p: UserProgress, today: Long): Int {
        val last = p.lastActiveDay ?: return 0
        return if (today - last <= 1) p.dayStreak else 0
    }
}
