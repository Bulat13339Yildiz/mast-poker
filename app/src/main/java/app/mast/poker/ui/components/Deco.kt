package app.mast.poker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mast.poker.core.poker.Suit
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Playfair
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small caps label in a gold pill, flanked by hairlines: ——[ ГЛАВА 1 ]—— */
@Composable
fun GoldLabel(text: String, modifier: Modifier = Modifier, lines: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (lines) Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, MastColors.GoldStroke))))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MastColors.GoldLight,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .border(1.dp, MastColors.Gold.copy(alpha = 0.75f), RoundedCornerShape(50))
                .background(MastColors.Felt.copy(alpha = 0.6f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 3.dp),
        )
        if (lines) Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(MastColors.GoldStroke, Color.Transparent))))
    }
}

/** Double gold frame: a hairline border and a fainter one inset inside it. */
fun Modifier.gildedFrame(corner: Dp = 22.dp, inset: Dp = 5.dp): Modifier = drawWithContent {
    drawContent()
    val c = corner.toPx()
    val i = inset.toPx()
    drawRoundRect(MastColors.Gold.copy(alpha = 0.55f), cornerRadius = CornerRadius(c), style = Stroke(1.dp.toPx()))
    drawRoundRect(
        MastColors.Gold.copy(alpha = 0.22f),
        topLeft = Offset(i, i),
        size = Size(size.width - i * 2, size.height - i * 2),
        cornerRadius = CornerRadius((c - i).coerceAtLeast(2f)),
        style = Stroke(1.dp.toPx()),
    )
}

/** Art-deco band: two gold rules with a diamond lattice between them. */
@Composable
fun DecoBand(modifier: Modifier = Modifier, height: Dp = 14.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gold = MastColors.Gold.copy(alpha = 0.55f)
        val faint = MastColors.Gold.copy(alpha = 0.28f)
        val stroke = 1.dp.toPx()
        drawLine(gold, Offset(0f, 0f), Offset(size.width, 0f), stroke)
        drawLine(gold, Offset(0f, size.height), Offset(size.width, size.height), stroke)
        val step = size.height
        clipRect {
            var x = 0f
            while (x < size.width) {
                drawLine(faint, Offset(x, 0f), Offset(x + step, size.height), stroke)
                drawLine(faint, Offset(x + step, 0f), Offset(x, size.height), stroke)
                x += step
            }
        }
    }
}

/** Ornamental divider with a suit in the middle. */
@Composable
fun SuitDivider(suit: Suit = Suit.SPADES, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, MastColors.GoldStroke))))
        Canvas(Modifier.padding(horizontal = 10.dp).size(12.dp)) { drawSuit(suit, Offset.Zero, size.width, MastColors.Gold) }
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(MastColors.GoldStroke, Color.Transparent))))
    }
}

// --- Casino chips -------------------------------------------------------------------

data class ChipColors(val body: Color, val dark: Color, val insert: Color)

val RedChip = ChipColors(MastColors.ChipRed, MastColors.ChipRedDark, MastColors.ChipWhite)
val GreenChip = ChipColors(Color(0xFF1D8A4E), Color(0xFF0C4426), MastColors.ChipWhite)
val BlackChip = ChipColors(Color(0xFF2A2A2A), Color(0xFF0E0E0E), MastColors.Gold)

/**
 * A casino chip seen at an angle (top face squashed to an ellipse) with its edge
 * visible below. [tilt] is the vertical squash of the top face: 1 = top-down.
 */
fun DrawScope.drawChip(center: Offset, radius: Float, colors: ChipColors = RedChip, tilt: Float = 0.55f, thickness: Float = radius * 0.22f) {
    val rx = radius
    val ry = radius * tilt
    val top = Offset(center.x - rx, center.y - ry)
    val faceSize = Size(rx * 2, ry * 2)

    // Shadow on the felt.
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(top.x - rx * 0.05f, top.y + thickness + ry * 0.25f), Size(faceSize.width * 1.1f, faceSize.height * 1.05f))
    // Edge: bottom ellipse + band.
    drawOval(colors.dark, Offset(top.x, top.y + thickness), faceSize)
    drawRect(colors.dark, Offset(top.x, center.y), Size(faceSize.width, thickness))
    // Edge inserts on the visible front half.
    val inserts = 12
    for (i in 0 until inserts) {
        val a = (i + 0.5f) / inserts * PI.toFloat() * 2f
        val s = sin(a)
        if (s <= 0.15f) continue
        val x = center.x + cos(a) * rx
        val w = rx * 0.22f * s
        drawRect(colors.insert.copy(alpha = 0.95f), Offset(x - w / 2, center.y + s * ry * 0.98f - 1f), Size(w, thickness * 0.95f))
    }
    // Top face.
    drawOval(Brush.linearGradient(listOf(colors.body.copy(alpha = 1f), colors.dark), start = top, end = Offset(top.x + faceSize.width, top.y + faceSize.height * 1.6f)), top, faceSize)
    // Rim inserts on the top face.
    for (i in 0 until inserts) {
        val a = i.toFloat() / inserts * PI.toFloat() * 2f
        val half = PI.toFloat() / inserts * 0.42f
        val path = Path().apply {
            val inner = 0.76f
            moveTo(center.x + cos(a - half) * rx * inner, center.y + sin(a - half) * ry * inner)
            lineTo(center.x + cos(a - half) * rx * 0.98f, center.y + sin(a - half) * ry * 0.98f)
            lineTo(center.x + cos(a + half) * rx * 0.98f, center.y + sin(a + half) * ry * 0.98f)
            lineTo(center.x + cos(a + half) * rx * inner, center.y + sin(a + half) * ry * inner)
            close()
        }
        drawPath(path, colors.insert)
    }
    val innerScale = 0.66f
    drawOval(
        colors.body,
        Offset(center.x - rx * innerScale, center.y - ry * innerScale),
        Size(rx * 2 * innerScale, ry * 2 * innerScale),
    )
    drawOval(
        colors.insert.copy(alpha = 0.7f),
        Offset(center.x - rx * innerScale, center.y - ry * innerScale),
        Size(rx * 2 * innerScale, ry * 2 * innerScale),
        style = Stroke(rx * 0.035f),
    )
    // Gloss.
    drawOval(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), Offset(center.x - rx * 0.3f, center.y - ry * 0.5f), rx * 0.9f),
        top, faceSize,
    )
}

@Composable
fun CasinoChip(size: Dp, modifier: Modifier = Modifier, colors: ChipColors = RedChip, tilt: Float = 0.55f) {
    Canvas(modifier.size(size, size * (tilt + 0.35f))) {
        val r = this.size.width / 2f * 0.92f
        drawChip(Offset(this.size.width / 2f, r * tilt + 2f), r, colors, tilt)
    }
}

/** Stacks of chips — the "strategy" illustration. */
@Composable
fun ChipStacks(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size, size * 0.9f)) {
        val r = this.size.width * 0.2f
        val t = r * 0.24f
        val stacks = listOf(
            Triple(this.size.width * 0.32f, 4, RedChip),
            Triple(this.size.width * 0.66f, 3, RedChip),
            Triple(this.size.width * 0.5f, 6, RedChip),
        )
        val base = this.size.height * 0.78f
        stacks.forEachIndexed { idx, (x, n, colors) ->
            val y0 = base + if (idx == 2) r * 0.35f else 0f
            for (i in 0 until n) drawChip(Offset(x, y0 - i * t - r * 0.55f), r, colors, thickness = t)
        }
    }
}

/** Glossy green button with a gold rim and a suit — the "how to play" illustration. */
@Composable
fun SuitBadge(suit: Suit, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.96f, center + Offset(0f, r * 0.08f))
        drawCircle(Brush.linearGradient(listOf(MastColors.GoldLight, MastColors.GoldDark)), r * 0.96f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF2BAA62), Color(0xFF0D5A31), Color(0xFF063A1E)), center - Offset(r * 0.25f, r * 0.35f), r * 1.2f), r * 0.86f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center - Offset(r * 0.2f, r * 0.45f), r * 0.6f), r * 0.86f)
        val s = r * 0.95f
        drawSuit(suit, Offset(center.x - s / 2, center.y - s / 2 + r * 0.04f), s, MastColors.Ivory)
    }
}

/** White dealer button with a serif "D". */
@Composable
fun DealerButton(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.95f, center + Offset(0f, r * 0.1f))
            drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFD9D3C7))), r * 0.95f)
            drawCircle(MastColors.Gold, r * 0.78f, style = Stroke(r * 0.05f))
        }
        Text("D", fontFamily = Playfair, fontWeight = FontWeight.Black, color = MastColors.SuitBlack, style = MaterialTheme.typography.headlineMedium)
    }
}
