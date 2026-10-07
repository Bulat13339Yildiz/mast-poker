package app.mast.poker.practice

import app.mast.poker.content.Chapter
import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import app.mast.poker.content.Question
import app.mast.poker.content.Step
import kotlin.random.Random

/** Chapter exams and the daily puzzle. */
object Challenges {
    const val EXAM_SIZE = 8

    /**
     * Eight questions for a chapter exam: half fresh ones from the chapter's drills at the
     * harder levels (when the chapter has drills), the rest from its lessons.
     */
    fun exam(chapter: Chapter, random: Random = Random.Default): List<Question> {
        val fromLessons = chapter.lessons.flatMap { l -> l.steps.filterIsInstance<Step.Ask>().map { it.question } }.shuffled(random)
        val drills = chapter.lessons.mapNotNull { it.unlocksDrill }.filter { it != DrillType.HAND_SIM }
        val exercises = Exercises(DrillGenerator(random), random)
        val generated = if (drills.isEmpty()) emptyList()
        else List(EXAM_SIZE / 2) { i -> exercises.next(drills[i % drills.size], 2 + random.nextInt(2)) }
        return (generated + fromLessons.take(EXAM_SIZE - generated.size)).shuffled(random)
    }

    /** Drills that can supply the puzzle: quick single answers only. */
    private fun puzzleDrills(unlocked: Set<String>): List<DrillType> =
        DrillType.entries.filter { it != DrillType.HAND_SIM && it != DrillType.RANGES && Curriculum.drillUnlocked(it, unlocked) }

    fun puzzleAvailable(completedLessons: Set<String>): Boolean = puzzleDrills(completedLessons).isNotEmpty()

    private fun seed(day: Long) = Random(day * 7919 + 17)

    /** Which drill the day's puzzle comes from — cheap, for cards and titles. */
    fun puzzleType(day: Long, completedLessons: Set<String>): DrillType? {
        val pool = puzzleDrills(completedLessons)
        return if (pool.isEmpty()) null else pool[seed(day).nextInt(pool.size)]
    }

    /** The day's puzzle — the same hard question all day long, from a drill the player has opened. */
    fun puzzle(day: Long, completedLessons: Set<String>): Pair<DrillType, Question>? {
        val pool = puzzleDrills(completedLessons)
        if (pool.isEmpty()) return null
        val random = seed(day)
        val type = pool[random.nextInt(pool.size)]
        return type to Exercises(DrillGenerator(random), random).next(type, 3)
    }
}
