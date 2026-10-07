package app.mast.poker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mast.poker.core.poker.Card
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One card that flies in from above the table, settles with a spring and then
 * turns face up. Changing [dealKey] replays the whole sequence.
 */
@Composable
fun DealtCard(
    card: Card,
    width: Dp,
    index: Int,
    dealKey: Any,
    modifier: Modifier = Modifier,
    baseDelayMs: Long = 0,
    faceUp: Boolean = true,
    state: CardState = CardState.Normal,
    onClick: (() -> Unit)? = null,
) {
    val reduced = LocalReducedMotion.current
    val enter = remember(dealKey) { Animatable(if (reduced) 1f else 0f) }
    val flip = remember(dealKey) { Animatable(if (reduced && faceUp) 1f else 0f) }

    LaunchedEffect(dealKey, faceUp) {
        if (reduced) {
            enter.snapTo(1f)
            flip.snapTo(if (faceUp) 1f else 0f)
            return@LaunchedEffect
        }
        delay(baseDelayMs + index * Motion.DealStagger.toLong())
        launch { enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f)) }
        if (faceUp) {
            delay(150)
            flip.animateTo(1f, tween(420, easing = Motion.Emphasized))
        } else {
            flip.animateTo(0f, tween(320, easing = Motion.Emphasized))
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lifted = state == CardState.Selected || state == CardState.Winning
    val lift by animateDpAsState(if (lifted) 8.dp else 0.dp, Motion.cardSpring(), label = "lift")
    val pressScale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.uiSpring(), label = "press")
    val density = LocalDensity.current
    val travel = with(density) { 140.dp.toPx() }
    val liftPx = with(density) { lift.toPx() }

    PlayingCard(
        card = card,
        width = width,
        flip = flip.value,
        state = state,
        modifier = modifier
            .graphicsLayer {
                val t = 1f - enter.value
                translationY = -travel * t - liftPx
                translationX = (index - 2) * -18f * t * density.density
                rotationZ = -16f * t
                alpha = (enter.value * 2f).coerceIn(0f, 1f)
                scaleX = (0.85f + 0.15f * enter.value) * pressScale
                scaleY = (0.85f + 0.15f * enter.value) * pressScale
            }
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier,
            ),
    )
}

/** A row of dealt cards. [stateOf] lets callers highlight, select or dim individual cards. */
@Composable
fun DealtCardRow(
    cards: List<Card>,
    cardWidth: Dp,
    dealKey: Any,
    modifier: Modifier = Modifier,
    gap: Dp = 6.dp,
    baseDelayMs: Long = 0,
    firstIndex: Int = 0,
    stateOf: (Card) -> CardState = { CardState.Normal },
    onCardClick: ((Card) -> Unit)? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        cards.forEachIndexed { i, card ->
            DealtCard(
                card = card,
                width = cardWidth,
                index = firstIndex + i,
                dealKey = dealKey,
                baseDelayMs = baseDelayMs,
                state = stateOf(card),
                onClick = onCardClick?.let { { it(card) } },
            )
        }
    }
}
