package app.mast.poker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mast.poker.achievements.Achievements
import app.mast.poker.content.Curriculum
import app.mast.poker.progress.Levels
import app.mast.poker.progress.ProgressReducer
import app.mast.poker.progress.UserProgress
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.Medal
import app.mast.poker.ui.components.ProgressRing
import app.mast.poker.ui.components.SecondaryButton
import app.mast.poker.ui.components.XpBar
import app.mast.poker.ui.components.emblemFor
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.Playfair
import java.time.LocalDate
import kotlin.math.roundToInt

private val weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
fun ProfileTab(nav: Nav) {
    val app = LocalApp.current
    val progress by app.progress.collectAsStateWithLifecycle()
    val p = progress ?: return
    val today = app.today()
    var confirmReset by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomBarSpace + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text("Профиль", style = MaterialTheme.typography.displaySmall, color = MastColors.TextPrimary, modifier = Modifier.statusBarsPadding().padding(top = 16.dp)) }
            item { LevelCard(p) }
            item { StatsGrid(p, today) }
            item { XpChart(p, today) }
            item { ConceptAccuracy(p) }
            item { AchievementsPreview(p) { nav.achievements() } }
            item {
                SettingsCard(
                    p,
                    onHaptics = { v -> app.updateSettings { it.copy(haptics = v) } },
                    onReduced = { v -> app.updateSettings { it.copy(reducedMotion = v) } },
                    onGoal = { app.setDailyGoal(it) },
                    onReset = { confirmReset = true },
                )
            }
            item { About() }
        }
        MastDialog(
            visible = confirmReset,
            title = "Сбросить прогресс?",
            text = "Удалятся пройденные уроки, XP, серия, статистика и ачивки. Отменить это нельзя.",
            confirm = "Сбросить",
            dismiss = "Отмена",
            onConfirm = { confirmReset = false; app.reset() },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun LevelCard(p: UserProgress) {
    GlassPanel(Modifier.fillMaxWidth(), gilded = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(Levels.fraction(p.xp), 92.dp, stroke = 7.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${p.level}", fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = MastColors.GoldLight)
                    Text("уровень", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(Levels.titleFor(p.level), style = MaterialTheme.typography.headlineMedium, color = MastColors.TextPrimary)
                Text("${p.xp} XP всего", style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
                Spacer(Modifier.height(10.dp))
                XpBar(Levels.fraction(p.xp))
                Spacer(Modifier.height(4.dp))
                val next = Levels.titles.firstOrNull { it.fromLevel > p.level }
                Text(
                    "До уровня ${p.level + 1}: ${Levels.xpForLevel(p.level + 1) - p.xp} XP" + (next?.let { " · дальше «${it.title}» с ${it.fromLevel}-го" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MastColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(p: UserProgress, today: Long) {
    val s = p.stats
    val accuracy = if (s.answersTotal == 0) "—" else "${(s.answersCorrect * 100f / s.answersTotal).roundToInt()}%"
    val cells = listOf(
        "${p.lessons.size}/${Curriculum.lessonCount}" to "уроков пройдено",
        accuracy to "точность ответов",
        "${ProgressReducer.visibleStreak(p, today)}" to "дней подряд (рекорд ${p.bestDayStreak})",
        "${s.bestAnswerStreak}" to "лучшая серия верных",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (value, label) ->
                    GlassPanel(Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                        Text(value, fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = MastColors.GoldLight)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun XpChart(p: UserProgress, today: Long) {
    val days = (13 downTo 0).map { today - it }
    val values = days.map { p.xpOn(it) }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(p.dailyGoalXp)
    val grow = remember { Animatable(0f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(Unit) { if (reduced) grow.snapTo(1f) else grow.animateTo(1f, Motion.enter(900)) }
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("XP за две недели", style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("цель ${p.dailyGoalXp}", style = MaterialTheme.typography.labelMedium, color = MastColors.TextMuted)
        }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val gap = size.width * 0.02f
            val barW = (size.width - gap * (days.size - 1)) / days.size
            val goalY = size.height * (1f - p.dailyGoalXp.toFloat() / max)
            drawLine(MastColors.Gold.copy(alpha = 0.35f), Offset(0f, goalY), Offset(size.width, goalY), 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            values.forEachIndexed { i, v ->
                val h = size.height * (v.toFloat() / max) * grow.value
                val x = i * (barW + gap)
                drawRoundRect(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 3))
                if (h > 0f) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(MastColors.GoldLight, MastColors.GoldDark), startY = size.height - h, endY = size.height),
                        Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 3),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Text(
                    if (i % 2 == 1 || i == days.lastIndex) weekdays[LocalDate.ofEpochDay(d).dayOfWeek.value - 1] else "",
                    style = MaterialTheme.typography.labelSmall, color = if (d == today) MastColors.GoldLight else MastColors.TextMuted,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ConceptAccuracy(p: UserProgress) {
    val rows = p.stats.perConcept.filter { it.value.total > 0 }.entries.sortedBy { it.value.accuracy }
    GlassPanel(Modifier.fillMaxWidth()) {
        Text("Точность по темам", style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        if (rows.isEmpty()) {
            Text("Здесь появится статистика после первых ответов.", style = MaterialTheme.typography.bodySmall, color = MastColors.TextMuted)
        }
        rows.forEach { (concept, stat) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(concept.ruName, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary, modifier = Modifier.weight(1f))
                Box(Modifier.width(110.dp)) { XpBar(stat.accuracy, height = 6.dp) }
                Text(
                    "${(stat.accuracy * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (stat.accuracy >= 0.8f) MastColors.Correct else if (stat.accuracy >= 0.6f) MastColors.GoldLight else MastColors.Wrong,
                    modifier = Modifier.width(48.dp), textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun AchievementsPreview(p: UserProgress, onOpen: () -> Unit) {
    val unlocked = p.achievements.entries.sortedByDescending { it.value }.mapNotNull { Achievements.byId(it.key) }
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Трофеи", style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("${unlocked.size} из ${Achievements.all.size}", style = MaterialTheme.typography.labelLarge, color = MastColors.GoldLight)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            unlocked.take(5).forEach { Medal(it.tier, emblemFor(it.id), 48.dp) }
            repeat((5 - unlocked.size).coerceAtLeast(0)) { Medal(app.mast.poker.achievements.Tier.BRONZE, app.mast.poker.core.poker.Suit.SPADES, 48.dp, locked = true) }
        }
        Spacer(Modifier.height(14.dp))
        SecondaryButton("Все трофеи", onOpen, Modifier.fillMaxWidth(), icon = MastIcons.Trophy)
    }
}

@Composable
private fun SettingsCard(p: UserProgress, onHaptics: (Boolean) -> Unit, onReduced: (Boolean) -> Unit, onGoal: (Int) -> Unit, onReset: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        GoldLabel("Настройки")
        Spacer(Modifier.height(8.dp))
        ToggleRow("Вибрация", "Отклик на верные и неверные ответы", p.settings.haptics, onHaptics)
        ToggleRow("Меньше анимаций", "Карты появляются без полёта и переворотов", p.settings.reducedMotion, onReduced)
        Spacer(Modifier.height(8.dp))
        Text("Дневная цель", style = MaterialTheme.typography.titleSmall, color = MastColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30 to "5 мин", 60 to "10 мин", 120 to "20 мин").forEach { (xp, label) ->
                val on = p.dailyGoalXp == xp
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) MastColors.Gold.copy(alpha = 0.16f) else MastColors.Glass)
                        .border(1.dp, if (on) MastColors.Gold else MastColors.GlassStroke, RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { onGoal(xp) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = if (on) MastColors.GoldLight else MastColors.TextPrimary)
                    Text("$xp XP", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Сбросить прогресс", onReset, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MastColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MastColors.FeltDeep,
                checkedTrackColor = MastColors.Gold,
                uncheckedThumbColor = MastColors.TextMuted,
                uncheckedTrackColor = MastColors.Glass,
                uncheckedBorderColor = MastColors.GlassStroke,
            ),
        )
    }
}

@Composable
private fun About() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Text(
        "Масть $version · обучающее приложение без игры на деньги и без рекламы.\nИгра на деньги — только 18+. Шрифт Playfair Display — SIL Open Font License.",
        style = MaterialTheme.typography.bodySmall,
        color = MastColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}
