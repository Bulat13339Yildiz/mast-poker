package app.mast.poker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import app.mast.poker.core.poker.Suit
import app.mast.poker.progress.DrillMode
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.AllInArt
import app.mast.poker.ui.components.BluffArt
import app.mast.poker.ui.components.CasinoChip
import app.mast.poker.ui.components.DealerButton
import app.mast.poker.ui.components.FannedCards
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.GreenChip
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.MiniRange
import app.mast.poker.ui.components.PressablePanel
import app.mast.poker.ui.components.SizingArt
import app.mast.poker.ui.components.SuitBadge
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion

@Composable
fun DrillArt(type: DrillType, size: Dp) {
    when (type) {
        DrillType.NAME_HAND -> FannedCards("Kh Ks", size)
        DrillType.WINNER -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { Icon(MastIcons.Trophy, null, tint = MastColors.Gold, modifier = Modifier.size(size * 0.62f)) }
        DrillType.BEST_FIVE -> FannedCards("Ad As", size)
        DrillType.OUTS -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { SuitBadge(Suit.HEARTS, size * 0.72f) }
        DrillType.POT_ODDS -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { CasinoChip(size * 0.85f) }
        DrillType.PREFLOP -> FannedCards("Qc Js", size)
        DrillType.EQUITY -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { CasinoChip(size * 0.85f, colors = GreenChip) }
        DrillType.HAND_SIM -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { DealerButton(size * 0.6f) }
        DrillType.RANGES -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { MiniRange(size * 0.86f) }
        DrillType.COMBOS -> FannedCards("Jh Jd", size)
        DrillType.BLUFF_MATH -> BluffArt(size)
        DrillType.SIZING -> SizingArt(size)
        DrillType.PUSH_FOLD -> AllInArt(size)
    }
}

@Composable
fun PracticeTab(nav: Nav, onPickDrill: (DrillType) -> Unit) {
    val app = LocalApp.current
    val progress by app.progress.collectAsStateWithLifecycle()
    val p = progress ?: return
    val completed = p.lessons.keys
    val due = p.dueMistakes(app.today())

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomBarSpace + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                    Text("Тренировка", style = MaterialTheme.typography.displaySmall, color = MastColors.TextPrimary)
                    Text("Бесконечные раздачи: каждый раз новые карты", style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
                }
            }
            item(span = { GridItemSpan(2) }) {
                PressablePanel(
                    { nav.review() },
                    Modifier.fillMaxWidth(),
                    border = if (due.isNotEmpty()) MastColors.Wrong.copy(alpha = 0.5f) else MastColors.GlassStroke,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MastIcons.Replay, null, tint = if (due.isNotEmpty()) MastColors.Wrong else MastColors.TextMuted, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Работа над ошибками", style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                            Text(
                                if (due.isEmpty()) "Сейчас повторять нечего" else "Тем к повторению: ${due.size}",
                                style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary,
                            )
                        }
                    }
                }
            }
            item(span = { GridItemSpan(2) }) { GoldLabel("Тренажёры", Modifier.padding(top = 6.dp)) }
            items(DrillType.entries, key = { it.name }) { type ->
                val unlocked = Curriculum.drillUnlocked(type, completed)
                DrillTile(type, unlocked, p.stats.blitzBest[type], onClick = { if (unlocked) onPickDrill(type) })
            }
        }
    }
}

/** Scrim + mode chooser; drawn by the main screen above the bottom bar. */
@Composable
fun ModeChooser(chosen: DrillType?, onDismiss: () -> Unit, onPick: (DrillType, DrillMode) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(chosen != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MastColors.Scrim)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        // Keep the last type while the sheet animates out.
        var last by remember { mutableStateOf(chosen) }
        if (chosen != null) last = chosen
        AnimatedVisibility(
            chosen != null,
            enter = slideInVertically(Motion.cardSpring()) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            last?.let { type -> ModeSheet(type, onPick = { mode -> onPick(type, mode) }) }
        }
    }
}

@Composable
private fun DrillTile(type: DrillType, unlocked: Boolean, best: Int?, onClick: () -> Unit) {
    val lockLesson = Curriculum.lessonUnlockingDrill(type)
    PressablePanel(
        onClick,
        Modifier.fillMaxWidth().heightIn(min = 196.dp).graphicsLayer { alpha = if (unlocked) 1f else 0.55f },
        enabled = unlocked,
        gilded = unlocked,
        padding = PaddingValues(14.dp),
    ) {
        DrillArt(type, 64.dp)
        Spacer(Modifier.height(10.dp))
        Text(type.title, style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
        Text(type.subtitle, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        if (!unlocked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MastIcons.Lock, null, tint = MastColors.TextMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("после урока «${lockLesson?.title}»", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
            }
        } else if (best != null && best > 0) {
            Text("Рекорд блица: $best", style = MaterialTheme.typography.labelMedium, color = MastColors.Gold)
        }
    }
}

@Composable
private fun ModeSheet(type: DrillType, onPick: (DrillMode) -> Unit) {
    GlassPanel(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        tint = MastColors.FeltDark.copy(alpha = 0.98f),
        gilded = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrillArt(type, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(type.title, style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary)
                Text(type.subtitle, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        ModeOption("Практика", if (type == DrillType.HAND_SIM) "5 раздач с разбором каждого решения" else "10 заданий с подробным разбором", MastIcons.Learn) { onPick(DrillMode.PRACTICE) }
        if (type.hasBlitz) {
            Spacer(Modifier.height(10.dp))
            ModeOption("Блиц", "60 секунд — сколько успеешь", MastIcons.Timer) { onPick(DrillMode.BLITZ) }
        }
    }
}

@Composable
private fun ModeOption(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    PressablePanel(onClick, Modifier.fillMaxWidth(), padding = PaddingValues(16.dp), tint = MastColors.Glass) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MastColors.Gold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MastColors.GoldLight)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
            }
            Icon(MastIcons.ChevronRight, null, tint = MastColors.TextMuted)
        }
    }
}
