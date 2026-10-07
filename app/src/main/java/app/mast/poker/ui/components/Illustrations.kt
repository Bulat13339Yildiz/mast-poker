package app.mast.poker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Suit
import app.mast.poker.core.poker.cards

/** Two cards fanned like a hand held at the table. */
@Composable
fun FannedCards(spec: String, size: Dp, modifier: Modifier = Modifier) {
    val (a, b) = cards(spec)
    FannedPair(a, b, size, modifier)
}

@Composable
private fun FannedPair(a: Card?, b: Card?, size: Dp, modifier: Modifier = Modifier, flip: Float = 1f) {
    val w = size * 0.5f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        PlayingCard(a, w, Modifier.offset(x = -size * 0.13f, y = size * 0.02f).rotate(-12f), flip = flip)
        PlayingCard(b, w, Modifier.offset(x = size * 0.13f).rotate(9f), flip = flip)
    }
}

/** Two cards face down — what the opponent sees when you bluff. */
@Composable
fun BluffArt(size: Dp, modifier: Modifier = Modifier) = FannedPair(null, null, size, modifier, flip = 0f)

/** One court card, slightly turned — a face across the table. */
@Composable
fun PortraitArt(spec: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        PlayingCard(cards(spec).first(), size * 0.56f, Modifier.rotate(-6f))
    }
}

/** Three stacks growing left to right: a third, two thirds, the whole pot. */
@Composable
fun SizingArt(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size, size * 0.9f)) {
        val r = this.size.width * 0.15f
        val t = r * 0.26f
        val base = this.size.height * 0.8f
        listOf(0.2f to 1, 0.5f to 3, 0.8f to 6).forEach { (x, n) ->
            for (i in 0 until n) drawChip(Offset(this.size.width * x, base - i * t - r * 0.55f), r, if (n == 6) BlackChip else RedChip, thickness = t)
        }
    }
}

/** One tall stack pushed to the middle — all-in. */
@Composable
fun AllInArt(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size, size * 0.9f)) {
        val r = this.size.width * 0.24f
        val t = r * 0.24f
        val base = this.size.height * 0.82f
        for (i in 0 until 3) drawChip(Offset(this.size.width * 0.28f, base - i * t - r * 0.4f), r * 0.8f, RedChip, thickness = t * 0.8f)
        for (i in 0 until 9) drawChip(Offset(this.size.width * 0.6f, base - i * t - r * 0.55f), r, BlackChip, thickness = t)
    }
}

/** Hero illustration for a chapter tile. */
@Composable
fun ChapterArt(chapterId: String, size: Dp, modifier: Modifier = Modifier) {
    when (chapterId) {
        "basics" -> Box(modifier.size(size), contentAlignment = Alignment.Center) { SuitBadge(Suit.SPADES, size * 0.72f) }
        "hands" -> FannedCards("Ah Ks", size, modifier)
        "flow" -> Box(modifier.size(size), contentAlignment = Alignment.Center) { DealerButton(size * 0.62f) }
        "preflop" -> FannedCards("Qd Qc", size, modifier)
        "math" -> Box(modifier.size(size), contentAlignment = Alignment.Center) { CasinoChip(size * 0.8f, colors = GreenChip) }
        "ranges" -> Box(modifier.size(size), contentAlignment = Alignment.Center) { MiniRange(size * 0.78f) }
        "sizing" -> SizingArt(size, modifier)
        "math2" -> Box(modifier.size(size), contentAlignment = Alignment.Center) { CasinoChip(size * 0.8f, colors = BlackChip) }
        "preflop2" -> FannedCards("Kh Kc", size, modifier)
        "tournaments" -> AllInArt(size, modifier)
        "opponents" -> PortraitArt("Kd", size, modifier)
        else -> ChipStacks(size, modifier)
    }
}
