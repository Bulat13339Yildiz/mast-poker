package app.mast.poker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mast.poker.achievements.Tier
import app.mast.poker.progress.Quests
import app.mast.poker.core.poker.Suit
import app.mast.poker.progress.Levels
import app.mast.poker.progress.Reward
import app.mast.poker.ui.components.ChipBurst
import app.mast.poker.ui.components.CountUpNumber
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.Medal
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.ProgressRing
import app.mast.poker.ui.components.SecondaryButton
import app.mast.poker.ui.components.emblemFor
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.Playfair
import app.mast.poker.ui.theme.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** End of a lesson, drill or review: medal, score, XP and what was unlocked. */
@Composable
fun ResultScreen(
    title: String,
    subtitle: String,
    correct: Int,
    total: Int,
    reward: Reward,
    onContinue: () -> Unit,
    secondary: Pair<String, () -> Unit>? = null,
    scoreLabel: String? = null,
) {
    val accuracy = if (total == 0) 0f else correct.toFloat() / total
    val tier = when {
        accuracy >= 0.9f -> Tier.GOLD
        accuracy >= 0.7f -> Tier.SILVER
        else -> Tier.BRONZE
    }
    val reduced = LocalReducedMotion.current
    val spin = remember { Animatable(if (reduced) 0f else 540f) }
    val scale = remember { Animatable(if (reduced) 1f else 0.3f) }
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) {
        haptics.success()
        if (!reduced) {
            launch { scale.animateTo(1f, Motion.bouncy()) }
            spin.animateTo(0f, tween(1100, easing = Motion.EmphasizedDecelerate))
        }
    }

    Box(Modifier.fillMaxSize()) {
        ChipBurst(key = reward, origin = androidx.compose.ui.geometry.Offset(0.5f, 0.2f))
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Medal(
                tier, Suit.SPADES, 132.dp,
                Modifier.graphicsLayer {
                    rotationY = spin.value
                    scaleX = scale.value
                    scaleY = scale.value
                    cameraDistance = 14f * density
                },
            )
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, color = MastColors.TextPrimary, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassPanel(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(accuracy, 64.dp) {
                            Text("${(accuracy * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall, color = MastColors.GoldLight)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(scoreLabel ?: "$correct из $total верно", style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary, textAlign = TextAlign.Center)
                    }
                }
                GlassPanel(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CountUpNumber(reward.xp, prefix = "+", style = MaterialTheme.typography.displaySmall.copy(fontFamily = Playfair, fontWeight = FontWeight.Bold), color = MastColors.GoldLight)
                        Text("XP", style = MaterialTheme.typography.labelLarge, color = MastColors.Gold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(MastIcons.Flame, null, tint = MastColors.Gold, modifier = Modifier.padding(end = 4.dp))
                            Text("серия ${reward.dayStreak} дн.", style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                        }
                    }
                }
            }
            if (reward.goalReached) {
                Spacer(Modifier.height(12.dp))
                GlassPanel(Modifier.fillMaxWidth(), gilded = true) {
                    Text("Дневная цель выполнена!", style = MaterialTheme.typography.titleMedium, color = MastColors.GoldLight, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
            if (reward.leveledUp) {
                Spacer(Modifier.height(12.dp))
                GlassPanel(Modifier.fillMaxWidth(), gilded = true) {
                    Text(
                        "Новый уровень ${reward.levelAfter} — ${Levels.titleFor(reward.levelAfter)}",
                        style = MaterialTheme.typography.titleMedium, color = MastColors.GoldLight,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                }
            }
            reward.quests.forEach { q ->
                Spacer(Modifier.height(12.dp))
                GlassPanel(Modifier.fillMaxWidth(), gilded = true) {
                    Text("Задание недели выполнено", style = MaterialTheme.typography.labelLarge, color = MastColors.Gold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text("${q.title} · +${Quests.XP} XP", style = MaterialTheme.typography.titleMedium, color = MastColors.GoldLight, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
            if (reward.achievements.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                GoldLabel("Новые ачивки")
                Spacer(Modifier.height(10.dp))
                reward.achievements.forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Medal(a.tier, emblemFor(a.id), 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(a.title, style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                            Text(a.description, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Дальше", onContinue, Modifier.fillMaxWidth())
            secondary?.let { (label, action) ->
                Spacer(Modifier.height(10.dp))
                SecondaryButton(label, action, Modifier.fillMaxWidth())
            }
        }
    }
}
