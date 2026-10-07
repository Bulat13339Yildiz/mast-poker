package app.mast.poker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion

/** Horizontal gold bar that springs to [fraction] and carries a travelling highlight. */
@Composable
fun XpBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 10.dp, track: Color = Color.White.copy(alpha = 0.08f)) {
    val anim = remember { Animatable(0f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(fraction) {
        if (reduced) anim.snapTo(fraction) else anim.animateTo(fraction, spring(dampingRatio = 0.8f, stiffness = 120f))
    }
    val shine = if (!reduced) {
        rememberInfiniteTransition(label = "xpShine")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "x").value
    } else -1f
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * anim.value.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(MastColors.GoldDark, MastColors.Gold, MastColors.GoldLight), endX = w),
                size = Size(w, size.height),
                cornerRadius = r,
            )
            if (shine >= 0f) {
                val x = w * shine
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                        startX = x - 40f, endX = x + 40f,
                    ),
                    size = Size(w, size.height),
                    cornerRadius = r,
                )
            }
        }
    }
}

/** Circular progress ring for the daily goal. */
@Composable
fun ProgressRing(
    fraction: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    stroke: Dp = 6.dp,
    color: Color = MastColors.Gold,
    content: @Composable () -> Unit = {},
) {
    val anim = remember { Animatable(0f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(fraction) {
        if (reduced) anim.snapTo(fraction) else anim.animateTo(fraction, tween(900, easing = Motion.EmphasizedDecelerate))
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            val sweep = 360f * anim.value.coerceIn(0f, 1f)
            drawArc(
                Brush.sweepGradient(listOf(MastColors.GoldDark, color, MastColors.GoldLight, MastColors.GoldDark)),
                -90f, sweep, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Number that rolls digit-by-digit when it changes. */
@Composable
fun RollingNumber(value: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Row(modifier) {
        value.toString().forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(Motion.cardSpring()) { if (up) it else -it } + fadeIn(tween(Motion.Short)))
                        .togetherWith(slideOutVertically(Motion.cardSpring()) { if (up) -it else it } + fadeOut(tween(Motion.Short)))
                },
                label = "digit$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}

/** Counts from 0 up to [target] over [durationMs] — for result screens. */
@Composable
fun CountUpNumber(target: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, durationMs: Int = 900, prefix: String = "") {
    var shown by remember { mutableIntStateOf(0) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(target) {
        if (reduced || target == 0) { shown = target; return@LaunchedEffect }
        val anim = Animatable(0f)
        anim.animateTo(target.toFloat(), tween(durationMs, easing = Motion.EmphasizedDecelerate)) { shown = value.toInt() }
        shown = target
    }
    Text("$prefix$shown", style = style, color = color, modifier = modifier)
}
