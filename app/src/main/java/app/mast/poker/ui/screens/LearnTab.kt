package app.mast.poker.ui.screens

import app.mast.poker.achievements.Tier
import app.mast.poker.ui.components.Medal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mast.poker.content.Chapter
import app.mast.poker.content.Concept
import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import app.mast.poker.content.Reference
import app.mast.poker.progress.DrillMode
import app.mast.poker.progress.Levels
import app.mast.poker.progress.ProgressReducer
import app.mast.poker.progress.UserProgress
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.ChapterArt
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.PressablePanel
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.ProgressRing
import app.mast.poker.ui.components.RollingNumber
import app.mast.poker.ui.components.SecondaryButton
import app.mast.poker.ui.components.XpBar
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Playfair
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun LearnTab(nav: Nav, onOpenPractice: () -> Unit) {
    val app = LocalApp.current
    val progress by app.progress.collectAsStateWithLifecycle()
    val p = progress ?: return
    val today = app.today()
    val completed = p.lessons.keys
    val next = Curriculum.nextLesson(completed)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomBarSpace + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(p, today, Modifier.statusBarsPadding().padding(top = 12.dp)) }
        item {
            if (next != null) ContinueCard(next.id, onStart = { nav.lesson(next.id) })
            else AllDoneCard(onOpenPractice)
        }
        val due = p.dueMistakes(today)
        if (due.isNotEmpty()) item { ReviewCard(due.size) { nav.review() } }
        item { PuzzleCard(p, today, onOpen = nav::puzzle) }
        item { QuestsCard(p, today) }
        item { TipCard(p, today, nav) }
        Curriculum.chapters.groupBy { it.level }.forEach { (level, chapters) ->
            item(key = "level-$level") {
                GoldLabel(if (level == 1) "Уровень 1 · Основы" else "Уровень 2 · Глубже в игру", Modifier.padding(top = 8.dp))
            }
            items(chapters, key = { it.id }) { chapter -> ChapterTile(chapter, completed, p.exams[chapter.id]?.passed == true) { nav.chapter(chapter.id) } }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}

@Composable
private fun Header(p: UserProgress, today: Long, modifier: Modifier = Modifier) {
    val streak = ProgressReducer.visibleStreak(p, today)
    val todayXp = p.xpOn(today)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
            Text(
                "${Levels.titleFor(p.level)} · ур. ${p.level}",
                style = MaterialTheme.typography.headlineMedium,
                color = MastColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            XpBar(Levels.fraction(p.xp))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingNumber(p.xp, MaterialTheme.typography.labelLarge, color = MastColors.GoldLight)
                Text(" / ${Levels.xpForLevel(p.level + 1)} XP", style = MaterialTheme.typography.labelLarge, color = MastColors.TextMuted)
                Spacer(Modifier.weight(1f))
                StatChip(MastIcons.Flame, {
                    RollingNumber(streak, MaterialTheme.typography.labelLarge, color = MastColors.TextPrimary)
                }, tint = if (streak > 0) MastColors.Gold else MastColors.TextMuted)
            }
        }
        Spacer(Modifier.width(16.dp))
        ProgressRing((todayXp.toFloat() / p.dailyGoalXp).coerceIn(0f, 1f), 76.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$todayXp", fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MastColors.GoldLight)
                Text("из ${p.dailyGoalXp}", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
            }
        }
    }
}

@Composable
private fun ContinueCard(lessonId: String, onStart: () -> Unit) {
    val lesson = Curriculum.lesson(lessonId)
    val chapter = Curriculum.chapterOf(lessonId)
    val number = chapter.lessons.indexOf(lesson) + 1
    GlassPanel(Modifier.fillMaxWidth(), gilded = true, tint = MastColors.Gold.copy(alpha = 0.08f)) {
        GoldLabel("Следующий урок")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChapterArt(chapter.id, 84.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Глава ${chapter.number} · урок $number", style = MaterialTheme.typography.labelMedium, color = MastColors.Gold)
                Text(lesson.title, style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary)
                Text("${lesson.subtitle} · ${lesson.minutes} мин", style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (lessonId == Curriculum.lessons.first().id) "Начать обучение" else "Продолжить", onStart, Modifier.fillMaxWidth(), icon = MastIcons.Play)
    }
}

@Composable
private fun AllDoneCard(onOpenPractice: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), gilded = true) {
        GoldLabel("Курс пройден")
        Spacer(Modifier.height(10.dp))
        Text("Все ${Curriculum.lessonCount} уроков позади!", style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary)
        Text("Закрепляй навыки в тренажёрах — особенно в «Сыграй раздачу».", style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("К тренировкам", onOpenPractice, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReviewCard(count: Int, onClick: () -> Unit) {
    PressablePanel(onClick, Modifier.fillMaxWidth(), border = MastColors.Wrong.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MastIcons.Replay, null, tint = MastColors.Wrong, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Работа над ошибками", style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                Text("Тем к повторению: $count", style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
            }
            Icon(MastIcons.ChevronRight, null, tint = MastColors.TextMuted)
        }
    }
}

@Composable
private fun TipCard(p: UserProgress, today: Long, nav: Nav) {
    val completed = p.lessons.keys
    val weak = p.stats.perConcept
        .filter { (_, s) -> s.total >= 5 && s.accuracy < 0.75f }
        .minByOrNull { it.value.accuracy }
    GlassPanel(Modifier.fillMaxWidth()) {
        Text("СОВЕТ ДНЯ", style = MaterialTheme.typography.labelSmall, color = MastColors.Gold)
        Spacer(Modifier.height(8.dp))
        if (weak != null) {
            val (concept, stat) = weak
            Text(
                "Подтяни тему «${concept.ruName}» — точность ${(stat.accuracy * 100).roundToInt()}%.",
                style = MaterialTheme.typography.bodyLarge,
                color = MastColors.TextPrimary,
            )
            val drill = drillFor(concept)?.takeIf { Curriculum.drillUnlocked(it, completed) }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                if (drill != null) "Потренировать: ${drill.title}" else "Повторить ошибки",
                { if (drill != null) nav.drill(drill, DrillMode.PRACTICE) else nav.review() },
                Modifier.fillMaxWidth(),
            )
        } else {
            Text(Reference.tips[(today % Reference.tips.size).toInt()], style = MaterialTheme.typography.bodyLarge, color = MastColors.TextPrimary)
        }
    }
}

private fun drillFor(concept: Concept): DrillType? =
    DrillType.entries.firstOrNull { it.concept == concept && it != DrillType.HAND_SIM }

@Composable
private fun ChapterTile(chapter: Chapter, completed: Set<String>, sealed: Boolean, onClick: () -> Unit) {
    val done = chapter.lessons.count { it.id in completed }
    val unlocked = Curriculum.isUnlocked(chapter.lessons.first().id, completed)
    PressablePanel(
        onClick,
        Modifier.fillMaxWidth().graphicsLayer { alpha = if (unlocked) 1f else 0.55f },
        gilded = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.sharedElementOrSelf("chapter-art-${chapter.id}")) { ChapterArt(chapter.id, 88.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoldLabel("Глава ${chapter.number}", lines = false)
                    if (!unlocked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(MastIcons.Lock, "Закрыто", tint = MastColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                    if (sealed) {
                        Spacer(Modifier.weight(1f))
                        Medal(Tier.GOLD, chapter.suit, 30.dp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(chapter.title, style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary, modifier = Modifier.sharedBoundsOrSelf("chapter-title-${chapter.id}"))
                Text(chapter.subtitle, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                Spacer(Modifier.height(10.dp))
                XpBar(done.toFloat() / chapter.lessons.size, height = 6.dp)
                Spacer(Modifier.height(4.dp))
                Text("$done из ${chapter.lessons.size} уроков", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
            }
        }
    }
}
