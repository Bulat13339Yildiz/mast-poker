package app.mast.poker.progress

import app.mast.poker.content.Concept
import app.mast.poker.content.DrillType
import kotlinx.serialization.Serializable

@Serializable
data class UserProgress(
    val onboarded: Boolean = false,
    val dailyGoalXp: Int = 60,
    val xp: Int = 0,
    val lessons: Map<String, LessonRecord> = emptyMap(),
    /** Epoch day → XP earned that day. Trimmed to the last [HISTORY_DAYS] days. */
    val xpByDay: Map<Long, Int> = emptyMap(),
    val dayStreak: Int = 0,
    val bestDayStreak: Int = 0,
    val lastActiveDay: Long? = null,
    val goalsReached: Int = 0,
    val achievements: Map<String, Long> = emptyMap(),
    val stats: Stats = Stats(),
    val mistakes: Map<Concept, Mistake> = emptyMap(),
    val settings: Settings = Settings(),
    /** Skill rating per concept (Elo, starts at [Rating.START]). */
    val ratings: Map<Concept, Int> = emptyMap(),
    /** Chapter id → best exam result. */
    val exams: Map<String, ExamRecord> = emptyMap(),
    val puzzle: PuzzleState = PuzzleState(),
    val quests: QuestState = QuestState(),
) {
    val level: Int get() = Levels.levelFor(xp)

    fun rating(concept: Concept): Int = ratings[concept] ?: Rating.START

    fun xpOn(day: Long): Int = xpByDay[day] ?: 0

    fun dueMistakes(today: Long): List<Mistake> = mistakes.values.filter { it.dueDay <= today }

    companion object {
        const val HISTORY_DAYS = 90
    }
}

@Serializable
data class LessonRecord(val correct: Int, val total: Int, val perfect: Boolean, val completedAt: Long)

@Serializable
data class ConceptStat(val correct: Int = 0, val total: Int = 0) {
    val accuracy: Float get() = if (total == 0) 0f else correct.toFloat() / total
}

@Serializable
data class Stats(
    val answersCorrect: Int = 0,
    val answersTotal: Int = 0,
    val answerStreak: Int = 0,
    val bestAnswerStreak: Int = 0,
    val perConcept: Map<Concept, ConceptStat> = emptyMap(),
    val drillsPlayed: Map<DrillType, Int> = emptyMap(),
    val blitzBest: Map<DrillType, Int> = emptyMap(),
    val perfectLessons: Int = 0,
    val royalsSpotted: Int = 0,
    val wheelsSpotted: Int = 0,
    val reviewsDone: Int = 0,
    val reviewsCleared: Int = 0,
    /** Lesson completions including repeats. */
    val lessonsCompleted: Int = 0,
    val blitzPlayed: Int = 0,
    val examsPassed: Int = 0,
    val perfectExams: Int = 0,
)

@Serializable
data class ExamRecord(val best: Int, val total: Int, val passed: Boolean, val at: Long)

/** The daily puzzle: one attempt per day; the streak counts days solved in a row. */
@Serializable
data class PuzzleState(
    val lastDay: Long? = null,
    val lastCorrect: Boolean = false,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val solved: Int = 0,
)

/** This week's quests: which ones, counters at the start of the week, and those already rewarded. */
@Serializable
data class QuestState(
    val week: Long = -1,
    val ids: List<String> = emptyList(),
    val baseline: Map<String, Int> = emptyMap(),
    val done: Set<String> = emptySet(),
    val totalDone: Int = 0,
)

/** One concept in the spaced-repetition queue. [level] 0..2 maps to 1/3/7-day intervals. */
@Serializable
data class Mistake(val concept: Concept, val level: Int, val dueDay: Long)

@Serializable
data class Settings(
    val haptics: Boolean = true,
    val reducedMotion: Boolean = false,
)

data class LevelTitle(val fromLevel: Int, val title: String)

object Levels {
    /** Total XP needed to reach [level]: 0, 100, 300, 600, 1000, … */
    fun xpForLevel(level: Int): Int = 50 * (level - 1) * level

    fun levelFor(xp: Int): Int {
        var level = 1
        while (xp >= xpForLevel(level + 1)) level++
        return level
    }

    /** Progress inside the current level, 0f..1f. */
    fun fraction(xp: Int): Float {
        val l = levelFor(xp)
        val from = xpForLevel(l)
        val to = xpForLevel(l + 1)
        return (xp - from).toFloat() / (to - from)
    }

    val titles = listOf(
        LevelTitle(1, "Новичок"),
        LevelTitle(3, "Любитель"),
        LevelTitle(5, "Завсегдатай"),
        LevelTitle(7, "Регуляр"),
        LevelTitle(10, "Акула"),
        LevelTitle(13, "Профи"),
        LevelTitle(16, "Легенда"),
    )

    fun titleFor(level: Int): String = titles.last { level >= it.fromLevel }.title
}
