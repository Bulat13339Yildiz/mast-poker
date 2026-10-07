package app.mast.poker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import app.mast.poker.core.poker.Suit
import app.mast.poker.ui.theme.MastColors

private val RedSuitOnFelt = Color(0xFFFF8A80)

private val suitIds = mapOf('♠' to "suit-s", '♥' to "suit-h", '♦' to "suit-d", '♣' to "suit-c")
private val suitById = mapOf("suit-s" to Suit.SPADES, "suit-h" to Suit.HEARTS, "suit-d" to Suit.DIAMONDS, "suit-c" to Suit.CLUBS)

/**
 * Suit symbols drawn as vector glyphs inline with the text. Font glyphs for ♠♥♦♣
 * turn into colour emoji on many phones, which looks cheap and inconsistent.
 */
val SuitInlineContent: Map<String, InlineTextContent> = suitById.mapValues { (_, suit) ->
    InlineTextContent(Placeholder(0.78.em, 0.78.em, PlaceholderVerticalAlign.TextCenter)) {
        // Follow the surrounding text's alpha so faded options fade their suits too.
        val alpha = LocalSuitAlpha.current
        Canvas(Modifier.fillMaxSize()) {
            val base = if (suit.isRed) RedSuitOnFelt else MastColors.TextPrimary
            drawSuit(suit, Offset.Zero, size.minDimension, base.copy(alpha = base.alpha * alpha))
        }
    }
}

private val LocalSuitAlpha = compositionLocalOf { 1f }

/** Turns "**bold**" spans into gold semibold text and suit symbols into vector glyphs. */
fun richText(source: String, accent: Color = MastColors.GoldLight): AnnotatedString = buildAnnotatedString {
    val parts = source.split("**")
    parts.forEachIndexed { i, part ->
        val bold = i % 2 == 1
        part.forEach { ch ->
            val suit = suitIds[ch]
            when {
                suit != null -> appendInlineContent(suit, ch.toString())
                ch == '️' || ch == '︎' -> Unit
                bold -> withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(ch) }
                else -> append(ch)
            }
        }
    }
}

@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MastColors.TextPrimary,
    textAlign: TextAlign? = null,
) {
    val alpha = if (color == Color.Unspecified) 1f else color.alpha
    CompositionLocalProvider(LocalSuitAlpha provides alpha) {
        Text(richText(text), modifier = modifier, style = style, color = color, inlineContent = SuitInlineContent, textAlign = textAlign)
    }
}

/** Horizontal shake for wrong answers. Bump [trigger] to replay. */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 420
                -14f at 50
                12f at 120
                -9f at 190
                6f at 260
                -3f at 330
            },
        )
    }
    return this.graphicsLayer { translationX = offset.value * density }
}
