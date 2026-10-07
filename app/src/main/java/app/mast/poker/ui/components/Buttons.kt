package app.mast.poker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion

private val ButtonShape = RoundedCornerShape(18.dp)

enum class ButtonTone { Gold, Correct, Wrong }

/**
 * Main call-to-action: a brass gradient bar with a slow light sweep. Sinks on
 * press and greys out when disabled.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.Gold,
    icon: ImageVector? = null,
    shimmer: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.uiSpring(), label = "btn")
    val (top, bottom) = when (tone) {
        ButtonTone.Gold -> MastColors.GoldLight to MastColors.Gold
        ButtonTone.Correct -> Color(0xFF6BF0B6) to MastColors.Correct
        ButtonTone.Wrong -> Color(0xFFFF9488) to MastColors.Wrong
    }
    val topC by animateColorAsState(if (enabled) top else Color(0xFF3B4A44), label = "top")
    val bottomC by animateColorAsState(if (enabled) bottom else Color(0xFF2C3833), label = "bottom")
    val reduced = LocalReducedMotion.current
    val sweep = if (shimmer && enabled && !reduced) {
        val t = rememberInfiniteTransition(label = "sweep")
        t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(2600, delayMillis = 1400, easing = LinearEasing), RepeatMode.Restart), label = "sweepX").value
    } else -1f

    Box(
        modifier
            .defaultMinSize(minHeight = 58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(ButtonShape)
            .background(Brush.verticalGradient(listOf(topC, bottomC)))
            // The light sweep runs under the label so the text never washes out.
            .drawBehind {
                if (sweep > -0.5f) {
                    val x = size.width * sweep
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                            start = Offset(x - size.width * 0.25f, 0f),
                            end = Offset(x + size.width * 0.25f, size.height),
                        ),
                    )
                }
            }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val fg = if (enabled) MastColors.FeltDeep else MastColors.TextMuted
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.padding(end = 8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = fg)
        }
    }
}

/** Quiet outlined button for secondary actions. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.uiSpring(), label = "btn2")
    Box(
        modifier
            .defaultMinSize(minHeight = 52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .clip(ButtonShape)
            .background(MastColors.Glass)
            .border(1.dp, MastColors.Gold.copy(alpha = 0.45f), ButtonShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MastColors.GoldLight)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = MastColors.GoldLight)
        }
    }
}

/** Answer option tile used in quizzes and drills. */
enum class OptionState { Idle, Selected, Correct, Wrong, Faded }

@Composable
fun OptionTile(
    text: String,
    state: OptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centered: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.97f
            state == OptionState.Correct -> 1.02f
            else -> 1f
        },
        Motion.bouncy(), label = "opt",
    )
    val border by animateColorAsState(
        when (state) {
            OptionState.Idle -> MastColors.GlassStroke
            OptionState.Selected -> MastColors.Gold
            OptionState.Correct -> MastColors.Correct
            OptionState.Wrong -> MastColors.Wrong
            OptionState.Faded -> Color.Transparent
        },
        label = "optBorder",
    )
    val fill by animateColorAsState(
        when (state) {
            OptionState.Selected -> MastColors.Gold.copy(alpha = 0.14f)
            OptionState.Correct -> MastColors.Correct.copy(alpha = 0.16f)
            OptionState.Wrong -> MastColors.Wrong.copy(alpha = 0.16f)
            else -> MastColors.Glass
        },
        label = "optFill",
    )
    val textColor = when (state) {
        OptionState.Faded -> MastColors.TextMuted
        OptionState.Correct -> MastColors.Correct
        OptionState.Wrong -> MastColors.Wrong
        OptionState.Selected -> MastColors.GoldLight
        OptionState.Idle -> MastColors.TextPrimary
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (centered) 8.dp else 18.dp, vertical = 12.dp),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        RichText(text, style = MaterialTheme.typography.titleMedium, color = textColor, textAlign = if (centered) TextAlign.Center else null)
    }
}
