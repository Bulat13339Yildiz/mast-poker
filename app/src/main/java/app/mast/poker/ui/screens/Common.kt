package app.mast.poker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.SecondaryButton
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion

@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MastColors.Glass)
            .border(1.dp, MastColors.GlassStroke, CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = MastColors.TextPrimary, modifier = Modifier.size(22.dp))
    }
}

/** Top bar with a back button and an optional title. */
@Composable
fun TopBar(title: String?, onBack: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = MastIcons.Back, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(icon, if (icon == MastIcons.Close) "Закрыть" else "Назад", onBack)
        Spacer(Modifier.width(12.dp))
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, color = MastColors.TextPrimary, modifier = Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Segmented progress of a lesson or session. */
@Composable
fun StepProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, Motion.uiSpring(), label = "steps")
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(MastColors.Glass)) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(MastColors.GoldDark, MastColors.Gold, MastColors.GoldLight))),
        )
    }
}

/** Centered modal with a scrim — used instead of a system dialog to keep the look. */
@Composable
fun MastDialog(
    visible: Boolean,
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MastColors.Scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(visible, enter = scaleIn(Motion.bouncy(), initialScale = 0.85f) + fadeIn(), exit = scaleOut() + fadeOut()) {
                GlassPanel(Modifier.padding(28.dp), tint = MastColors.FeltDark.copy(alpha = 0.96f), gilded = true) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(dismiss, onDismiss, Modifier.weight(1f))
                        PrimaryButton(confirm, onConfirm, Modifier.weight(1f), shimmer = false)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextMuted, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().padding(24.dp))
}

/** Small "chip" with an icon and a value: streak, xp… */
@Composable
fun StatChip(icon: ImageVector, value: @Composable () -> Unit, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MastColors.Gold) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MastColors.Glass)
            .border(1.dp, MastColors.GlassStroke, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp).offset(y = (-1).dp))
        Spacer(Modifier.width(6.dp))
        value()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary)
    }
}
