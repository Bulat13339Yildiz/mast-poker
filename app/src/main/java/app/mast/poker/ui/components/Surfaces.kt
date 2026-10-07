package app.mast.poker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion

val PanelShape = RoundedCornerShape(24.dp)

/** Frosted dark panel with a hairline border — the base container of the UI. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape,
    padding: PaddingValues = PaddingValues(20.dp),
    border: Color = MastColors.GlassStroke,
    tint: Color = MastColors.Glass,
    gilded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A near-solid tint marks a sheet or dialog: keep it opaque so nothing shows through.
    val bottom = if (tint.alpha >= 0.95f) lerp(tint, MastColors.FeltDeep, 0.6f).copy(alpha = 1f) else tint.copy(alpha = tint.alpha * 0.4f)
    val top = if (tint.alpha >= 0.95f) tint.copy(alpha = 1f) else tint
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .then(if (gilded) Modifier.gildedFrame() else Modifier.border(BorderStroke(1.dp, border), shape))
            .padding(padding),
        content = content,
    )
}

/** Panel that sinks a little under the finger. */
@Composable
fun PressablePanel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = PanelShape,
    padding: PaddingValues = PaddingValues(20.dp),
    border: Color = MastColors.GlassStroke,
    tint: Color = MastColors.Glass,
    gilded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.uiSpring(), label = "panelPress")
    GlassPanel(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick),
        shape = shape,
        padding = padding,
        border = border,
        tint = tint,
        gilded = gilded,
        content = content,
    )
}

@Composable
fun GoldRing(size: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, MastColors.Gold.copy(alpha = 0.7f), RoundedCornerShape(50))
            .background(MastColors.Glass)
            .padding(horizontal = size / 3, vertical = size / 6),
        content = content,
    )
}
