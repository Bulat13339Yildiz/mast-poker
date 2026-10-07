package app.mast.poker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import app.mast.poker.achievements.Tier
import app.mast.poker.core.poker.Suit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class MedalPalette(val light: Color, val base: Color, val dark: Color)

fun Tier.palette(): MedalPalette = when (this) {
    Tier.BRONZE -> MedalPalette(Color(0xFFF0B98A), Color(0xFFC07A45), Color(0xFF6E3F1F))
    Tier.SILVER -> MedalPalette(Color(0xFFF4F6F8), Color(0xFFB9C1C8), Color(0xFF5F6870))
    Tier.GOLD -> MedalPalette(Color(0xFFFFE9A8), Color(0xFFD9AE4F), Color(0xFF7A5718))
}

private val LockedPalette = MedalPalette(Color(0xFF3C4A44), Color(0xFF26322D), Color(0xFF161E1B))

/**
 * Poker-chip style medal: notched outer ring, inner disc and a suit emblem.
 * The emblem suit is picked from the achievement id so medals look varied.
 */
@Composable
fun Medal(tier: Tier, emblem: Suit, size: Dp, modifier: Modifier = Modifier, locked: Boolean = false) {
    val p = if (locked) LockedPalette else tier.palette()
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.35f), r, center + Offset(0f, r * 0.06f))
        drawCircle(
            Brush.linearGradient(listOf(p.light, p.base, p.dark), start = Offset(0f, 0f), end = Offset(this.size.width, this.size.height)),
            r,
        )
        // Chip notches around the edge.
        val notches = 8
        for (i in 0 until notches) {
            val a = (i * 2 * PI / notches).toFloat()
            val path = Path().apply {
                val inner = r * 0.78f
                val half = (PI / notches * 0.45).toFloat()
                moveTo(center.x + cos(a - half) * inner, center.y + sin(a - half) * inner)
                lineTo(center.x + cos(a - half) * r * 0.98f, center.y + sin(a - half) * r * 0.98f)
                lineTo(center.x + cos(a + half) * r * 0.98f, center.y + sin(a + half) * r * 0.98f)
                lineTo(center.x + cos(a + half) * inner, center.y + sin(a + half) * inner)
                close()
            }
            drawPath(path, Color.White.copy(alpha = if (locked) 0.06f else 0.55f))
        }
        drawCircle(Brush.radialGradient(listOf(p.base, p.dark), center, r * 0.8f), r * 0.72f)
        drawCircle(p.light.copy(alpha = 0.8f), r * 0.72f, style = Stroke(r * 0.04f))
        val s = r * 0.7f
        drawSuit(emblem, Offset(center.x - s / 2, center.y - s / 2), s, if (locked) Color.White.copy(alpha = 0.15f) else p.light)
    }
}

fun emblemFor(id: String): Suit = Suit.entries[(id.hashCode() and 0x7fffffff) % 4]
