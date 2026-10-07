package app.mast.poker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import app.mast.poker.core.poker.Suit
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val size: Float,
    val kind: Int,
    val color: Color,
    val suit: Suit,
)

private val chipColors = listOf(MastColors.Gold, MastColors.GoldLight, MastColors.Ivory, MastColors.SuitRed, Color(0xFF2F6FD6))

/**
 * Burst of poker chips and suit glyphs from [origin] (fractions of the canvas).
 * Pure Canvas — replays whenever [key] changes.
 */
@Composable
fun ChipBurst(key: Any, modifier: Modifier = Modifier, origin: Offset = Offset(0.5f, 0.42f), count: Int = 36, durationMs: Int = 1500) {
    if (LocalReducedMotion.current) return
    val particles = remember(key) {
        val rnd = Random(key.hashCode())
        List(count) {
            Particle(
                angle = (rnd.nextFloat() * 2 * PI).toFloat(),
                speed = 0.55f + rnd.nextFloat() * 0.75f,
                spin = (rnd.nextFloat() - 0.5f) * 720f,
                size = 0.022f + rnd.nextFloat() * 0.022f,
                kind = rnd.nextInt(3),
                color = chipColors[rnd.nextInt(chipColors.size)],
                suit = Suit.entries[rnd.nextInt(4)],
            )
        }
    }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(durationMs, easing = LinearEasing)) }

    Canvas(modifier.fillMaxSize()) {
        val time = t.value
        if (time >= 1f) return@Canvas
        val w = size.width
        val o = Offset(size.width * origin.x, size.height * origin.y)
        particles.forEach { p ->
            // Ballistic flight: fast out, gravity pulls down, fade at the end.
            val dist = p.speed * w * 0.55f * (1f - (1f - time) * (1f - time))
            val gravity = w * 0.9f * time * time
            val pos = Offset(o.x + cos(p.angle) * dist, o.y + sin(p.angle) * dist + gravity)
            val alpha = (1f - time).coerceIn(0f, 1f) * if (time < 0.05f) time / 0.05f else 1f
            val s = w * p.size
            rotate(p.spin * time, pivot = pos) {
                when (p.kind) {
                    0 -> {
                        drawCircle(p.color.copy(alpha = alpha), s, pos)
                        drawCircle(Color.White.copy(alpha = alpha * 0.8f), s * 0.72f, pos, style = Stroke(s * 0.18f))
                    }
                    1 -> drawSuit(p.suit, Offset(pos.x - s, pos.y - s), s * 2, MastColors.Gold.copy(alpha = alpha))
                    else -> drawRect(p.color.copy(alpha = alpha), Offset(pos.x - s * 0.5f, pos.y - s), androidx.compose.ui.geometry.Size(s, s * 2))
                }
            }
        }
    }
}
