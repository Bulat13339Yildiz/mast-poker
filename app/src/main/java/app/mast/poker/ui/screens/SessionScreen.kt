package app.mast.poker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mast.poker.content.DrillType
import app.mast.poker.practice.Exercises
import app.mast.poker.practice.sim.Act
import app.mast.poker.practice.sim.PlayerView
import app.mast.poker.practice.sim.Quality
import app.mast.poker.practice.sim.TableHand
import app.mast.poker.practice.sim.TableView
import app.mast.poker.practice.sim.Verdict
import app.mast.poker.practice.sim.VillainStyle
import app.mast.poker.progress.DrillMode
import app.mast.poker.progress.Reward
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.DealtCard
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.MiniCard
import app.mast.poker.ui.components.OptionState
import app.mast.poker.ui.components.OptionTile
import app.mast.poker.ui.components.PlayingCard
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.RedChip
import app.mast.poker.ui.components.RichText
import app.mast.poker.ui.components.RollingNumber
import app.mast.poker.ui.components.SecondaryButton
import app.mast.poker.ui.components.drawChip
import app.mast.poker.ui.theme.LocalSounds
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.Playfair
import app.mast.poker.ui.theme.rememberHaptics
import app.mast.poker.ui.visuals.cardWidthFor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

private const val SESSION_HANDS = 12
private const val BUY_IN = 200
private const val REBUY_BELOW = 40

@Composable
fun SessionScreen(nav: Nav) {
    var round by remember { mutableIntStateOf(0) }
    key(round) { SessionRun(nav, onRestart = { round++ }) }
}

@Composable
private fun SessionRun(nav: Nav, onRestart: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val sounds = LocalSounds.current
    val random = remember { Random(System.nanoTime()) }
    val styles = remember { listOf<VillainStyle?>(null) + List(5) { VillainStyle.entries.random(random) } }
    val firstButton = remember { random.nextInt(6) }
    var handNo by remember { mutableIntStateOf(1) }
    var stack by remember { mutableIntStateOf(BUY_IN) }
    var rebuys by remember { mutableIntStateOf(0) }
    val hand = remember(handNo) { TableHand(Random(random.nextLong()), stack, (firstButton + handNo) % 6, styles) }
    var view by remember(handNo) { mutableStateOf(hand.view()) }
    var lastVerdict by remember { mutableStateOf<Verdict?>(null) }
    val allVerdicts = remember { mutableStateListOf<Verdict>() }
    var reward by remember { mutableStateOf<Reward?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var reviewing by remember(handNo) { mutableStateOf(false) }
    val good = allVerdicts.count { it.quality != Quality.MISTAKE }
    val invested = BUY_IN * (rebuys + 1)

    fun finishSession() {
        scope.launch { reward = app.finishDrill(DrillType.HAND_SIM, DrillMode.SESSION, good) }
    }

    fun nextHand() {
        stack = hand.heroStackAfter
        if (handNo >= SESSION_HANDS) {
            finishSession()
            return
        }
        // A short stack tops up to the buy-in, like a real rebuy.
        if (stack < REBUY_BELOW) {
            stack = BUY_IN
            rebuys++
        }
        handNo++
    }

    val done = reward
    if (done != null) {
        val result = stack - invested
        ResultScreen(
            title = "Сессия окончена",
            subtitle = "Стек: $BUY_IN → $stack" + (if (rebuys > 0) " · докупок: $rebuys" else "") +
                " · итог ${if (result >= 0) "+" else "−"}${abs(result)} ${Exercises.plural(abs(result), "фишка", "фишки", "фишек")}",
            correct = good,
            total = allVerdicts.size,
            reward = done,
            onContinue = nav::back,
            secondary = "Новая сессия" to onRestart,
            scoreLabel = "$good из ${allVerdicts.size} решений верно",
        )
        return
    }

    BackHandler { if (reviewing) reviewing = false else confirmExit = true }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(MastIcons.Close, "Завершить", { confirmExit = true })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Сессия за столом", style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                    Text("Раздача $handNo из $SESSION_HANDS · решений верно $good/${allVerdicts.size}", style = MaterialTheme.typography.labelMedium, color = MastColors.TextMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("стек", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
                    RollingNumber(view.heroStack, MaterialTheme.typography.titleLarge.copy(fontFamily = Playfair, fontWeight = FontWeight.Bold), color = MastColors.GoldLight)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 260.dp)) {
                SessionTable(view, handNo)
                Spacer(Modifier.height(10.dp))
                AnimatedContent(view.lastLog, transitionSpec = { fadeIn(tween(Motion.Medium)).togetherWith(fadeOut(tween(Motion.Short))) }, label = "log") { line ->
                    RichText(line, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            val verdict = lastVerdict
            val result = view.result
            AnimatedContent(
                targetState = Triple(verdict, result, view.options),
                transitionSpec = { (slideInVertically(Motion.cardSpring()) { it / 2 } + fadeIn()).togetherWith(fadeOut(tween(Motion.Short))) },
                label = "bottom",
            ) { (v, r, options) ->
                when {
                    v != null -> VerdictPanel(v) { lastVerdict = null }
                    r != null -> GlassPanel(Modifier.fillMaxWidth(), tint = MastColors.FeltDark.copy(alpha = 0.97f), gilded = true) {
                        val tone = if (r.heroNet > 0) MastColors.Correct else if (r.heroNet < 0) MastColors.Wrong else MastColors.GoldLight
                        val chips = Exercises.plural(abs(r.heroNet), "фишка", "фишки", "фишек")
                        Text(
                            when {
                                r.heroNet > 0 -> "+${r.heroNet} $chips"
                                r.heroNet < 0 -> "−${-r.heroNet} $chips"
                                else -> "При своих"
                            },
                            fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = tone,
                        )
                        Text(r.text, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton("Разбор", { reviewing = true }, Modifier.weight(1f))
                            PrimaryButton(if (handNo >= SESSION_HANDS) "Итоги" else "Дальше", ::nextHand, Modifier.weight(1.4f))
                        }
                    }
                    options.isNotEmpty() -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { o ->
                            OptionTile(o.label, OptionState.Idle, {
                                if (o.act == Act.CALL || o.act == Act.BET || o.act == Act.RAISE) sounds.chip()
                                val v2 = hand.act(o.act)
                                app.recordDecision(v2)
                                allVerdicts += v2
                                lastVerdict = v2
                                view = hand.view()
                                if (v2.quality == Quality.MISTAKE) haptics.error() else haptics.success()
                            }, Modifier.weight(1f).fillMaxHeight(), centered = true)
                        }
                    }
                    else -> Spacer(Modifier.height(1.dp))
                }
            }
        }

        AnimatedVisibility(reviewing, enter = fadeIn() + slideInVertically(Motion.cardSpring()) { it / 3 }, exit = fadeOut()) {
            val rows = listOf("Твои карты · ${view.heroPosition.short}" to view.players.first { it.style == null }.cards) +
                view.players.filter { it.style != null && it.inHand }.map { p -> "${p.position.short} · ${p.style!!.ruName.lowercase()}" to p.cards }
            HandHistorySheet(rows, view.history, onClose = { reviewing = false })
        }

        MastDialog(
            visible = confirmExit,
            title = "Завершить сессию?",
            text = if (allVerdicts.isEmpty()) "Ты ещё не принял ни одного решения." else "Засчитаем решения, которые ты уже принял.",
            confirm = "Завершить",
            dismiss = "Играть дальше",
            onConfirm = {
                confirmExit = false
                if (allVerdicts.isEmpty()) nav.back() else {
                    stack = if (view.result != null) hand.heroStackAfter else stack
                    finishSession()
                }
            },
            onDismiss = { confirmExit = false },
        )
    }
}

@Composable
private fun SessionTable(view: TableView, handNo: Int) {
    val showdown = view.result?.showdown == true
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(64.dp))
            .background(Brush.radialGradient(listOf(MastColors.FeltLight, MastColors.Felt, MastColors.FeltDark)))
            .border(6.dp, Brush.verticalGradient(listOf(Color(0xFF5A3A22), Color(0xFF2B1A0C))), RoundedCornerShape(64.dp))
            .padding(vertical = 18.dp, horizontal = 10.dp),
    ) {
        val w = cardWidthFor(5, maxWidth, cap = 54.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                view.players.filter { it.style != null }
                    .sortedBy { app.mast.poker.practice.sim.HandSimulator.PreflopOrder.indexOf(it.position) }
                    .forEach { p -> SeatChip(p, showdown, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 5).forEach { i ->
                    Box(Modifier.size(w, w * 1.4f)) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(w * 0.09f)).border(1.dp, MastColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(w * 0.09f)))
                        if (i < view.board.size) DealtCard(view.board[i], w, index = 0, dealKey = "s$handNo-$i")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(28.dp, 20.dp)) { drawChip(center, size.width * 0.42f, RedChip, thickness = 4f) }
                Spacer(Modifier.width(6.dp))
                Text("Банк ", style = MaterialTheme.typography.titleSmall, color = MastColors.TextSecondary)
                RollingNumber(view.pot, MaterialTheme.typography.titleLarge.copy(fontFamily = Playfair, fontWeight = FontWeight.Bold), color = MastColors.GoldLight)
                if (view.toCall > 0 && view.options.isNotEmpty()) {
                    Text("  · к оплате ${view.toCall}", style = MaterialTheme.typography.labelLarge, color = MastColors.Wrong)
                }
            }
            Spacer(Modifier.height(12.dp))
            val hero = view.players.first { it.style == null }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                hero.cards!!.forEachIndexed { i, c -> DealtCard(c, w * 1.1f, 2 + i, dealKey = "sh$handNo") }
            }
            Spacer(Modifier.height(6.dp))
            Text("Ты · ${view.heroPosition.short}", style = MaterialTheme.typography.labelMedium, color = MastColors.GoldLight)
        }
    }
}

/** A bot at the table: seat, style, cards (face up at showdown) and last action. */
@Composable
private fun SeatChip(p: PlayerView, showdown: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .graphicsLayer { alpha = if (p.inHand) 1f else 0.38f }
            .clip(RoundedCornerShape(12.dp))
            .background(MastColors.FeltDeep.copy(alpha = 0.55f))
            .border(1.dp, if (p.inHand) MastColors.Gold.copy(alpha = 0.5f) else MastColors.GlassStroke, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(p.position.short, style = MaterialTheme.typography.labelMedium, color = MastColors.TextPrimary)
        Text(p.style!!.short, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MastColors.GoldLight, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val cards = p.cards
            if (cards != null && showdown) cards.forEach { MiniCard(it, 20.dp) }
            else if (p.inHand) repeat(2) { PlayingCard(null, 16.dp, flip = 0f) }
            else Spacer(Modifier.height(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(p.action ?: " ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MastColors.TextSecondary, maxLines = 1)
    }
}
