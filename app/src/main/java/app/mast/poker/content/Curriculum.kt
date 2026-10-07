package app.mast.poker.content

import app.mast.poker.content.chapters.basicsChapter
import app.mast.poker.content.chapters.flowChapter
import app.mast.poker.content.chapters.handsChapter
import app.mast.poker.content.chapters.mathChapter
import app.mast.poker.content.chapters.preflopChapter
import app.mast.poker.content.chapters.strategyChapter

object Curriculum {
    val chapters: List<Chapter> = listOf(
        basicsChapter,
        handsChapter,
        flowChapter,
        preflopChapter,
        mathChapter,
        strategyChapter,
    )

    val lessons: List<Lesson> = chapters.flatMap { it.lessons }
    val lessonCount: Int = lessons.size

    private val chapterById = chapters.associateBy { it.id }
    private val lessonById = lessons.associateBy { it.id }
    private val chapterOfLesson = chapters.flatMap { c -> c.lessons.map { it.id to c } }.toMap()

    fun chapter(id: String): Chapter = chapterById.getValue(id)
    fun lesson(id: String): Lesson = lessonById.getValue(id)
    fun chapterOf(lessonId: String): Chapter = chapterOfLesson.getValue(lessonId)

    /** Lessons open strictly in order: the next one unlocks when the previous one is completed. */
    fun isUnlocked(lessonId: String, completed: Set<String>): Boolean {
        val index = lessons.indexOfFirst { it.id == lessonId }
        return index == 0 || lessons[index - 1].id in completed
    }

    fun nextLesson(completed: Set<String>): Lesson? = lessons.firstOrNull { it.id !in completed }

    fun drillUnlocked(type: DrillType, completed: Set<String>): Boolean =
        lessons.any { it.unlocksDrill == type && it.id in completed }

    fun lessonUnlockingDrill(type: DrillType): Lesson? = lessons.firstOrNull { it.unlocksDrill == type }

    /** All lesson questions grouped by the concept they train — the pool for reviews. */
    val questionsByConcept: Map<Concept, List<Question>> =
        lessons.flatMap { l -> l.steps.filterIsInstance<Step.Ask>().map { it.question } }.groupBy { it.concept }
}
