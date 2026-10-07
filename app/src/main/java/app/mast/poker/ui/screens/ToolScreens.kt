package app.mast.poker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.EquityCalculator
import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandDescriber
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.Rank
import app.mast.poker.core.poker.RangeEquity
import app.mast.poker.core.poker.Suit
import app.mast.poker.core.poker.Variance
import app.mast.poker.practice.Explainer
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.MiniCard
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.SecondaryButton
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Playfair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

// --- Equity calculator -------------------------------------------------------------------

private enum class VillainMode(val title: String) { HAND("Рука"), RANDOM("Случайная"), RANGE("Топ-%") }

private enum class SlotGroup { HERO, VILLAIN, BOARD }

private data class Slot(val group: SlotGroup, val index: Int)

private data class EquityResult(val equity: Double, val win: Double? = null, val tie: Double? = null)

@Composable
fun EquityToolScreen(nav: Nav) {
    val hero = remember { mutableStateListOf<Card?>(null, null) }
    val villain = remember { mutableStateListOf<Card?>(null, null) }
    val board = remember { mutableStateListOf<Card?>(null, null, null, null, null) }
    var mode by remember { mutableStateOf(VillainMode.RANDOM) }
    var percent by remember { mutableFloatStateOf(20f) }
    var active by remember { mutableStateOf<Slot?>(Slot(SlotGroup.HERO, 0)) }
    var result by remember { mutableStateOf<EquityResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun list(g: SlotGroup) = when (g) {
        SlotGroup.HERO -> hero
        SlotGroup.VILLAIN -> villain
        SlotGroup.BOARD -> board
    }

    val used = (hero + villain + board).filterNotNull().toSet()

    fun nextEmpty(): Slot? {
        val order = buildList {
            add(Slot(SlotGroup.HERO, 0)); add(Slot(SlotGroup.HERO, 1))
            if (mode == VillainMode.HAND) { add(Slot(SlotGroup.VILLAIN, 0)); add(Slot(SlotGroup.VILLAIN, 1)) }
            (0 until 5).forEach { add(Slot(SlotGroup.BOARD, it)) }
        }
        return order.firstOrNull { list(it.group)[it.index] == null }
    }

    fun pick(card: Card) {
        val slot = active ?: nextEmpty() ?: return
        list(slot.group)[slot.index] = card
        active = nextEmpty()
    }

    val heroCards = hero.filterNotNull()
    val villainCards = villain.filterNotNull()
    val boardCards = board.filterNotNull()
    val ready = heroCards.size == 2 && boardCards.size != 1 && boardCards.size != 2 &&
        (mode != VillainMode.HAND || villainCards.size == 2)
    val key = listOf(heroCards, villainCards, boardCards, mode, percent.roundToInt())

    LaunchedEffect(key) {
        result = null
        if (!ready) return@LaunchedEffect
        busy = true
        delay(150) // let quick taps settle before simulating
        result = withContext(Dispatchers.Default) {
            when (mode) {
                VillainMode.HAND -> {
                    val e = EquityCalculator.headsUp(heroCards, villainCards, boardCards, samples = 20_000, random = Random(1))
                    EquityResult(e.win + e.tie / 2, e.win, e.tie)
                }
                VillainMode.RANDOM -> EquityResult(RangeEquity.vsRange(heroCards, HandRange(HandClasses.all.toSet()), boardCards, samples = 12_000, random = Random(2)))
                VillainMode.RANGE -> EquityResult(RangeEquity.vsRange(heroCards, HandRange.top(percent.roundToInt() / 100.0), boardCards, samples = 12_000, random = Random(3)))
            }
        }
        busy = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Калькулятор шансов", nav::back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                SlotRow("Твоя рука", hero, SlotGroup.HERO, active) { s -> hero[s.index] = null; active = s }
                Spacer(Modifier.height(14.dp))
                Text("Соперник", style = MaterialTheme.typography.labelMedium, color = MastColors.TextMuted)
                Spacer(Modifier.height(6.dp))
                Segments(VillainMode.entries.map { it.title }, mode.ordinal) { i ->
                    mode = VillainMode.entries[i]
                    if (mode != VillainMode.HAND) { villain[0] = null; villain[1] = null }
                    active = nextEmpty()
                }
                Spacer(Modifier.height(10.dp))
                when (mode) {
                    VillainMode.HAND -> SlotRow(null, villain, SlotGroup.VILLAIN, active) { s -> villain[s.index] = null; active = s }
                    VillainMode.RANDOM -> Text("Любые две карты, которых нет на столе.", style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                    VillainMode.RANGE -> Column {
                        Text("Топ-${percent.roundToInt()}% рук", style = MaterialTheme.typography.titleSmall, color = MastColors.GoldLight)
                        GoldSlider(percent, { percent = it }, 3f..60f)
                        Text(HandRange.top(percent.roundToInt() / 100.0).describe(), style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                SlotRow("Стол", board, SlotGroup.BOARD, active) { s -> board[s.index] = null; active = s }
            }

            EquityPanel(result, busy, ready, heroCards, boardCards, mode)

            GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                Text(
                    active?.let { "Выбери карту: " + slotName(it) } ?: "Все места заполнены — нажми на карту, чтобы заменить её",
                    style = MaterialTheme.typography.labelMedium, color = MastColors.TextMuted,
                )
                Spacer(Modifier.height(8.dp))
                CardPicker(used) { pick(it) }
            }
            SecondaryButton("Очистить", {
                hero.indices.forEach { hero[it] = null }
                villain.indices.forEach { villain[it] = null }
                board.indices.forEach { board[it] = null }
                active = Slot(SlotGroup.HERO, 0)
            }, Modifier.fillMaxWidth())
        }
    }
}

private fun slotName(s: Slot): String = when (s.group) {
    SlotGroup.HERO -> "твоя ${s.index + 1}-я карта"
    SlotGroup.VILLAIN -> "${s.index + 1}-я карта соперника"
    SlotGroup.BOARD -> when (s.index) {
        in 0..2 -> "флоп"
        3 -> "тёрн"
        else -> "ривер"
    }
}

@Composable
private fun SlotRow(label: String?, cards: List<Card?>, group: SlotGroup, active: Slot?, onTap: (Slot) -> Unit) {
    if (label != null) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MastColors.TextMuted)
        Spacer(Modifier.height(6.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cards.forEachIndexed { i, c ->
            val slot = Slot(group, i)
            val on = slot == active
            Box(
                Modifier
                    .size(44.dp, 62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) MastColors.Gold else MastColors.GlassStroke, RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onTap(slot) },
                contentAlignment = Alignment.Center,
            ) {
                if (c != null) MiniCard(c, 40.dp)
                else Text("+", style = MaterialTheme.typography.titleLarge, color = if (on) MastColors.Gold else MastColors.TextMuted)
            }
        }
    }
}

@Composable
private fun Segments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MastColors.Glass).border(1.dp, MastColors.GlassStroke, RoundedCornerShape(50)).padding(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) MastColors.FeltDeep else MastColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) MastColors.Gold else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun GoldSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int = 0) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MastColors.GoldLight,
            activeTrackColor = MastColors.Gold,
            inactiveTrackColor = MastColors.GlassStroke,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

/** 52 cards, a suit per row; cards already on the table are faded and inactive. */
@Composable
private fun CardPicker(used: Set<Card>, onPick: (Card) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 3.dp
        val w = (maxWidth - gap * 12) / 13
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            listOf(Suit.SPADES, Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS).forEach { suit ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Rank.entries.reversed().forEach { rank ->
                        val card = Card(rank, suit)
                        val taken = card in used
                        Box(Modifier.clickable(enabled = !taken, role = Role.Button) { onPick(card) }) {
                            MiniCard(card, w, dimmed = taken)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquityPanel(result: EquityResult?, busy: Boolean, ready: Boolean, hero: List<Card>, board: List<Card>, mode: VillainMode) {
    GlassPanel(Modifier.fillMaxWidth(), gilded = result != null, padding = PaddingValues(16.dp)) {
        when {
            !ready -> Text(
                "Выбери свои две карты" + (if (mode == VillainMode.HAND) " и карты соперника" else "") + ". На столе — 0, 3, 4 или 5 карт.",
                style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary,
            )
            result == null || busy -> Text("Считаю…", style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
            else -> {
                val anim by animateFloatAsState(result.equity.toFloat(), label = "eq")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(result.equity * 100).roundToInt()}%",
                        style = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 44.sp, fontFeatureSettings = "lnum"),
                        color = MastColors.GoldLight,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "твоя доля банка в среднем",
                        style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MastColors.Wrong.copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth(anim).height(8.dp).background(MastColors.Gold))
                }
                if (result.win != null && result.tie != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Победа ${pct1(result.win)} · ничья ${pct1(result.tie)} · проигрыш ${pct1(1 - result.win - result.tie)}",
                        style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary,
                    )
                }
                if (board.size >= 3) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Сейчас у тебя: " + HandDescriber.describe(HandEvaluator.evaluate(hero + board)),
                        style = MaterialTheme.typography.bodySmall, color = MastColors.TextMuted,
                    )
                }
            }
        }
    }
}

private fun pct1(x: Double) = String.format(Locale.ROOT, "%.1f", x * 100).replace('.', ',') + "%"

// --- Variance simulator ------------------------------------------------------------------

private const val NBSP = ' '

private val handSteps = listOf(1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000)

@Composable
fun VarianceToolScreen(nav: Nav) {
    var winRate by remember { mutableFloatStateOf(5f) }
    var sd by remember { mutableFloatStateOf(90f) }
    var handsIndex by remember { mutableFloatStateOf(3f) }
    var seed by remember { mutableIntStateOf(1) }
    val hands = handSteps[handsIndex.roundToInt()]
    val wr = (winRate * 2).roundToInt() / 2.0
    val sdValue = (sd / 5).roundToInt() * 5.0
    var paths by remember { mutableStateOf<List<FloatArray>>(emptyList()) }

    LaunchedEffect(wr, sdValue, hands, seed) {
        paths = withContext(Dispatchers.Default) { Variance.simulate(20, hands, wr, sdValue, Random(seed)) }
    }

    val expected = Variance.expected(wr, hands)
    val sigma = Variance.stdDev(sdValue, hands)
    val loss = if (wr > 0) Variance.lossChance(wr, sdValue, hands) else null

    Column(Modifier.fillMaxSize()) {
        TopBar("Симулятор дисперсии", nav::back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "20 игроков одного уровня играют по ${formatInt(hands)} раздач. Посмотри, как сильно расходятся их результаты.",
                style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary,
            )
            GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                VarianceChart(paths, hands, expected, sigma)
            }
            GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                StatLine("В среднем", signed(expected) + " BB")
                StatLine("Обычный разброс (σ)", "± " + formatInt(sigma.roundToInt()) + " BB")
                StatLine("95% игроков между", "${signed(expected - 2 * sigma)} и ${signed(expected + 2 * sigma)} BB")
                if (loss != null) StatLine("Шанс оказаться в минусе", "${(loss * 100).roundToInt()}%", highlight = true)
            }
            GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Text("Средний выигрыш: ${signedHalf(wr)} BB на 100 раздач", style = MaterialTheme.typography.titleSmall, color = MastColors.TextPrimary)
                GoldSlider(winRate, { winRate = it }, -5f..15f)
                Text("Разброс: ${sdValue.roundToInt()} BB на 100 раздач", style = MaterialTheme.typography.titleSmall, color = MastColors.TextPrimary)
                GoldSlider(sd, { sd = it }, 60f..120f)
                Text("Раздач: ${formatInt(hands)}", style = MaterialTheme.typography.titleSmall, color = MastColors.TextPrimary)
                GoldSlider(handsIndex, { handsIndex = it }, 0f..(handSteps.size - 1).toFloat(), steps = handSteps.size - 2)
            }
            Text(
                "Хороший игрок выигрывает около 5 BB на 100 раздач, а разброс в холдеме — около 90 BB. Поэтому несколько тысяч раздач ничего не доказывают: оценивай решения, а не результат вечера.",
                style = MaterialTheme.typography.bodySmall, color = MastColors.TextMuted,
            )
            PrimaryButton("Новые 20 игроков", { seed++ }, Modifier.fillMaxWidth(), shimmer = false)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = if (highlight) MastColors.Wrong else MastColors.GoldLight)
    }
}

@Composable
private fun VarianceChart(paths: List<FloatArray>, hands: Int, expected: Double, sigma: Double, height: Dp = 220.dp) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        if (paths.isEmpty()) return@Canvas
        val maxAbs = maxOf(paths.maxOf { p -> p.maxOf { abs(it) } }.toDouble(), abs(expected) + 2.2 * sigma).coerceAtLeast(1.0)
        val w = size.width
        val h = size.height
        fun y(v: Double) = (h / 2 - (v / maxAbs) * (h / 2 * 0.92)).toFloat()
        val n = paths.first().size - 1
        fun x(i: Int) = w * i / n.coerceAtLeast(1)
        // ±2σ band around the expected line, widening like √hands.
        val band = Path().apply {
            for (i in 0..n) {
                val e = expected * i / n
                val s = sigma * kotlin.math.sqrt(i.toDouble() / n)
                if (i == 0) moveTo(x(i), y(e + 2 * s)) else lineTo(x(i), y(e + 2 * s))
            }
            for (i in n downTo 0) {
                val e = expected * i / n
                val s = sigma * kotlin.math.sqrt(i.toDouble() / n)
                lineTo(x(i), y(e - 2 * s))
            }
            close()
        }
        drawPath(band, MastColors.Gold.copy(alpha = 0.10f))
        drawLine(MastColors.TextMuted, Offset(0f, y(0.0)), Offset(w, y(0.0)), 1.dp.toPx())
        paths.forEachIndexed { k, p ->
            val path = Path()
            p.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v.toDouble())) else path.lineTo(x(i), y(v.toDouble())) }
            val final = p.last()
            val color = if (final >= 0) MastColors.Correct else MastColors.Wrong
            drawPath(path, color.copy(alpha = 0.35f + 0.25f * (k % 3) / 2f), style = Stroke(1.2.dp.toPx()))
        }
        drawLine(MastColors.GoldLight, Offset(0f, y(0.0)), Offset(w, y(expected)), 2.dp.toPx())
    }
    Row(Modifier.fillMaxWidth()) {
        Text("0", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted, modifier = Modifier.weight(1f))
        Text("${formatInt(hands)} раздач", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
    }
}

private fun formatInt(n: Int): String = String.format(Locale.ROOT, "%,d", n).replace(',', NBSP)

private fun signed(x: Double): String {
    val r = x.roundToInt()
    return when {
        r > 0 -> "+" + formatInt(r)
        r < 0 -> "−" + formatInt(-r)
        else -> "0"
    }
}

private fun signedHalf(x: Double): String = when {
    x > 0 -> "+" + Explainer.bb(x)
    x < 0 -> "−" + Explainer.bb(-x)
    else -> "0"
}
