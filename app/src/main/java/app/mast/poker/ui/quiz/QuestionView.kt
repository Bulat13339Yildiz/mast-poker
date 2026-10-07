package app.mast.poker.ui.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mast.poker.content.Question
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.HandDescriber
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.Showdown
import app.mast.poker.practice.Answer
import app.mast.poker.practice.Grader
import app.mast.poker.ui.components.CardState
import app.mast.poker.ui.components.DealtCard
import app.mast.poker.ui.components.DealtCardRow
import app.mast.poker.ui.components.OptionState
import app.mast.poker.ui.components.OptionTile
import app.mast.poker.ui.components.RichText
import app.mast.poker.ui.components.shake
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.Playfair
import app.mast.poker.ui.visuals.ContentVisual
import app.mast.poker.ui.visuals.SectionLabel
import app.mast.poker.ui.visuals.TableVisual
import app.mast.poker.ui.visuals.cardWidthFor
import kotlin.math.roundToInt

/**
 * Renders any [Question] and edits [state]. With [onInstant] set (blitz), picking
 * an answer submits it right away instead of waiting for "Проверить".
 */
@Composable
fun QuestionView(state: QuestionState, key: Any, modifier: Modifier = Modifier, shakeKey: Int = 0, onInstant: (() -> Unit)? = null) {
    val q = state.question
    val select: (Answer) -> Unit = { a ->
        if (!state.checked) {
            state.answer = a
            onInstant?.invoke()
        }
    }
    Column(modifier.fillMaxWidth().shake(shakeKey)) {
        RichText(q.prompt, style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary)
        Spacer(Modifier.height(18.dp))
        when (q) {
            is Question.Choice -> ChoiceView(q, state, key, select)
            is Question.NameHand -> NameHandView(q, state, key, select)
            is Question.PickWinner -> WinnerView(q, state, key, select)
            is Question.PickBestFive -> BestFiveView(q, state, key, onInstant)
            is Question.OrderHands -> OrderView(q, state, onInstant)
            is Question.CountOuts -> OutsView(q, state, key, select)
            is Question.CallOrFold -> CallOrFoldView(q, state, select)
            is Question.OpenOrFold -> OpenOrFoldView(q, state, key, select)
            is Question.Favourite -> FavouriteView(q, state, key, select)
        }
    }
}

private fun pickState(index: Int, state: QuestionState): OptionState {
    val picked = (state.answer as? Answer.Pick)?.index
    if (!state.checked) return if (picked == index) OptionState.Selected else OptionState.Idle
    val correct = (Grader.correctAnswer(state.question) as Answer.Pick).index
    return when (index) {
        correct -> OptionState.Correct
        picked -> OptionState.Wrong
        else -> OptionState.Faded
    }
}

@Composable
private fun OptionsGrid(labels: List<String>, state: QuestionState, columns: Int, select: (Answer) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.indices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    OptionTile(labels[i], pickState(i, state), { select(Answer.Pick(i)) }, Modifier.weight(1f), enabled = !state.checked)
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChoiceView(q: Question.Choice, state: QuestionState, key: Any, select: (Answer) -> Unit) {
    q.visual?.let {
        ContentVisual(it, key)
        Spacer(Modifier.height(18.dp))
    }
    OptionsGrid(q.options, state, 1, select)
}

/** Hole cards and board in two labelled rows. */
@Composable
fun HandRows(hole: List<Card>, board: List<Card>, key: Any, stateOf: (Card) -> CardState = { CardState.Normal }, onClick: ((Card) -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = cardWidthFor(5, maxWidth)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hole.isNotEmpty()) {
                SectionLabel("Твои карты")
                DealtCardRow(hole, w, key, stateOf = stateOf, onCardClick = onClick)
                Spacer(Modifier.height(12.dp))
                SectionLabel("Стол")
            }
            DealtCardRow(board, w, key, firstIndex = hole.size, stateOf = stateOf, onCardClick = onClick)
        }
    }
}

private fun bestFiveStates(hole: List<Card>, board: List<Card>, checked: Boolean): (Card) -> CardState {
    if (!checked) return { CardState.Normal }
    val best = HandEvaluator.evaluate(hole + board).cards.toSet()
    return { c -> if (c in best) CardState.Winning else CardState.Dimmed }
}

@Composable
private fun NameHandView(q: Question.NameHand, state: QuestionState, key: Any, select: (Answer) -> Unit) {
    HandRows(q.hole, q.board, key, bestFiveStates(q.hole, q.board, state.checked))
    Spacer(Modifier.height(22.dp))
    OptionsGrid(q.options.map { it.ruName }, state, 2, select)
}

@Composable
private fun SelectablePanel(
    selected: Boolean,
    result: OptionState?,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val border by animateColorAsState(
        when (result) {
            OptionState.Correct -> MastColors.Correct
            OptionState.Wrong -> MastColors.Wrong
            else -> if (selected) MastColors.Gold else MastColors.GlassStroke
        },
        label = "panelBorder",
    )
    val scale by animateFloatAsState(if (selected && result == null) 1.02f else 1f, Motion.bouncy(), label = "panelScale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MastColors.Gold.copy(alpha = 0.10f) else MastColors.Glass)
            .border(1.5.dp, border, RoundedCornerShape(18.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun WinnerView(q: Question.PickWinner, state: QuestionState, key: Any, select: (Answer) -> Unit) {
    val result = remember(q) { Showdown.resolve(q.board, q.hands) }
    val picked = (state.answer as? Answer.Pick)?.index
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = cardWidthFor(5, maxWidth)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionLabel("Стол")
            DealtCardRow(q.board, w, key, stateOf = { c ->
                if (!state.checked) CardState.Normal
                else if (result.winners.any { c in result.values[it].cards }) CardState.Winning else CardState.Dimmed
            })
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                q.hands.forEachIndexed { i, hand ->
                    SelectablePanel(
                        selected = picked == i,
                        result = if (state.checked) pickState(i, state).takeIf { it != OptionState.Faded } else null,
                        onClick = { select(Answer.Pick(i)) },
                        enabled = !state.checked,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Игрок ${i + 1}", style = MaterialTheme.typography.labelMedium, color = MastColors.TextSecondary)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                hand.forEachIndexed { j, c ->
                                    DealtCard(c, w * 0.85f, 5 + i * 2 + j, key, state = if (state.checked && i in result.winners && c in result.values[i].cards) CardState.Winning else CardState.Normal)
                                }
                            }
                            AnimatedVisibility(state.checked, enter = fadeIn() + expandVertically()) {
                                Text(
                                    HandDescriber.describe(result.values[i]).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (i in result.winners) MastColors.GoldLight else MastColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OptionTile("Ничья — банк делится", pickState(q.hands.size, state), { select(Answer.Pick(q.hands.size)) }, enabled = !state.checked)
        }
    }
}

@Composable
private fun BestFiveView(q: Question.PickBestFive, state: QuestionState, key: Any, onInstant: (() -> Unit)?) {
    val selected = state.selection.toSet()
    val closest = remember(q, state.checked) {
        if (!state.checked) emptySet()
        else HandEvaluator.allBestFives(q.hole + q.board).maxBy { alt -> alt.count { it in selected } }.toSet()
    }
    val stateOf: (Card) -> CardState = { c ->
        when {
            !state.checked -> if (c in selected) CardState.Selected else CardState.Normal
            c in closest -> CardState.Winning
            c in selected -> CardState.Wrong
            else -> CardState.Dimmed
        }
    }
    HandRows(q.hole, q.board, key, stateOf) { c ->
        state.toggle(c)
        if (state.answer != null) onInstant?.invoke()
    }
    Spacer(Modifier.height(14.dp))
    val count = selected.size
    Text(
        if (state.checked) "Лучшая пятёрка подсвечена золотом" else "Выбрано: $count из 5",
        style = MaterialTheme.typography.titleSmall,
        color = if (count == 5 || state.checked) MastColors.GoldLight else MastColors.TextSecondary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun OrderView(q: Question.OrderHands, state: QuestionState, onInstant: (() -> Unit)?) {
    val order = state.partialOrder
    Text("Нажимай по очереди — от самой слабой к самой сильной", style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        q.categories.forEach { cat ->
            val pos = order.indexOf(cat)
            val correctPos = q.answer.indexOf(cat)
            val result = when {
                !state.checked -> null
                pos == correctPos -> OptionState.Correct
                else -> OptionState.Wrong
            }
            SelectablePanel(
                selected = pos >= 0,
                result = result,
                enabled = !state.checked,
                onClick = {
                    if (pos >= 0) {
                        while (order.size > pos) order.removeAt(order.lastIndex)
                    } else {
                        order += cat
                    }
                    state.answer = if (order.size == q.categories.size) Answer.Order(order.toList()) else null
                    if (state.answer != null) onInstant?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (pos >= 0) MastColors.Gold else Color.Transparent)
                            .border(1.dp, MastColors.Gold.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        val label = if (state.checked) "${correctPos + 1}" else if (pos >= 0) "${pos + 1}" else ""
                        Text(label, fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (pos >= 0) MastColors.FeltDeep else MastColors.GoldLight)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(cat.ruName, style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun OutsView(q: Question.CountOuts, state: QuestionState, key: Any, select: (Answer) -> Unit) {
    HandRows(q.hole, q.board, key)
    Spacer(Modifier.height(22.dp))
    OptionsGrid(q.options.map { "$it" }, state, 4, select)
}

@Composable
private fun InfoCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MastColors.Glass)
            .border(1.dp, MastColors.GlassStroke, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = MastColors.GoldLight)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DecisionRow(yesLabel: String, noLabel: String, state: QuestionState, select: (Answer) -> Unit) {
    val picked = (state.answer as? Answer.Decision)?.yes
    val correct = if (state.checked) (Grader.correctAnswer(state.question) as Answer.Decision).yes else null
    fun st(v: Boolean): OptionState = when {
        correct == null -> if (picked == v) OptionState.Selected else OptionState.Idle
        v == correct -> OptionState.Correct
        v == picked -> OptionState.Wrong
        else -> OptionState.Faded
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionTile(noLabel, st(false), { select(Answer.Decision(false)) }, Modifier.weight(1f), enabled = !state.checked)
        OptionTile(yesLabel, st(true), { select(Answer.Decision(true)) }, Modifier.weight(1f), enabled = !state.checked)
    }
}

@Composable
private fun CallOrFoldView(q: Question.CallOrFold, state: QuestionState, select: (Answer) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCell("${q.pot}", "банк со ставкой", Modifier.weight(1f))
        InfoCell("${q.call}", "нужно доплатить", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCell("${q.outs}", "аутов", Modifier.weight(1f))
        InfoCell("${q.cardsToCome}", if (q.cardsToCome == 1) "карта впереди" else "карты впереди", Modifier.weight(1f))
    }
    Spacer(Modifier.height(22.dp))
    DecisionRow("Колл", "Фолд", state, select)
}

@Composable
private fun OpenOrFoldView(q: Question.OpenOrFold, state: QuestionState, key: Any, select: (Answer) -> Unit) {
    TableVisual(q.position)
    Spacer(Modifier.height(14.dp))
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DealtCardRow(q.hole, cardWidthFor(5, maxWidth) * 1.1f, key)
    }
    Spacer(Modifier.height(22.dp))
    DecisionRow("Повысить", "Пас", state, select)
}

@Composable
private fun FavouriteView(q: Question.Favourite, state: QuestionState, key: Any, select: (Answer) -> Unit) {
    val picked = (state.answer as? Answer.Pick)?.index
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = cardWidthFor(5, maxWidth)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (q.board.isNotEmpty()) {
                SectionLabel(if (q.board.size == 3) "Флоп" else "Тёрн")
                DealtCardRow(q.board, w, key)
                Spacer(Modifier.height(16.dp))
            } else {
                Text("До флопа", style = MaterialTheme.typography.labelMedium, color = MastColors.TextMuted)
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                q.hands.forEachIndexed { i, hand ->
                    val share = if (i == 0) q.equity else 1 - q.equity
                    SelectablePanel(
                        selected = picked == i,
                        result = if (state.checked) pickState(i, state).takeIf { it != OptionState.Faded } else null,
                        onClick = { select(Answer.Pick(i)) },
                        enabled = !state.checked,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Рука ${i + 1}", style = MaterialTheme.typography.labelMedium, color = MastColors.TextSecondary)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                hand.forEachIndexed { j, c -> DealtCard(c, w * 0.85f, 5 + i * 2 + j, key) }
                            }
                            AnimatedVisibility(state.checked, enter = fadeIn() + expandVertically()) {
                                EquityBar(share)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquityBar(share: Double, width: Dp = 110.dp) {
    val anim by animateFloatAsState(share.toFloat(), Motion.enter(700), label = "equity")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        Text("${(share * 100).roundToInt()}%", fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = if (share > 0.5) MastColors.GoldLight else MastColors.TextSecondary)
        Box(Modifier.width(width).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.width(width * anim).height(6.dp).background(if (share > 0.5) MastColors.Gold else MastColors.TextMuted))
        }
    }
}
