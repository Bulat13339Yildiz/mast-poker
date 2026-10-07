package app.mast.poker.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mast.poker.content.Curriculum
import app.mast.poker.content.Lesson
import app.mast.poker.progress.LessonRecord
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.ChapterArt
import app.mast.poker.ui.components.DecoBand
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.PressablePanel
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors

private enum class NodeState { Done, Current, Locked }

@Composable
fun ChapterScreen(chapterId: String, nav: Nav) {
    val app = LocalApp.current
    val progress by app.progress.collectAsStateWithLifecycle()
    val p = progress ?: return
    val chapter = Curriculum.chapter(chapterId)
    val completed = p.lessons.keys

    Column(Modifier.fillMaxSize()) {
        TopBar(null, nav::back)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.sharedElementOrSelf("chapter-art-${chapter.id}")) { ChapterArt(chapter.id, 150.dp) }
                    Spacer(Modifier.height(12.dp))
                    GoldLabel("Глава ${chapter.number}")
                    Spacer(Modifier.height(8.dp))
                    Text(chapter.title, style = MaterialTheme.typography.displaySmall, color = MastColors.TextPrimary, modifier = Modifier.sharedBoundsOrSelf("chapter-title-${chapter.id}"))
                    Text(chapter.subtitle, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    DecoBand()
                    Spacer(Modifier.height(18.dp))
                }
            }
            itemsIndexed(chapter.lessons, key = { _, l -> l.id }) { i, lesson ->
                val state = when {
                    lesson.id in completed -> NodeState.Done
                    Curriculum.isUnlocked(lesson.id, completed) -> NodeState.Current
                    else -> NodeState.Locked
                }
                LessonRow(i + 1, lesson, state, p.lessons[lesson.id], isLast = i == chapter.lessons.lastIndex) {
                    if (state != NodeState.Locked) nav.lesson(lesson.id)
                }
            }
            item { ExamPanel(chapter, p) { nav.exam(chapter.id) } }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun LessonRow(number: Int, lesson: Lesson, state: NodeState, record: LessonRecord?, isLast: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(52.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!isLast) {
                Canvas(Modifier.fillMaxHeight().width(2.dp).padding(top = 44.dp)) {
                    drawLine(
                        if (state == NodeState.Done) MastColors.Gold.copy(alpha = 0.7f) else MastColors.GlassStroke,
                        Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), size.width,
                    )
                }
            }
            Node(number, state, Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(12.dp))
        PressablePanel(
            onClick,
            Modifier.weight(1f).padding(bottom = 14.dp).graphicsLayer { alpha = if (state == NodeState.Locked) 0.5f else 1f },
            enabled = state != NodeState.Locked,
            border = if (state == NodeState.Current) MastColors.Gold.copy(alpha = 0.7f) else MastColors.GlassStroke,
            padding = PaddingValues(16.dp),
        ) {
            Text(lesson.title, style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
            Text(lesson.subtitle, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${lesson.minutes} мин", style = MaterialTheme.typography.labelMedium, color = MastColors.TextMuted)
                Text("${lesson.questionCount} вопр.", style = MaterialTheme.typography.labelMedium, color = MastColors.TextMuted)
                if (record != null) {
                    Text(
                        if (record.perfect) "Без ошибок" else "${record.correct} из ${record.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (record.perfect) MastColors.Gold else MastColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Node(number: Int, state: NodeState, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val pulse = if (state == NodeState.Current && !reduced) {
        rememberInfiniteTransition(label = "node").animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p").value
    } else 0f
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (state == NodeState.Current) {
            Canvas(Modifier.size(40.dp)) {
                drawCircle(MastColors.Gold.copy(alpha = (1f - pulse) * 0.6f), radius = size.minDimension / 2 * (0.8f + pulse * 0.45f), style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        NodeState.Done -> Brush.verticalGradient(listOf(MastColors.GoldLight, MastColors.Gold))
                        NodeState.Current -> Brush.verticalGradient(listOf(MastColors.FeltLight, MastColors.Felt))
                        NodeState.Locked -> Brush.verticalGradient(listOf(MastColors.FeltDark, MastColors.FeltDeep))
                    },
                )
                .border(1.dp, if (state == NodeState.Locked) MastColors.GlassStroke else MastColors.Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                NodeState.Done -> Icon(MastIcons.Check, "Пройден", tint = MastColors.FeltDeep, modifier = Modifier.size(18.dp))
                NodeState.Current -> Text("$number", style = MaterialTheme.typography.titleSmall, color = MastColors.GoldLight)
                NodeState.Locked -> Icon(MastIcons.Lock, "Закрыт", tint = MastColors.TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}
