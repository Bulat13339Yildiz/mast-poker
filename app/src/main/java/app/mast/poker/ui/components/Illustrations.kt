package app.mast.poker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import app.mast.poker.core.poker.Suit
import app.mast.poker.core.poker.cards

/** Two cards fanned like a hand held at the table. */
@Composable
fun FannedCards(spec: String, size: Dp, modifier: Modifier = Modifier) {
    val (a, b) = cards(spec)
    val w = size * 0.5f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        PlayingCard(a, w, Modifier.offset(x = -size * 0.13f, y = size * 0.02f).rotate(-12f))
        PlayingCard(b, w, Modifier.offset(x = size * 0.13f).rotate(9f))
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
        else -> ChipStacks(size, modifier)
    }
}
