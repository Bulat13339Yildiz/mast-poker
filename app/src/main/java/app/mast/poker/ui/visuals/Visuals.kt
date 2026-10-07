package app.mast.poker.ui.visuals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.mast.poker.content.Position
import app.mast.poker.content.Reference
import app.mast.poker.content.Street
import app.mast.poker.content.Visual
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.EquityCalculator
import app.mast.poker.core.poker.HandDescriber
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.Rank
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.Showdown
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.Suit
import app.mast.poker.core.poker.cards
import app.mast.poker.ui.components.CardState
import app.mast.poker.ui.components.DealerButton
import app.mast.poker.ui.components.DealtCard
import app.mast.poker.ui.components.DealtCardRow
import app.mast.poker.ui.components.MiniCard
import app.mast.poker.ui.components.RangeGrid
import app.mast.poker.ui.components.RedChip
import app.mast.poker.ui.components.drawChip
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.Playfair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Renders any lesson [Visual]. [key] restarts its entrance animation. */
@Composable
fun ContentVisual(visual: Visual, key: Any, modifier: Modifier = Modifier) {
    when (visual) {
        is Visual.Cards -> CardsVisual(visual, key, modifier)
        is Visual.Hand -> HandVisual(visual, key, modifier)
        is Visual.Duel -> DuelVisual(visual.board, visual.hands, key, modifier)
        Visual.RankingLadder -> RankingLadder(modifier)
        Visual.DeckGrid -> DeckGrid(modifier)
        is Visual.Table -> TableVisual(visual.highlight, modifier)
        is Visual.Streets -> StreetsVisual(visual.active, key, modifier)
        is Visual.PotMath -> PotMathVisual(visual.pot, visual.call, modifier)
        Visual.StartingHandsChart -> StartingHandsChart(modifier)
        is Visual.EquityStreets -> EquityStreetsVisual(visual.hero, visual.villain, visual.board, key, modifier)
        is Visual.Range -> Column(modifier.fillMaxWidth()) {
            RangeGrid(visual.hands)
            Caption(visual.caption)
        }
    }
}

/** Becomes true [delayMs] after [key] changes (or immediately with reduced motion). */
@Composable
fun rememberRevealed(key: Any, delayMs: Long): Boolean {
    val reduced = LocalReducedMotion.current
    var revealed by remember(key) { mutableStateOf(reduced) }
    LaunchedEffect(key) {
        if (!reduced) {
            delay(delayMs)
            revealed = true
        }
    }
    return revealed
}

@Composable
fun cardWidthFor(count: Int, maxWidth: Dp, gap: Dp = 6.dp, cap: Dp = 64.dp): Dp =
    min((maxWidth - gap * (count - 1)) / count, cap)

@Composable
fun Caption(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MastColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MastColors.TextMuted) {
    // Bottom gap leaves room for a lifted, glowing card under the label.
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier.padding(bottom = 16.dp))
}

@Composable
private fun CardsVisual(v: Visual.Cards, key: Any, modifier: Modifier) {
    val revealed = rememberRevealed(key, v.cards.size * 60L + 750)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val w = cardWidthFor(v.cards.size, maxWidth, cap = 72.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DealtCardRow(v.cards, w, key, stateOf = { c ->
                when {
                    !revealed || v.highlight.isEmpty() -> CardState.Normal
                    c in v.highlight -> CardState.Winning
                    else -> CardState.Dimmed
                }
            })
            Caption(v.caption)
        }
    }
}

@Composable
private fun HandVisual(v: Visual.Hand, key: Any, modifier: Modifier) {
    val all = v.hole + v.board
    val best = remember(v) { if (v.showBest && all.size >= 5) HandEvaluator.evaluate(all).cards.toSet() else emptySet() }
    val revealed = rememberRevealed(key, all.size * 60L + 900)
    val stateOf: (Card) -> CardState = { c ->
        when {
            !revealed || best.isEmpty() -> CardState.Normal
            c in best -> CardState.Winning
            else -> CardState.Dimmed
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = cardWidthFor(5, maxWidth)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (v.hole.isNotEmpty()) {
                SectionLabel("Твои карты")
                DealtCardRow(v.hole, w, key, stateOf = stateOf)
            }
            if (v.board.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionLabel("Стол")
                DealtCardRow(v.board, w, key, firstIndex = v.hole.size, stateOf = stateOf)
            }
            Caption(v.caption)
        }
    }
}

/** Board plus two hands; the winner lights up once the cards have landed. */
@Composable
fun DuelVisual(board: List<Card>, hands: List<List<Card>>, key: Any, modifier: Modifier = Modifier, reveal: Boolean = true, labels: List<String> = listOf("Игрок 1", "Игрок 2")) {
    val result = remember(board, hands) { Showdown.resolve(board, hands) }
    val revealed = rememberRevealed(key, 1300) && reveal
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = cardWidthFor(5, maxWidth)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionLabel("Стол")
            DealtCardRow(board, w, key, stateOf = { c ->
                if (!revealed) CardState.Normal
                else if (result.winners.any { c in result.values[it].cards }) CardState.Winning else CardState.Dimmed
            })
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                hands.forEachIndexed { i, hand ->
                    val won = revealed && i in result.winners
                    val glow by animateFloatAsState(if (won) 1f else 0f, Motion.uiSpring(), label = "duelGlow")
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MastColors.Glass)
                            .border(1.dp, lerpColor(MastColors.GlassStroke, MastColors.Gold, glow), RoundedCornerShape(16.dp))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(labels.getOrElse(i) { "Игрок ${i + 1}" }, style = MaterialTheme.typography.labelMedium, color = MastColors.TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            hand.forEachIndexed { j, c ->
                                DealtCard(c, w * 0.82f, index = 5 + i * 2 + j, dealKey = key, state = when {
                                    !revealed -> CardState.Normal
                                    i in result.winners && c in result.values[i].cards -> CardState.Winning
                                    else -> CardState.Normal
                                })
                            }
                        }
                        AnimatedVisibility(revealed, enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 2 }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    HandDescriber.describe(result.values[i]).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (won) MastColors.GoldLight else MastColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                )
                                if (won) {
                                    Text(
                                        if (result.isSplit) "Делёж банка" else "Победа",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MastColors.Gold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

@Composable
fun RankingLadder(modifier: Modifier = Modifier, onSelect: ((Int) -> Unit)? = null) {
    val reduced = LocalReducedMotion.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Reference.ladder.forEachIndexed { i, ex ->
            val appear = remember { Animatable(if (reduced) 1f else 0f) }
            LaunchedEffect(Unit) {
                if (!reduced) {
                    delay(i * 45L)
                    appear.animateTo(1f, Motion.enter(400))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = appear.value; translationX = (1f - appear.value) * 40f * density }
                    .clip(RoundedCornerShape(12.dp))
                    .background(MastColors.Glass)
                    .then(if (onSelect != null) Modifier.clickable { onSelect(i) } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).border(1.dp, MastColors.Gold.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${10 - i}", fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MastColors.GoldLight)
                }
                Spacer(Modifier.width(10.dp))
                Text(ex.category.ruName, style = MaterialTheme.typography.titleSmall, color = MastColors.TextPrimary, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ex.cards.forEach { c -> MiniCard(c, 20.dp, dimmed = c in ex.kickers) }
                }
            }
        }
    }
}

@Composable
private fun DeckGrid(modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = (maxWidth - 2.dp * 12) / 13
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Suit.SPADES, Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS).forEachIndexed { row, suit ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Rank.entries.forEachIndexed { col, rank ->
                        val a = remember { Animatable(if (reduced) 1f else 0f) }
                        LaunchedEffect(Unit) {
                            if (!reduced) {
                                delay((row * 13 + col) * 12L)
                                a.animateTo(1f, Motion.cardSpring())
                            }
                        }
                        MiniCard(
                            Card(rank, suit), w,
                            Modifier.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); scaleX = 0.6f + 0.4f * a.value; scaleY = 0.6f + 0.4f * a.value },
                        )
                    }
                }
            }
        }
    }
}

private val seatAngles = mapOf(
    Position.BTN to 90f, Position.SB to 150f, Position.BB to 210f,
    Position.UTG to 270f, Position.MP to 330f, Position.CO to 30f,
)

/** Oval table, six seats, dealer button and blinds. [highlight] pulses. */
@Composable
fun TableVisual(highlight: Position?, modifier: Modifier = Modifier) {
    val pulse = if (!LocalReducedMotion.current) {
        rememberInfiniteTransition(label = "seat").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p").value
    } else 0f
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.55f)) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            Canvas(Modifier.matchParentSize()) {
                val rail = Size(size.width * 0.78f, size.height * 0.66f)
                val tl = Offset((size.width - rail.width) / 2, (size.height - rail.height) / 2)
                drawOval(Color.Black.copy(alpha = 0.35f), tl + Offset(0f, 8f), rail)
                drawOval(Brush.verticalGradient(listOf(Color(0xFF4A2E1A), Color(0xFF26160B))), tl, rail)
                val inset = rail.width * 0.04f
                val felt = Size(rail.width - inset * 2, rail.height - inset * 2)
                drawOval(Brush.radialGradient(listOf(MastColors.FeltLight, MastColors.Felt, MastColors.FeltDark), center, felt.width * 0.6f), tl + Offset(inset, inset), felt)
                drawOval(MastColors.Gold.copy(alpha = 0.5f), tl + Offset(inset * 1.8f, inset * 1.8f), Size(felt.width - inset * 1.6f, felt.height - inset * 1.6f), style = Stroke(1.dp.toPx()))
                // Blinds on the felt in front of SB and BB.
                listOf(Position.SB to 0.7f, Position.BB to 0.85f).forEach { (p, scale) ->
                    val a = Math.toRadians(seatAngles.getValue(p).toDouble())
                    val c = Offset(center.x + cos(a).toFloat() * rail.width * 0.3f, center.y + sin(a).toFloat() * rail.height * 0.27f)
                    drawChip(c, size.width * 0.028f * scale + size.width * 0.01f, RedChip, thickness = 4f)
                }
            }
            seatAngles.forEach { (pos, angle) ->
                val a = Math.toRadians(angle.toDouble())
                val cx = wPx / 2 + cos(a).toFloat() * wPx * 0.43f
                val cy = hPx / 2 + sin(a).toFloat() * hPx * 0.42f
                val seat = 44.dp
                val active = pos == highlight
                val density = androidx.compose.ui.platform.LocalDensity.current
                val half = with(density) { (seat / 2).toPx() }
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()) }
                        .size(seat),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        Canvas(Modifier.size(seat)) {
                            drawCircle(MastColors.Gold.copy(alpha = (1f - pulse) * 0.6f), radius = size.minDimension / 2 * (1f + pulse * 0.45f), style = Stroke(2.dp.toPx()))
                        }
                    }
                    Box(
                        Modifier
                            .size(seat)
                            .clip(CircleShape)
                            .background(if (active) Brush.verticalGradient(listOf(MastColors.GoldLight, MastColors.Gold)) else Brush.verticalGradient(listOf(Color(0xFF1E3A2A), Color(0xFF0E2318))))
                            .border(1.dp, if (active) MastColors.GoldLight else MastColors.Gold.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(pos.short, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) MastColors.FeltDeep else MastColors.TextPrimary)
                    }
                }
                if (pos == Position.BTN) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val bx = wPx / 2 + cos(a).toFloat() * wPx * 0.26f + wPx * 0.08f
                    val by = hPx / 2 + sin(a).toFloat() * hPx * 0.24f
                    val half = with(density) { 13.dp.toPx() }
                    DealerButton(26.dp, Modifier.offset { androidx.compose.ui.unit.IntOffset((bx - half).roundToInt(), (by - half).roundToInt()) })
                }
            }
        }
        if (highlight != null) {
            Text(highlight.ruName, style = MaterialTheme.typography.titleMedium, color = MastColors.GoldLight)
            Text(highlight.description, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

private val streetsHole = cards("As Kd")
private val streetsBoard = cards("Qh Jc 7s 4d 2h")

/** The five board slots filling street by street up to [active]. */
@Composable
private fun StreetsVisual(active: Street, key: Any, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    var shown by remember(key) { mutableIntStateOf(if (reduced) active.ordinal else 0) }
    LaunchedEffect(key) {
        if (reduced) return@LaunchedEffect
        shown = 0
        for (s in 1..active.ordinal) {
            delay(1100)
            shown = s
        }
    }
    val street = Street.entries[shown]
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = cardWidthFor(5, maxWidth)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionLabel("Твои карты")
            DealtCardRow(streetsHole, w, key)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                streetsBoard.forEachIndexed { i, c ->
                    Box(Modifier.size(w, w * 1.4f)) {
                        Box(Modifier.matchParentSize().clip(RoundedCornerShape(w * 0.09f)).border(1.dp, MastColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(w * 0.09f)))
                        if (i < street.boardCards) DealtCard(c, w, index = 0, dealKey = "$key-$i")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Street.FLOP to 3, Street.TURN to 1, Street.RIVER to 1).forEach { (s, n) ->
                    val on = s == street
                    Text(
                        s.ruName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MastColors.Gold else if (s.ordinal <= street.ordinal) MastColors.TextSecondary else MastColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(w * n + 6.dp * (n - 1)),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Сейчас: ${street.ruName.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
        }
    }
}

@Composable
private fun PotMathVisual(pot: Int, call: Int, modifier: Modifier) {
    val share = call.toFloat() / (pot + call)
    val a = remember { Animatable(0f) }
    LaunchedEffect(share) { a.animateTo(share, tween(900, delayMillis = 300, easing = Motion.EmphasizedDecelerate)) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Банк" to pot, "Твой колл" to call, "Итоговый банк" to pot + call).forEach { (label, value) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$value", fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = MastColors.GoldLight)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = r)
            drawRoundRect(Brush.horizontalGradient(listOf(MastColors.GoldDark, MastColors.Gold)), size = Size(size.width * a.value, size.height), cornerRadius = r)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Нужно выигрывать хотя бы ${(share * 100).roundToInt()}% раз",
            style = MaterialTheme.typography.titleSmall,
            color = MastColors.TextPrimary,
        )
    }
}

/** 13×13 grid of starting hands coloured by the earliest seat that opens them. */
@Composable
fun StartingHandsChart(modifier: Modifier = Modifier, highlight: StartingHand? = null) {
    val ranks = Rank.entries.reversed()
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - 1.dp * 12) / 13
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                ranks.forEachIndexed { i, r1 ->
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        ranks.forEachIndexed { j, r2 ->
                            val hand = when {
                                i == j -> StartingHand(r1, r1, false)
                                i < j -> StartingHand(r1, r2, true)
                                else -> StartingHand(r2, r1, false)
                            }
                            val group = when {
                                PreflopChart.shouldOpen(hand, SeatGroup.EARLY) -> 0
                                PreflopChart.shouldOpen(hand, SeatGroup.MIDDLE) -> 1
                                PreflopChart.shouldOpen(hand, SeatGroup.LATE) -> 2
                                else -> 3
                            }
                            val bg = when (group) {
                                0 -> MastColors.Gold
                                1 -> MastColors.GoldLight.copy(alpha = 0.55f)
                                2 -> MastColors.Gold.copy(alpha = 0.22f)
                                else -> Color.White.copy(alpha = 0.05f)
                            }
                            val isHi = hand == highlight
                            Box(
                                Modifier
                                    .size(cell)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(bg)
                                    .then(if (isHi) Modifier.border(2.dp, MastColors.Ivory, RoundedCornerShape(3.dp)) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    hand.notation.take(if (hand.isPair) 2 else 3),
                                    fontSize = (cell.value * 0.3f).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (group <= 1) MastColors.FeltDeep else MastColors.TextSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                MastColors.Gold to "любая позиция",
                MastColors.GoldLight.copy(alpha = 0.55f) to "средняя",
                MastColors.Gold.copy(alpha = 0.22f) to "поздняя",
            ).forEach { (c, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                }
            }
        }
    }
}

/** Two hands; equity bars re-settle as the board comes street by street. */
@Composable
private fun EquityStreetsVisual(hero: List<Card>, villain: List<Card>, board: List<Card>, key: Any, modifier: Modifier) {
    val equities by produceState<List<Float>?>(null, hero, villain, board) {
        value = withContext(Dispatchers.Default) {
            listOf(0, 3, 4, 5).map { n ->
                val e = EquityCalculator.headsUp(hero, villain, board.take(n), samples = 3000)
                (e.win + e.tie / 2).toFloat()
            }
        }
    }
    val reduced = LocalReducedMotion.current
    var stage by remember(key) { mutableIntStateOf(if (reduced) 3 else 0) }
    LaunchedEffect(key, equities) {
        if (equities == null || reduced) return@LaunchedEffect
        stage = 0
        while (stage < 3) {
            delay(1800)
            stage++
        }
    }
    val eq = equities?.get(stage) ?: 0.5f
    val heroBar by animateFloatAsState(eq, tween(700, easing = Motion.EmphasizedDecelerate), label = "eq")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = cardWidthFor(5, maxWidth)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                board.forEachIndexed { i, c ->
                    val visibleCount = listOf(0, 3, 4, 5)[stage]
                    Box(Modifier.size(w, w * 1.4f)) {
                        Box(Modifier.matchParentSize().clip(RoundedCornerShape(w * 0.09f)).border(1.dp, MastColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(w * 0.09f)))
                        if (i < visibleCount) DealtCard(c, w, index = 0, dealKey = "$key-eq-$i")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            listOf(hero to heroBar, villain to 1f - heroBar).forEachIndexed { idx, (hand, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { hand.forEach { MiniCard(it, 26.dp) } }
                    Spacer(Modifier.width(10.dp))
                    Canvas(Modifier.weight(1f).height(12.dp)) {
                        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = r)
                        drawRoundRect(
                            if (idx == 0) Brush.horizontalGradient(listOf(MastColors.GoldDark, MastColors.Gold)) else Brush.horizontalGradient(listOf(Color(0xFF6B7F76), Color(0xFFA8B8B0))),
                            size = Size(size.width * value, size.height), cornerRadius = r,
                        )
                    }
                    Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall, color = MastColors.TextPrimary, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                }
            }
            Text(
                listOf("До флопа", "Флоп", "Тёрн", "Ривер")[stage],
                style = MaterialTheme.typography.labelMedium,
                color = MastColors.Gold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
