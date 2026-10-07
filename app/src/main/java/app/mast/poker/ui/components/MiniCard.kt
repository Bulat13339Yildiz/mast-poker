package app.mast.poker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.mast.poker.core.poker.Card
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Playfair

/** Tiny card for dense layouts (ladder, deck grid): rank over suit, nothing else. */
@Composable
fun MiniCard(card: Card, width: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false, highlighted: Boolean = false) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size(width, width * CARD_RATIO)) {
        val r = CornerRadius(size.width * 0.14f)
        drawRoundRect(MastColors.Ivory, cornerRadius = r)
        val color = SuitShapes.colorOf(card.suit)
        val label = card.rank.label.let { if (it == "10") "10" else it }
        val layout = measurer.measure(
            label,
            TextStyle(color = color, fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = (size.width * (if (label.length > 1) 0.38f else 0.48f) / density).sp),
        )
        drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, size.height * 0.06f))
        val s = size.width * 0.5f
        drawSuit(card.suit, Offset((size.width - s) / 2f, size.height * 0.52f), s)
        if (highlighted) drawRoundRect(MastColors.Gold, cornerRadius = r, style = Stroke(size.width * 0.08f))
        if (dimmed) drawRoundRect(Color.Black.copy(alpha = 0.55f), cornerRadius = r)
    }
}
