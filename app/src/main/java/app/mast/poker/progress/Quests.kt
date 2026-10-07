package app.mast.poker.progress

import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import kotlin.random.Random

/** A weekly goal measured as growth of one counter since the week began. */
data class Quest(
    val id: String,
    val title: String,
    val target: Int,
    val counter: (UserProgress) -> Int,
    /** Whether the player can work on it at all, judged when the week starts. */
    val available: (UserProgress) -> Boolean = { true },
)

object Quests {
    const val XP = 40
    const val PER_WEEK = 3

    private fun anyDrill(p: UserProgress) = DrillType.entries.any { it != DrillType.HAND_SIM && Curriculum.drillUnlocked(it, p.lessons.keys) }

    val pool: List<Quest> = listOf(
        Quest("answers", "Дай 60 верных ответов", 60, { it.stats.answersCorrect }),
        Quest("xp", "Набери 250 XP", 250, { it.xp }),
        Quest("lessons", "Пройди 3 урока", 3, { it.stats.lessonsCompleted }),
        Quest("drills", "Сыграй 5 тренировок", 5, { it.stats.drillsPlayed.values.sum() }, ::anyDrill),
        Quest("puzzles", "Реши 4 задачи дня", 4, { it.puzzle.solved }, ::anyDrill),
        Quest("blitz", "Сыграй 3 блица", 3, { it.stats.blitzPlayed }, ::anyDrill),
        Quest("sim", "Сыграй 2 сессии раздач", 2, { it.stats.drillsPlayed[DrillType.HAND_SIM] ?: 0 },
            { Curriculum.drillUnlocked(DrillType.HAND_SIM, it.lessons.keys) }),
        Quest("reviews", "Пройди 2 повторения ошибок", 2, { it.stats.reviewsDone }, { it.mistakes.isNotEmpty() }),
        Quest("exam", "Сдай экзамен любой главы", 1, { it.stats.examsPassed },
            { p -> Curriculum.chapters.any { c -> c.lessons.all { it.id in p.lessons } } }),
    )

    private val byId = pool.associateBy { it.id }

    fun byId(id: String): Quest? = byId[id]

    /** Weeks start on Monday; epoch day 0 was a Thursday. */
    fun week(day: Long): Long = Math.floorDiv(day + 3, 7L)

    /** First day (Monday) of [week]. */
    fun weekStart(week: Long): Long = week * 7 - 3

    /** This week's three quests, the same for the whole week. */
    fun pick(p: UserProgress, week: Long): List<Quest> =
        pool.filter { it.available(p) }.shuffled(Random(week)).take(PER_WEEK)

    fun roll(p: UserProgress, week: Long): QuestState {
        val quests = pick(p, week)
        return QuestState(
            week = week,
            ids = quests.map { it.id },
            baseline = quests.associate { it.id to it.counter(p) },
            done = emptySet(),
            totalDone = p.quests.totalDone,
        )
    }

    /** Progress of [quest] this week, capped at its target. */
    fun progress(p: UserProgress, quest: Quest): Int =
        (quest.counter(p) - (p.quests.baseline[quest.id] ?: quest.counter(p))).coerceIn(0, quest.target)

    /** The quests shown on [day]: the stored ones, or a fresh preview when a new week has begun. */
    fun current(p: UserProgress, day: Long): Pair<List<Quest>, UserProgress> {
        val week = week(day)
        val state = if (p.quests.week == week) p else p.copy(quests = roll(p, week))
        return state.quests.ids.mapNotNull(::byId) to state
    }
}
