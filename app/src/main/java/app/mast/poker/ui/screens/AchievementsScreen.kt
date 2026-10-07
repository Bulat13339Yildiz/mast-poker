package app.mast.poker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mast.poker.achievements.Achievement
import app.mast.poker.achievements.Achievements
import app.mast.poker.progress.UserProgress
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.Medal
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.XpBar
import app.mast.poker.ui.components.emblemFor
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AchievementsScreen(nav: Nav) {
    val app = LocalApp.current
    val progress by app.progress.collectAsStateWithLifecycle()
    val p = progress ?: return
    var open by remember { mutableStateOf<Achievement?>(null) }
    val sorted = remember(p.achievements) {
        Achievements.all.sortedWith(compareByDescending<Achievement> { it.id in p.achievements }.thenBy { it.hidden })
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Трофеи", nav::back)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(3) }) {
                    Text(
                        "Открыто ${p.achievements.size} из ${Achievements.all.size}",
                        style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(sorted, key = { it.id }) { a -> AchievementCell(a, p) { open = a } }
            }
        }
        AchievementDetail(open, p) { open = null }
    }
}

@Composable
private fun AchievementCell(a: Achievement, p: UserProgress, onClick: () -> Unit) {
    val unlocked = a.id in p.achievements
    val secret = a.hidden && !unlocked
    Column(
        Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Medal(a.tier, emblemFor(a.id), 76.dp, locked = !unlocked)
        Spacer(Modifier.height(6.dp))
        Text(
            if (secret) "???" else a.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (unlocked) MastColors.TextPrimary else MastColors.TextMuted,
            textAlign = TextAlign.Center, maxLines = 2,
        )
        val prog = a.progress?.invoke(p)
        if (!unlocked && !secret && prog != null && prog.second > 1) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(64.dp)) { XpBar(prog.first.toFloat() / prog.second, height = 4.dp) }
            Text("${prog.first}/${prog.second}", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
        }
    }
}

@Composable
private fun AchievementDetail(a: Achievement?, p: UserProgress, onClose: () -> Unit) {
    AnimatedVisibility(a != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(MastColors.Scrim).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(a != null, enter = scaleIn(Motion.bouncy(), 0.8f) + fadeIn(), exit = scaleOut() + fadeOut()) {
                val item = a ?: return@AnimatedVisibility
                val unlocked = item.id in p.achievements
                val secret = item.hidden && !unlocked
                val spin = remember(item) { Animatable(if (unlocked) 360f else 0f) }
                val reduced = LocalReducedMotion.current
                LaunchedEffect(item) { if (unlocked && !reduced) spin.animateTo(0f, tween(900, easing = Motion.EmphasizedDecelerate)) else spin.snapTo(0f) }
                GlassPanel(Modifier.padding(28.dp), tint = MastColors.FeltDark.copy(alpha = 0.97f), gilded = true) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Medal(item.tier, emblemFor(item.id), 120.dp, Modifier.graphicsLayer { rotationY = spin.value; cameraDistance = 14f * density }, locked = !unlocked)
                        Spacer(Modifier.height(14.dp))
                        GoldLabel(item.tier.ruName)
                        Spacer(Modifier.height(8.dp))
                        Text(if (secret) "Скрытая ачивка" else item.title, style = MaterialTheme.typography.headlineMedium, color = MastColors.TextPrimary, textAlign = TextAlign.Center)
                        Text(
                            if (secret) "Условие откроется, когда ты её получишь." else item.description,
                            style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary, textAlign = TextAlign.Center,
                        )
                        p.achievements[item.id]?.let { millis ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Получена " + DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())),
                                style = MaterialTheme.typography.labelMedium, color = MastColors.Gold,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton("Закрыть", onClose, Modifier.fillMaxWidth(), shimmer = false)
                    }
                }
            }
        }
    }
}
