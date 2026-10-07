package app.mast.poker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.mast.poker.achievements.Tier
import app.mast.poker.content.Chapter
import app.mast.poker.content.Curriculum
import app.mast.poker.content.Question
import app.mast.poker.practice.Challenges
import app.mast.poker.practice.Exercises
import app.mast.poker.progress.ProgressReducer
import app.mast.poker.progress.Quests
import app.mast.poker.progress.Rating
import app.mast.poker.progress.UserProgress
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.CasinoChip
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.Medal
import app.mast.poker.ui.components.PressablePanel
import app.mast.poker.ui.components.XpBar
import app.mast.poker.ui.theme.MastColors

/** Hands out the exam's eight questions, building a fresh exam for every new round. */
private class ExamDeck(private val chapter: Chapter) {
    private var questions: List<Question> = emptyList()
    private var index = 0

    @Synchronized
    fun next(): Question {
        if (index % Challenges.EXAM_SIZE == 0) questions = Challenges.exam(chapter)
        return questions[index++ % Challenges.EXAM_SIZE]
    }
}

@Composable
fun ExamScreen(chapterId: String, nav: Nav) {
    val app = LocalApp.current
    val chapter = remember { Curriculum.chapter(chapterId) }
    val deck = remember { ExamDeck(chapter) }
    val need = (Challenges.EXAM_SIZE * 3 + 3) / 4
    QuizSession(
        title = "Экзамен · ${chapter.title}",
        total = Challenges.EXAM_SIZE,
        timeLimitSec = null,
        nextQuestion = deck::next,
        onFinish = { results ->
            val correct = results.count { it.second }
            // Unanswered questions count as wrong.
            val reward = app.finishExam(chapterId, correct, Challenges.EXAM_SIZE)
            val passed = ProgressReducer.examPassed(correct, Challenges.EXAM_SIZE)
            reward to SessionSummary(
                if (passed) "Экзамен сдан!" else "Экзамен не сдан",
                if (passed) "Глава «${chapter.title}» отмечена золотой печатью"
                else "Нужно $need верных из ${Challenges.EXAM_SIZE}. Повтори уроки и попробуй снова",
                "$correct из ${Challenges.EXAM_SIZE} верно",
                outOf = Challenges.EXAM_SIZE,
            )
        },
        onExit = nav::back,
        difficultyOf = { Rating.difficulty(2) },
    )
}

@Composable
fun PuzzleScreen(nav: Nav) {
    val app = LocalApp.current
    val day = remember { app.today() }
    val start = remember { app.progress.value }
    val alreadyTried = start?.puzzle?.lastDay == day
    val lessons = remember { start?.lessons?.keys.orEmpty() }
    val puzzle = remember { lazy { Challenges.puzzle(day, lessons) } }
    QuizSession(
        title = "Задача дня",
        total = 1,
        timeLimitSec = null,
        nextQuestion = { puzzle.value?.second ?: error("No puzzle available") },
        onFinish = { results ->
            val correct = results.firstOrNull()?.second == true
            val reward = app.solvePuzzle(correct)
            val streak = app.progress.value?.puzzle?.streak ?: 0
            reward to SessionSummary(
                title = when {
                    alreadyTried -> "Задача дня"
                    correct -> "Задача решена!"
                    else -> "Не в этот раз"
                },
                subtitle = when {
                    alreadyTried -> "Сегодня задача уже засчитана — новая будет завтра"
                    correct -> "Серия задач: $streak ${Exercises.plural(streak, "день", "дня", "дней")}. Новая задача — завтра"
                    else -> "Разбор ты уже видел. Новая задача — завтра"
                },
            )
        },
        onExit = nav::back,
        difficultyOf = { Rating.difficulty(3) },
        restartable = false,
    )
}

// --- Cards on the learning tab ------------------------------------------------------------

@Composable
fun PuzzleCard(p: UserProgress, today: Long, onOpen: () -> Unit) {
    val type = remember(p.lessons.size, today) { Challenges.puzzleType(today, p.lessons.keys) } ?: return
    val doneToday = p.puzzle.lastDay == today
    val streak = ProgressReducer.visiblePuzzleStreak(p, today)
    PressablePanel(onOpen, Modifier.fillMaxWidth(), gilded = !doneToday, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { CasinoChip(50.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("ЗАДАЧА ДНЯ", style = MaterialTheme.typography.labelSmall, color = MastColors.Gold)
                Text(type.title, style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                Text(
                    when {
                        doneToday && p.puzzle.lastCorrect -> "Решена · серия $streak ${Exercises.plural(streak, "день", "дня", "дней")}"
                        doneToday -> "Попытка использована · завтра новая"
                        else -> "Сложный вопрос раз в день · +${ProgressReducer.Xp.PUZZLE_SOLVED} XP"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (doneToday && p.puzzle.lastCorrect) MastColors.Correct else MastColors.TextSecondary,
                )
            }
            if (doneToday && p.puzzle.lastCorrect) Icon(MastIcons.Check, null, tint = MastColors.Correct)
            else Icon(MastIcons.ChevronRight, null, tint = MastColors.TextMuted)
        }
    }
}

@Composable
fun QuestsCard(p: UserProgress, today: Long) {
    val (quests, state) = remember(p, today) { Quests.current(p, today) }
    if (quests.isEmpty()) return
    val daysLeft = (Quests.weekStart(Quests.week(today)) + 7 - today).toInt()
    GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ЗАДАНИЯ НЕДЕЛИ", style = MaterialTheme.typography.labelSmall, color = MastColors.Gold, modifier = Modifier.weight(1f))
            Text("ещё $daysLeft ${Exercises.plural(daysLeft, "день", "дня", "дней")}", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            quests.forEach { q ->
                val done = q.id in state.quests.done
                val progress = if (done) q.target else Quests.progress(state, q)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q.title, style = MaterialTheme.typography.bodyMedium, color = if (done) MastColors.TextMuted else MastColors.TextPrimary, modifier = Modifier.weight(1f))
                        if (done) Icon(MastIcons.Check, "Выполнено", tint = MastColors.Correct, modifier = Modifier.size(18.dp))
                        else Text("$progress/${q.target} · +${Quests.XP} XP", style = MaterialTheme.typography.labelMedium, color = MastColors.GoldLight)
                    }
                    Spacer(Modifier.height(4.dp))
                    XpBar(progress.toFloat() / q.target, height = 5.dp)
                }
            }
        }
    }
}

/** The exam block at the end of a chapter. */
@Composable
fun ExamPanel(chapter: Chapter, p: UserProgress, onStart: () -> Unit) {
    val ready = chapter.lessons.all { it.id in p.lessons }
    val record = p.exams[chapter.id]
    val need = (Challenges.EXAM_SIZE * 3 + 3) / 4
    PressablePanel(
        onStart,
        Modifier.fillMaxWidth().padding(top = 6.dp).graphicsLayer { alpha = if (ready) 1f else 0.5f },
        enabled = ready,
        gilded = ready,
        padding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Medal(Tier.GOLD, chapter.suit, 52.dp, locked = record?.passed != true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                GoldLabel("Экзамен главы", lines = false)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !ready -> "Откроется после всех уроков главы"
                        record?.passed == true -> "Сдан · лучший результат ${record.best} из ${record.total}"
                        record != null -> "Лучший результат ${record.best} из ${record.total} — нужно $need"
                        else -> "${Challenges.EXAM_SIZE} вопросов, для печати нужно $need верных"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (record?.passed == true) MastColors.GoldLight else MastColors.TextSecondary,
                )
            }
            if (ready) Icon(MastIcons.ChevronRight, null, tint = MastColors.TextMuted)
            else Icon(MastIcons.Lock, null, tint = MastColors.TextMuted)
        }
    }
}
