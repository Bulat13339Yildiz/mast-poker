package app.mast.poker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.Rank
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

private val GridRanks = Rank.entries.reversed()
private const val N = 13

/** Cell of the 13×13 chart: pairs on the diagonal, suited hands above it, offsuit below. */
fun gridHand(row: Int, col: Int): StartingHand {
    val r1 = GridRanks[row]
    val r2 = GridRanks[col]
    return when {
        row == col -> StartingHand(r1, r1, false)
        row < col -> StartingHand(r1, r2, true)
        else -> StartingHand(r2, r1, false)
    }
}

private val AllCells: List<StartingHand> = List(N * N) { gridHand(it / N, it % N) }

private fun baseColor(h: StartingHand): Color = Color.White.copy(
    alpha = when {
        h.isPair -> 0.12f
        h.suited -> 0.075f
        else -> 0.045f
    },
)

/**
 * The 13×13 starting-hand grid. With [onChange] the player paints cells: the first cell
 * touched decides whether the drag adds or removes hands. With [target] (after checking)
 * cells show hits, extras and misses. Read-only grids fill in with a diagonal wave.
 */
@Composable
fun RangeGrid(
    selected: Set<StartingHand>,
    modifier: Modifier = Modifier,
    target: Set<StartingHand>? = null,
    onChange: ((Set<StartingHand>) -> Unit)? = null,
) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val interactive = onChange != null
    val anims = remember {
        AllCells.associateWith { h -> Animatable(if (h in selected && (interactive || reduced)) 1f else 0f) }
    }
    LaunchedEffect(selected) {
        AllCells.forEachIndexed { i, h ->
            val goal = if (h in selected) 1f else 0f
            val a = anims.getValue(h)
            if (a.targetValue == goal) return@forEachIndexed
            scope.launch {
                if (!interactive && !reduced) delay((i / N + i % N) * 22L)
                if (reduced) a.snapTo(goal) else a.animateTo(goal, tween(if (interactive) 170 else 260))
            }
        }
    }
    val current by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onChange)
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val gap = 2.dp
        val cell = (maxWidth - gap * (N - 1)) / N
        val labels = remember(cell) {
            val style = TextStyle(fontSize = (cell.value * 0.3f).sp, fontWeight = FontWeight.SemiBold)
            AllCells.associateWith { h -> measurer.measure(h.notation, style) }
        }
        val gapPx = with(density) { gap.toPx() }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(maxWidth)
                .pointerInput(interactive) {
                    if (!interactive) return@pointerInput
                    fun cellAt(p: Offset): StartingHand? {
                        val step = (size.width + gapPx) / N
                        val col = floor(p.x / step).toInt()
                        val row = floor(p.y / step).toInt()
                        return if (row in 0 until N && col in 0 until N) gridHand(row, col) else null
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val first = cellAt(down.position) ?: return@awaitEachGesture
                        down.consume()
                        val adding = first !in current
                        var sel = current
                        fun paint(h: StartingHand) {
                            sel = if (adding) sel + h else sel - h
                            change?.invoke(sel)
                            haptics.tick()
                        }
                        paint(first)
                        var last = first
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            val h = cellAt(c.position) ?: continue
                            if (h == last) continue
                            last = h
                            if ((h in sel) != adding) paint(h)
                        }
                    }
                },
        ) {
            val step = (size.width + gapPx) / N
            val side = step - gapPx
            val radius = CornerRadius(side * 0.14f)
            for (i in AllCells.indices) {
                val h = AllCells[i]
                val p = anims.getValue(h).value
                // A short swell while a cell changes state.
                val swell = if (interactive) sin(p * PI).toFloat() * 0.12f * side else 0f
                val topLeft = Offset((i % N) * step - swell / 2, (i / N) * step - swell / 2)
                val cellSize = Size(side + swell, side + swell)
                val base = baseColor(h)
                val (fill, ink, stroke) = if (target == null) {
                    Triple(lerp(base, MastColors.Gold, p), lerp(MastColors.TextSecondary, MastColors.FeltDeep, p), null)
                } else {
                    val inTarget = h in target
                    val picked = h in current
                    when {
                        inTarget && picked -> Triple(MastColors.Gold, MastColors.FeltDeep, null)
                        picked -> Triple(MastColors.Wrong.copy(alpha = 0.85f), MastColors.Ivory, null)
                        inTarget -> Triple(MastColors.Gold.copy(alpha = 0.2f), MastColors.GoldLight, MastColors.Wrong)
                        else -> Triple(base, MastColors.TextMuted, null)
                    }
                }
                drawRoundRect(fill, topLeft, cellSize, radius)
                if (stroke != null) drawRoundRect(stroke, topLeft, cellSize, radius, style = Stroke(1.5.dp.toPx()))
                val label = labels.getValue(h)
                drawText(
                    label,
                    color = ink,
                    topLeft = Offset(
                        topLeft.x + (cellSize.width - label.size.width) / 2f,
                        topLeft.y + (cellSize.height - label.size.height) / 2f,
                    ),
                )
            }
        }
    }
}

private val LateOpen: Set<StartingHand> by lazy { AllCells.filter { PreflopChart.shouldOpen(it, SeatGroup.LATE) }.toSet() }

/** Label-free miniature of a range — drill and chapter art. */
@Composable
fun MiniRange(size: Dp, modifier: Modifier = Modifier, hands: Set<StartingHand> = remember { LateOpen }) {
    Canvas(modifier.size(size)) { drawMiniRange(hands) }
}

private fun DrawScope.drawMiniRange(hands: Set<StartingHand>) {
    val gap = size.width * 0.012f
    val step = (size.width + gap) / N
    val side = step - gap
    for (i in AllCells.indices) {
        val h = AllCells[i]
        val color = if (h in hands) MastColors.Gold else baseColor(h)
        drawRoundRect(color, Offset((i % N) * step, (i / N) * step), Size(side, side), CornerRadius(side * 0.2f))
    }
}
