package app.mast.poker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Rank
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.Playfair
import kotlin.math.roundToInt

enum class CardState { Normal, Selected, Winning, Dimmed, Wrong }

const val CARD_RATIO = 1.4f

private val CardPaper = Color(0xFFFBF7EE)
private val CardPaperShade = Color(0xFFEFE7D6)
private val CardInk = Color(0xFF1B1B1F)
private val BackNavy = Color(0xFF233368)
private val BackNavyDark = Color(0xFF1A274F)
private val BackLine = Color(0xFFF1ECE1)

/**
 * A playing card drawn on Canvas in a classic deck style: corner indices, pip layouts,
 * illustrated court figures and a lattice back. [flip] goes 0 (back) → 1 (face); the
 * face is swapped exactly at the 90° edge so the turn reads as one physical motion.
 */
@Composable
fun PlayingCard(
    card: Card?,
    width: Dp,
    modifier: Modifier = Modifier,
    flip: Float = 1f,
    state: CardState = CardState.Normal,
) {
    val measurer = rememberTextMeasurer()
    val art = card?.let { rememberCourtArt(it) }
    val glow by animateFloatAsState(
        targetValue = when (state) { CardState.Selected, CardState.Winning -> 1f; else -> 0f },
        animationSpec = Motion.uiSpring(), label = "glow",
    )
    val dim by animateFloatAsState(if (state == CardState.Dimmed) 1f else 0f, Motion.enter(), label = "dim")
    val wrong by animateFloatAsState(if (state == CardState.Wrong) 1f else 0f, Motion.enter(), label = "wrong")
    val height = width * CARD_RATIO
    val angle = flip.coerceIn(0f, 1f) * 180f
    val showFace = angle >= 90f
    val description = if (card != null && showFace) "${card.rank.ruName} ${card.suit.ruGenitive}" else "карта рубашкой вверх"

    Box(
        modifier
            .size(width, height)
            .semantics { contentDescription = description }
            .graphicsLayer {
                rotationY = if (showFace) angle - 180f else angle
                cameraDistance = 12f * density
            }
            .drawBehind { drawShadow(glow) },
    ) {
        Canvas(Modifier.size(width, height)) {
            if (showFace && card != null) drawFace(card, measurer, art) else drawBack()
            if (glow > 0f) drawGlowBorder(MastColors.Gold, glow)
            if (wrong > 0f) drawGlowBorder(MastColors.Wrong, wrong)
            if (dim > 0f) drawRoundRect(Color.Black.copy(alpha = 0.55f * dim), cornerRadius = corner())
        }
    }
}

private fun DrawScope.corner() = CornerRadius(size.width * 0.075f)

private fun DrawScope.drawShadow(glow: Float) {
    val r = corner()
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(0f, size.height * 0.025f),
        size = size,
        cornerRadius = r,
    )
    if (glow > 0f) {
        val spread = size.width * 0.10f * glow
        drawRoundRect(
            brush = Brush.radialGradient(
                listOf(MastColors.Gold.copy(alpha = 0.45f * glow), Color.Transparent),
                center = center,
                radius = size.maxDimension * 0.75f,
            ),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = CornerRadius(r.x + spread),
        )
    }
}

private fun DrawScope.drawGlowBorder(color: Color, amount: Float) {
    val w = size.width * 0.035f
    drawRoundRect(
        color = color.copy(alpha = amount),
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(corner().x - w / 2),
        style = Stroke(width = w),
    )
}

// --- Face -------------------------------------------------------------------------------

private fun suitColor(card: Card) = if (card.suit.isRed) MastColors.SuitRed else CardInk

/** Area for pips and court art, between the corner indices. */
private fun DrawScope.artRect(): Rect = Rect(size.width * 0.18f, size.height * 0.07f, size.width * 0.82f, size.height * 0.93f)

private fun DrawScope.drawFace(card: Card, measurer: TextMeasurer, art: ImageBitmap?) {
    val r = corner()
    drawRoundRect(
        brush = Brush.linearGradient(listOf(CardPaper, CardPaperShade), start = Offset.Zero, end = Offset(size.width, size.height)),
        cornerRadius = r,
    )
    drawRoundRect(color = Color.Black.copy(alpha = 0.10f), cornerRadius = r, style = Stroke(width = size.width * 0.01f))

    drawIndex(card, measurer)
    rotate(180f) { drawIndex(card, measurer) }

    when (card.rank) {
        Rank.JACK, Rank.QUEEN, Rank.KING -> drawCourt(card, measurer, art)
        Rank.ACE -> drawAce(card)
        else -> drawPips(card)
    }
}

/** Rank over suit in the top-left corner, like a printed deck. */
private fun DrawScope.drawIndex(card: Card, measurer: TextMeasurer) {
    val w = size.width
    val label = if (card.rank == Rank.TEN) "10" else card.rank.label
    val wide = label.length > 1
    val layout = measurer.measure(
        label,
        TextStyle(
            color = suitColor(card),
            fontFamily = Playfair,
            fontWeight = FontWeight.Bold,
            fontSize = (w * (if (wide) 0.15f else 0.19f) / density).sp,
            letterSpacing = if (wide) (-0.06).em else 0.em,
        ),
    )
    val column = w * 0.15f
    val left = w * 0.025f
    drawText(layout, topLeft = Offset(left + (column - layout.size.width) / 2f, w * 0.03f + if (wide) w * 0.02f else 0f))
    val s = w * 0.11f
    drawSuit(card.suit, Offset(left + (column - s) / 2f, w * 0.26f), s, suitColor(card))
}

private fun DrawScope.drawAce(card: Card) {
    val w = size.width
    val big = w * 0.42f
    val c = center
    drawCircle(MastColors.GoldDark.copy(alpha = 0.55f), radius = big * 0.82f, center = c, style = Stroke(width = w * 0.01f))
    drawCircle(MastColors.GoldDark.copy(alpha = 0.3f), radius = big * 0.92f, center = c, style = Stroke(width = w * 0.005f))
    drawSuit(card.suit, Offset(c.x - big / 2f, c.y - big / 2f), big, suitColor(card))
}

/** Pip positions (x, y) as fractions of the art rect; lower-half pips are drawn upside down. */
private val pipLayouts: Map<Int, List<Pair<Float, Float>>> = run {
    val l = 0.22f; val c = 0.5f; val r = 0.78f
    mapOf(
        2 to listOf(c to 0.12f, c to 0.88f),
        3 to listOf(c to 0.12f, c to 0.5f, c to 0.88f),
        4 to listOf(l to 0.12f, r to 0.12f, l to 0.88f, r to 0.88f),
        5 to listOf(l to 0.12f, r to 0.12f, c to 0.5f, l to 0.88f, r to 0.88f),
        6 to listOf(l to 0.12f, r to 0.12f, l to 0.5f, r to 0.5f, l to 0.88f, r to 0.88f),
        7 to listOf(l to 0.12f, r to 0.12f, c to 0.31f, l to 0.5f, r to 0.5f, l to 0.88f, r to 0.88f),
        8 to listOf(l to 0.12f, r to 0.12f, c to 0.31f, l to 0.5f, r to 0.5f, c to 0.69f, l to 0.88f, r to 0.88f),
        9 to listOf(l to 0.12f, r to 0.12f, l to 0.37f, r to 0.37f, c to 0.5f, l to 0.63f, r to 0.63f, l to 0.88f, r to 0.88f),
        10 to listOf(l to 0.12f, r to 0.12f, c to 0.245f, l to 0.37f, r to 0.37f, l to 0.63f, r to 0.63f, c to 0.755f, l to 0.88f, r to 0.88f),
    )
}

private fun DrawScope.drawPips(card: Card) {
    val area = artRect()
    val pip = size.width * 0.17f
    val color = suitColor(card)
    pipLayouts.getValue(card.rank.value).forEach { (fx, fy) ->
        val cx = area.left + area.width * fx
        val cy = area.top + area.height * fy
        val topLeft = Offset(cx - pip / 2f, cy - pip / 2f)
        if (fy > 0.5f) {
            rotate(180f, pivot = Offset(cx, cy)) { drawSuit(card.suit, topLeft, pip, color) }
        } else {
            drawSuit(card.suit, topLeft, pip, color)
        }
    }
}

private fun DrawScope.drawCourt(card: Card, measurer: TextMeasurer, art: ImageBitmap?) {
    val area = artRect()
    val frame = RoundRect(area, CornerRadius(size.width * 0.02f))
    if (art != null) {
        clipPath(Path().apply { addRoundRect(frame) }) {
            // Centre-crop the illustration into the frame.
            val scale = maxOf(area.width / art.width, area.height / art.height)
            val dw = art.width * scale
            val dh = art.height * scale
            drawImage(
                art,
                dstOffset = IntOffset((area.center.x - dw / 2f).roundToInt(), (area.center.y - dh / 2f).roundToInt()),
                dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
    } else {
        // Fallback when no illustration is bundled: a large serif letter.
        val letter = measurer.measure(
            card.rank.label,
            TextStyle(color = suitColor(card), fontFamily = Playfair, fontWeight = FontWeight.Black, fontSize = (size.width * 0.42f / density).sp),
        )
        drawText(letter, topLeft = Offset(area.center.x - letter.size.width / 2f, area.center.y - letter.size.height * 0.62f))
        val s = size.width * 0.16f
        drawSuit(card.suit, Offset(area.center.x - s / 2f, area.center.y + size.width * 0.12f), s, suitColor(card))
    }
    drawRoundRect(CardInk.copy(alpha = 0.75f), topLeft = area.topLeft, size = area.size, cornerRadius = frame.topLeftCornerRadius, style = Stroke(width = size.width * 0.008f))
}

// --- Back -------------------------------------------------------------------------------

/**
 * Classic lattice back: paper border, navy field, a band of rings, a ring-and-diagonal
 * lattice and a diamond medallion in the centre.
 */
private fun DrawScope.drawBack() {
    val w = size.width
    val h = size.height
    val r = corner()
    drawRoundRect(CardPaper, cornerRadius = r)
    val m = w * 0.06f
    val field = Rect(m, m, w - m, h - m)
    val fieldR = CornerRadius(w * 0.03f)
    drawRoundRect(Brush.verticalGradient(listOf(BackNavy, BackNavyDark)), topLeft = field.topLeft, size = field.size, cornerRadius = fieldR)

    val line = w * 0.009f
    val thin = Stroke(width = line)
    // Outer and inner frame lines of the border band.
    val outer = field.deflate(w * 0.025f)
    val inner = field.deflate(w * 0.11f)
    drawRect(BackLine, outer.topLeft, outer.size, style = thin)
    drawRect(BackLine, inner.topLeft, inner.size, style = thin)
    // Chain of rings around the band.
    val bandMid = field.deflate(w * 0.0675f)
    val ringR = w * 0.03f
    val step = ringR * 2.25f
    fun ringsAlong(from: Offset, to: Offset) {
        val len = (to - from).getDistance()
        val n = (len / step).toInt().coerceAtLeast(1)
        for (i in 0..n) {
            val t = i / n.toFloat()
            drawCircle(BackLine, ringR, Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t), style = thin)
        }
    }
    ringsAlong(bandMid.topLeft, bandMid.topRight)
    ringsAlong(bandMid.bottomLeft, bandMid.bottomRight)
    ringsAlong(bandMid.topLeft, bandMid.bottomLeft)
    ringsAlong(bandMid.topRight, bandMid.bottomRight)

    // Lattice: diagonal lines with rings at the crossings.
    val lattice = inner.deflate(w * 0.02f)
    clipRect(lattice.left, lattice.top, lattice.right, lattice.bottom) {
        val cell = w * 0.1f
        val color = BackLine.copy(alpha = 0.9f)
        var k = -h
        while (k < w + h) {
            drawLine(color, Offset(lattice.left + k, lattice.top), Offset(lattice.left + k + lattice.height, lattice.bottom), line * 0.8f)
            drawLine(color, Offset(lattice.left + k + lattice.height, lattice.top), Offset(lattice.left + k, lattice.bottom), line * 0.8f)
            k += cell
        }
        var y = lattice.top
        var row = 0
        while (y <= lattice.bottom + cell) {
            var x = lattice.left + if (row % 2 == 0) 0f else cell / 2f
            while (x <= lattice.right + cell) {
                drawCircle(BackNavy, cell * 0.2f, Offset(x, y))
                drawCircle(color, cell * 0.2f, Offset(x, y), style = Stroke(width = line * 0.8f))
                x += cell
            }
            y += cell / 2f
            row++
        }
    }

    // Diamond medallion.
    val c = center
    val dx = w * 0.2f
    val dy = w * 0.27f
    val diamond = Path().apply {
        moveTo(c.x, c.y - dy); lineTo(c.x + dx, c.y); lineTo(c.x, c.y + dy); lineTo(c.x - dx, c.y); close()
    }
    drawPath(diamond, BackNavy)
    drawPath(diamond, BackLine, style = Stroke(width = line * 1.4f))
    val innerDiamond = Path().apply {
        val k2 = 0.8f
        moveTo(c.x, c.y - dy * k2); lineTo(c.x + dx * k2, c.y); lineTo(c.x, c.y + dy * k2); lineTo(c.x - dx * k2, c.y); close()
    }
    drawPath(innerDiamond, BackLine, style = Stroke(width = line * 0.8f))
    // Concave four-point star.
    val sx = dx * 0.62f
    val sy = dy * 0.62f
    val star = Path().apply {
        moveTo(c.x, c.y - sy)
        quadraticTo(c.x + sx * 0.18f, c.y - sy * 0.18f, c.x + sx, c.y)
        quadraticTo(c.x + sx * 0.18f, c.y + sy * 0.18f, c.x, c.y + sy)
        quadraticTo(c.x - sx * 0.18f, c.y + sy * 0.18f, c.x - sx, c.y)
        quadraticTo(c.x - sx * 0.18f, c.y - sy * 0.18f, c.x, c.y - sy)
        close()
    }
    drawPath(star, BackLine, style = Stroke(width = line))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0 + 22.5)
        drawCircle(BackLine, w * 0.012f, Offset(c.x + (kotlin.math.cos(a) * dx * 0.42f).toFloat(), c.y + (kotlin.math.sin(a) * dy * 0.42f).toFloat()))
    }
    withTransform({ rotate(45f, c) }) {
        drawRect(BackLine, Offset(c.x - w * 0.022f, c.y - w * 0.022f), Size(w * 0.044f, w * 0.044f))
    }
    drawRoundRect(color = Color.Black.copy(alpha = 0.12f), cornerRadius = r, style = Stroke(width = w * 0.01f))
}
