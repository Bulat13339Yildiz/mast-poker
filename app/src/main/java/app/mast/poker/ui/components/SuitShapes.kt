package app.mast.poker.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import app.mast.poker.core.poker.Suit
import app.mast.poker.ui.theme.MastColors

/** Suit glyphs as vector paths in a 1×1 box — never rely on font glyphs, they turn into emoji on some phones. */
object SuitShapes {

    val heart: Path = Path().apply {
        moveTo(0.5f, 0.95f)
        cubicTo(0.18f, 0.70f, 0.0f, 0.50f, 0.0f, 0.30f)
        cubicTo(0.0f, 0.13f, 0.13f, 0.02f, 0.28f, 0.02f)
        cubicTo(0.38f, 0.02f, 0.46f, 0.08f, 0.5f, 0.17f)
        cubicTo(0.54f, 0.08f, 0.62f, 0.02f, 0.72f, 0.02f)
        cubicTo(0.87f, 0.02f, 1.0f, 0.13f, 1.0f, 0.30f)
        cubicTo(1.0f, 0.50f, 0.82f, 0.70f, 0.5f, 0.95f)
        close()
    }

    val diamond: Path = Path().apply {
        moveTo(0.5f, 0.0f)
        quadraticTo(0.68f, 0.28f, 0.9f, 0.5f)
        quadraticTo(0.68f, 0.72f, 0.5f, 1.0f)
        quadraticTo(0.32f, 0.72f, 0.1f, 0.5f)
        quadraticTo(0.32f, 0.28f, 0.5f, 0.0f)
        close()
    }

    val spade: Path = Path().apply {
        moveTo(0.5f, 0.02f)
        cubicTo(0.30f, 0.25f, 0.0f, 0.40f, 0.0f, 0.60f)
        cubicTo(0.0f, 0.76f, 0.12f, 0.86f, 0.27f, 0.86f)
        cubicTo(0.37f, 0.86f, 0.44f, 0.81f, 0.48f, 0.75f)
        cubicTo(0.46f, 0.86f, 0.40f, 0.94f, 0.30f, 1.0f)
        lineTo(0.70f, 1.0f)
        cubicTo(0.60f, 0.94f, 0.54f, 0.86f, 0.52f, 0.75f)
        cubicTo(0.56f, 0.81f, 0.63f, 0.86f, 0.73f, 0.86f)
        cubicTo(0.88f, 0.86f, 1.0f, 0.76f, 1.0f, 0.60f)
        cubicTo(1.0f, 0.40f, 0.70f, 0.25f, 0.5f, 0.02f)
        close()
    }

    val club: Path = Path().apply {
        addOval(Rect(center = Offset(0.5f, 0.25f), radius = 0.22f))
        addOval(Rect(center = Offset(0.25f, 0.57f), radius = 0.22f))
        addOval(Rect(center = Offset(0.75f, 0.57f), radius = 0.22f))
        addOval(Rect(center = Offset(0.5f, 0.52f), radius = 0.14f))
        moveTo(0.46f, 0.55f)
        cubicTo(0.46f, 0.78f, 0.40f, 0.92f, 0.30f, 1.0f)
        lineTo(0.70f, 1.0f)
        cubicTo(0.60f, 0.92f, 0.54f, 0.78f, 0.54f, 0.55f)
        close()
    }

    fun of(suit: Suit): Path = when (suit) {
        Suit.HEARTS -> heart
        Suit.DIAMONDS -> diamond
        Suit.SPADES -> spade
        Suit.CLUBS -> club
    }

    fun colorOf(suit: Suit): Color = if (suit.isRed) MastColors.SuitRed else MastColors.SuitBlack
}

/** Draws [suit] fitted into a square of [size] px whose top-left is [topLeft]. */
fun DrawScope.drawSuit(suit: Suit, topLeft: Offset, size: Float, color: Color = SuitShapes.colorOf(suit)) {
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(size, size, pivot = Offset.Zero)
    }) {
        drawPath(SuitShapes.of(suit), color)
    }
}
