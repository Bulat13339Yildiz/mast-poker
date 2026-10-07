package app.mast.poker.achievements

import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import app.mast.poker.progress.UserProgress

enum class Tier(val ruName: String) { BRONZE("Бронза"), SILVER("Серебро"), GOLD("Золото") }

/** Context of the moment an achievement is checked: things not stored in progress. */
data class CheckContext(val hour: Int, val daysAway: Long = 0)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val tier: Tier,
    val hidden: Boolean = false,
    /** Optional progress towards the goal: current to target. */
    val progress: ((UserProgress) -> Pair<Int, Int>)? = null,
    val condition: (UserProgress, CheckContext) -> Boolean,
)

object Achievements {

    private fun chapterDone(id: String): (UserProgress, CheckContext) -> Boolean = { p, _ ->
        Curriculum.chapter(id).lessons.all { it.id in p.lessons }
    }

    private fun counter(target: Int, value: (UserProgress) -> Int): Pair<(UserProgress) -> Pair<Int, Int>, (UserProgress, CheckContext) -> Boolean> =
        Pair({ p -> value(p).coerceAtMost(target) to target }, { p, _ -> value(p) >= target })

    private fun counted(
        id: String, title: String, description: String, tier: Tier, target: Int, hidden: Boolean = false,
        value: (UserProgress) -> Int,
    ): Achievement {
        val (progress, condition) = counter(target, value)
        return Achievement(id, title, description, tier, hidden, progress, condition)
    }

    val all: List<Achievement> = listOf(
        // Обучение
        counted("first_lesson", "Первая раздача", "Пройди свой первый урок", Tier.BRONZE, 1) { it.lessons.size },
        Achievement("chapter_basics", "Знакомство состоялось", "Пройди главу «Знакомство»", Tier.BRONZE, condition = chapterDone("basics")),
        Achievement("chapter_hands", "Роял", "Пройди главу «Комбинации»", Tier.SILVER, condition = chapterDone("hands")),
        Achievement("chapter_flow", "За столом", "Пройди главу «Ход раздачи»", Tier.SILVER, condition = chapterDone("flow")),
        Achievement("chapter_preflop", "Разборчивый", "Пройди главу «Стартовые руки»", Tier.SILVER, condition = chapterDone("preflop")),
        Achievement("chapter_math", "Математик", "Пройди главу «Математика»", Tier.GOLD, condition = chapterDone("math")),
        Achievement("chapter_strategy", "Стратег", "Пройди главу «Стратегия»", Tier.GOLD, condition = chapterDone("strategy")),
        counted("all_lessons", "Выпускник", "Пройди все уроки", Tier.GOLD, Curriculum.lessonCount) { it.lessons.size },
        counted("perfect_lesson", "Чистая игра", "Пройди урок без единой ошибки", Tier.BRONZE, 1) { it.stats.perfectLessons },
        counted("perfect_5", "Перфекционист", "Пройди 5 уроков без ошибок", Tier.SILVER, 5) { it.stats.perfectLessons },

        // Точность
        counted("streak_10", "В масть", "Ответь верно 10 раз подряд", Tier.BRONZE, 10) { it.stats.bestAnswerStreak },
        counted("streak_20", "Снайпер", "Ответь верно 20 раз подряд", Tier.SILVER, 20) { it.stats.bestAnswerStreak },
        counted("streak_50", "Хладнокровие", "Ответь верно 50 раз подряд", Tier.GOLD, 50) { it.stats.bestAnswerStreak },
        counted("answers_100", "Сотня", "Дай 100 верных ответов", Tier.BRONZE, 100) { it.stats.answersCorrect },
        counted("answers_500", "Пятьсот", "Дай 500 верных ответов", Tier.SILVER, 500) { it.stats.answersCorrect },
        counted("answers_2000", "Две тысячи рук", "Дай 2000 верных ответов", Tier.GOLD, 2000) { it.stats.answersCorrect },

        // Тренажёры
        counted("first_drill", "Разминка", "Сыграй любой тренажёр", Tier.BRONZE, 1) { it.stats.drillsPlayed.values.sum() },
        counted("blitz_15", "Быстрые руки", "Набери 15 очков в блице", Tier.BRONZE, 15) { it.stats.blitzBest.values.maxOrNull() ?: 0 },
        counted("blitz_30", "Молния", "Набери 30 очков в блице", Tier.SILVER, 30) { it.stats.blitzBest.values.maxOrNull() ?: 0 },
        counted("blitz_45", "Телепат", "Набери 45 очков в блице", Tier.GOLD, 45) { it.stats.blitzBest.values.maxOrNull() ?: 0 },
        counted("all_drills", "Мастер на все руки", "Сыграй каждый из тренажёров", Tier.SILVER, DrillType.entries.size) { p ->
            DrillType.entries.count { (p.stats.drillsPlayed[it] ?: 0) > 0 }
        },
        counted("royal_spotted", "Королевская особа", "Узнай роял-флеш в тренажёре", Tier.BRONZE, 1, hidden = true) { it.stats.royalsSpotted },
        counted("wheel_10", "Колесо", "10 раз узнай стрит A-2-3-4-5", Tier.SILVER, 10) { it.stats.wheelsSpotted },

        // Привычка
        counted("days_3", "Втягиваюсь", "Занимайся 3 дня подряд", Tier.BRONZE, 3) { it.bestDayStreak },
        counted("days_7", "Неделя за столом", "Занимайся 7 дней подряд", Tier.SILVER, 7) { it.bestDayStreak },
        counted("days_30", "Месяц покера", "Занимайся 30 дней подряд", Tier.GOLD, 30) { it.bestDayStreak },
        counted("goals_10", "Цель взята", "Выполни дневную цель 10 раз", Tier.SILVER, 10) { it.goalsReached },

        // Повторение
        counted("review_first", "Работа над ошибками", "Пройди первое повторение", Tier.BRONZE, 1) { it.stats.reviewsDone },
        counted("review_clear", "Чистый лист", "Полностью разбери очередь ошибок", Tier.SILVER, 1) { it.stats.reviewsCleared },

        // Уровни
        Achievement("level_5", "Завсегдатай", "Достигни 5 уровня", Tier.SILVER) { p, _ -> p.level >= 5 },
        Achievement("level_10", "Акула", "Достигни 10 уровня", Tier.GOLD) { p, _ -> p.level >= 10 },

        // Второй уровень
        Achievement("chapter_ranges", "Мыслю диапазонами", "Пройди главу «Диапазоны»", Tier.SILVER, condition = chapterDone("ranges")),
        Achievement("chapter_math2", "Считаю EV", "Пройди главу «Математика 2»", Tier.GOLD, condition = chapterDone("math2")),
        Achievement("chapter_sizing", "Точный размер", "Пройди главу «Размер ставок»", Tier.SILVER, condition = chapterDone("sizing")),
        Achievement("chapter_preflop2", "Три бета", "Пройди главу «Префлоп 2»", Tier.SILVER, condition = chapterDone("preflop2")),
        Achievement("chapter_tournaments", "Турнирный боец", "Пройди главу «Турниры»", Tier.GOLD, condition = chapterDone("tournaments")),
        Achievement("chapter_opponents", "Читаю людей", "Пройди главу «Соперники»", Tier.GOLD, condition = chapterDone("opponents")),

        // Экзамены
        counted("exam_first", "Печать мастера", "Сдай экзамен любой главы", Tier.BRONZE, 1) { p -> p.exams.values.count { it.passed } },
        counted("exam_perfect", "Отличник", "Сдай экзамен без единой ошибки", Tier.SILVER, 1) { it.stats.perfectExams },
        counted("exam_all", "Золотая коллекция", "Сдай экзамены всех глав", Tier.GOLD, Curriculum.chapters.size) { p -> p.exams.values.count { it.passed } },

        // Задача дня и задания
        counted("puzzle_first", "Задача дня", "Реши первую задачу дня", Tier.BRONZE, 1) { it.puzzle.solved },
        counted("puzzle_7", "Неделя задач", "Решай задачу дня 7 дней подряд", Tier.SILVER, 7) { it.puzzle.bestStreak },
        counted("quest_first", "Задание выполнено", "Выполни задание недели", Tier.BRONZE, 1) { it.quests.totalDone },
        counted("quests_15", "Охотник за заданиями", "Выполни 15 заданий недели", Tier.GOLD, 15) { it.quests.totalDone },

        // Рейтинг
        Achievement("rating_1250", "Сильный игрок", "Подними рейтинг любой темы до 1250", Tier.SILVER) { p, _ -> (p.ratings.values.maxOrNull() ?: 0) >= 1250 },
        Achievement("rating_1450", "Мастер темы", "Подними рейтинг любой темы до 1450", Tier.GOLD) { p, _ -> (p.ratings.values.maxOrNull() ?: 0) >= 1450 },

        // Скрытые
        Achievement("night_owl", "Ночная сова", "Позанимайся после полуночи", Tier.BRONZE, hidden = true) { _, c -> c.hour in 0..3 },
        Achievement("early_bird", "Ранняя пташка", "Позанимайся до 7 утра", Tier.BRONZE, hidden = true) { _, c -> c.hour in 4..6 },
        Achievement("comeback", "Возвращение", "Вернись к занятиям после недельного перерыва", Tier.BRONZE, hidden = true) { _, c -> c.daysAway >= 7 },
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Achievement? = byId[id]

    /** Achievements satisfied by [progress] that are not unlocked yet. */
    fun newlyUnlocked(progress: UserProgress, context: CheckContext): List<Achievement> =
        all.filter { it.id !in progress.achievements && it.condition(progress, context) }
}
