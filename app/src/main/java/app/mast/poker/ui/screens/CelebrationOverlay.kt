package app.mast.poker.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mast.poker.achievements.Tier
import app.mast.poker.core.poker.Suit
import app.mast.poker.progress.Levels
import app.mast.poker.ui.AppViewModel
import app.mast.poker.ui.Celebration
import app.mast.poker.ui.components.ChipBurst
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.Medal
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.emblemFor
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.Playfair
import app.mast.poker.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/** Full-screen moment for each unlocked achievement or level-up, one after another. */
@Composable
fun CelebrationOverlay(app: AppViewModel) {
    val current = app.celebrations.firstOrNull()
    // Let the result screen play its own moment (medal, XP count-up) before the overlay.
    var ready by remember { mutableStateOf(false) }
    val hasAny = current != null
    LaunchedEffect(hasAny) {
        if (hasAny) {
            delay(1300)
            ready = true
        } else {
            ready = false
        }
    }
    AnimatedVisibility(current != null && ready, enter = fadeIn(tween(Motion.Medium)), exit = fadeOut(tween(Motion.Medium))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MastColors.Scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null) { app.dismissCelebration() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { (scaleIn(Motion.bouncy(), 0.7f) + fadeIn()).togetherWith(fadeOut(tween(Motion.Short))) },
                label = "celebration",
            ) { c ->
                if (c != null) CelebrationCard(c) { app.dismissCelebration() }
            }
        }
    }
}

@Composable
private fun CelebrationCard(c: Celebration, onClose: () -> Unit) {
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    val spin = remember(c) { Animatable(if (reduced) 0f else 900f) }
    val drop = remember(c) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(c) {
        haptics.heavy()
        if (!reduced) {
            launch { drop.animateTo(1f, Motion.bouncy()) }
            spin.animateTo(0f, tween(1300, easing = Motion.EmphasizedDecelerate))
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ChipBurst(key = c, origin = androidx.compose.ui.geometry.Offset(0.5f, 0.38f), count = 44)
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val medalModifier = Modifier.graphicsLayer {
                rotationY = spin.value
                scaleX = 0.4f + 0.6f * drop.value
                scaleY = 0.4f + 0.6f * drop.value
                translationY = (1f - drop.value) * -200f
                cameraDistance = 16f * density
            }
            when (c) {
                is Celebration.NewAchievement -> {
                    Medal(c.achievement.tier, emblemFor(c.achievement.id), 168.dp, medalModifier)
                    Spacer(Modifier.height(22.dp))
                    GoldLabel("Новая ачивка · ${c.achievement.tier.ruName}")
                    Spacer(Modifier.height(10.dp))
                    Text(c.achievement.title, style = MaterialTheme.typography.displaySmall, color = MastColors.TextPrimary, textAlign = TextAlign.Center)
                    Text(c.achievement.description, style = MaterialTheme.typography.bodyLarge, color = MastColors.TextSecondary, textAlign = TextAlign.Center)
                }
                is Celebration.LevelUp -> {
                    Box(contentAlignment = Alignment.Center) {
                        Medal(Tier.GOLD, Suit.SPADES, 168.dp, medalModifier)
                    }
                    Spacer(Modifier.height(22.dp))
                    GoldLabel("Новый уровень")
                    Spacer(Modifier.height(10.dp))
                    Text("${c.level}", fontFamily = Playfair, fontWeight = FontWeight.Black, fontSize = 64.sp, color = MastColors.GoldLight)
                    Text(Levels.titleFor(c.level), style = MaterialTheme.typography.headlineMedium, color = MastColors.TextPrimary)
                }
            }
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Забрать", onClose, Modifier.fillMaxWidth())
        }
    }
}
