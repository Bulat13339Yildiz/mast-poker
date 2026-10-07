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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mast.poker.content.DrillType
import app.mast.poker.practice.Exercises
import app.mast.poker.practice.sim.HandSimulator
import app.mast.poker.practice.sim.HistoryEntry
import app.mast.poker.practice.sim.Quality
import app.mast.poker.practice.sim.SimView
import app.mast.poker.practice.sim.Verdict
import app.mast.poker.practice.sim.VillainStyle
import app.mast.poker.practice.sim.Who
import app.mast.poker.progress.DrillMode
import app.mast.poker.progress.Reward
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.ButtonTone
import app.mast.poker.ui.components.DealtCard
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.MiniCard
import app.mast.poker.ui.components.OptionState
import app.mast.poker.ui.components.OptionTile
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.RedChip
import app.mast.poker.ui.components.RichText
import app.mast.poker.ui.components.RollingNumber
import app.mast.poker.ui.components.SecondaryButton
import app.mast.poker.ui.components.drawChip
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.Playfair
import app.mast.poker.ui.theme.rememberHaptics
import app.mast.poker.ui.visuals.cardWidthFor
import kotlinx.coroutines.launch

private const val HANDS_PER_SESSION = 5

@Composable
fun HandSimScreen(nav: Nav) {
    var round by remember { mutableIntStateOf(0) }
    key(round) { HandSimSession(nav, onRestart = { round++ }) }
}

@Composable
private fun HandSimSession(nav: Nav, onRestart: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var handNo by remember { mutableIntStateOf(1) }
    val sim = remember(handNo) { HandSimulator() }
    var view by remember(handNo) { mutableStateOf(sim.view()) }
    var lastVerdict by remember { mutableStateOf<Verdict?>(null) }
    val allVerdicts = remember { mutableStateListOf<Verdict>() }
    var reward by remember { mutableStateOf<Reward?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var reviewing by remember(handNo) { mutableStateOf(false) }
    val good = allVerdicts.count { it.quality != Quality.MISTAKE }

    fun finishSession() {
        scope.launch { reward = app.finishDrill(DrillType.HAND_SIM, DrillMode.PRACTICE, good) }
    }

    val done = reward
    if (done != null) {
        ResultScreen(
            title = "Сессия завершена",
            subtitle = "Сыграй раздачу",
            correct = good,
            total = allVerdicts.size,
            reward = done,
            onContinue = nav::back,
            secondary = "Ещё $HANDS_PER_SESSION раздач" to onRestart,
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
                    Text("Сыграй раздачу", style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                    Text("Раздача $handNo из $HANDS_PER_SESSION", style = MaterialTheme.typography.labelMedium, color = MastColors.TextMuted)
                }
                Text("$good/${allVerdicts.size}", style = MaterialTheme.typography.titleMedium, color = MastColors.GoldLight)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 280.dp)) {
                Table(view, handNo)
                Spacer(Modifier.height(10.dp))
                Text(
                    view.style.tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MastColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
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
                    r != null -> HandOver(view, isLast = handNo >= HANDS_PER_SESSION, onReview = { reviewing = true }) {
                        if (handNo >= HANDS_PER_SESSION) finishSession() else handNo++
                    }
                    options.isNotEmpty() -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { o ->
                            OptionTile(o.label, OptionState.Idle, {
                                val verdictNow = sim.act(o.act)
                                allVerdicts += verdictNow
                                lastVerdict = verdictNow
                                view = sim.view()
                                if (verdictNow.quality == Quality.MISTAKE) haptics.error() else haptics.success()
                            }, Modifier.weight(1f).fillMaxHeight(), centered = true)
                        }
                    }
                    else -> Spacer(Modifier.height(1.dp))
                }
            }
        }

        AnimatedVisibility(reviewing, enter = fadeIn() + slideInVertically(Motion.cardSpring()) { it / 3 }, exit = fadeOut()) {
            HistorySheet(view, onClose = { reviewing = false })
        }

        MastDialog(
            visible = confirmExit,
            title = "Завершить сессию?",
            text = if (allVerdicts.isEmpty()) "Ты ещё не принял ни одного решения." else "Засчитаем решения, которые ты уже принял.",
            confirm = "Завершить",
            dismiss = "Играть дальше",
            onConfirm = {
                confirmExit = false
                if (allVerdicts.isEmpty()) nav.back() else finishSession()
            },
            onDismiss = { confirmExit = false },
        )
    }
}

@Composable
private fun Table(view: SimView, handNo: Int) {
    val showdown = view.result?.showdown == true
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(120.dp))
            .background(Brush.radialGradient(listOf(MastColors.FeltLight, MastColors.Felt, MastColors.FeltDark)))
            .border(6.dp, Brush.verticalGradient(listOf(Color(0xFF5A3A22), Color(0xFF2B1A0C))), RoundedCornerShape(120.dp))
            .padding(vertical = 22.dp, horizontal = 16.dp),
    ) {
        val w = cardWidthFor(5, maxWidth, cap = 56.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Соперник · ${view.villainSeat.short}", style = MaterialTheme.typography.labelMedium, color = MastColors.TextSecondary)
                Spacer(Modifier.width(8.dp))
                StyleChip(view.style)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                view.villain.forEachIndexed { i, c -> DealtCard(c, w * 0.9f, i, dealKey = "v$handNo", faceUp = showdown) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 5).forEach { i ->
                    Box(Modifier.size(w, w * 1.4f)) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(w * 0.09f)).border(1.dp, MastColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(w * 0.09f)))
                        if (i < view.board.size) DealtCard(view.board[i], w, index = 0, dealKey = "b$handNo-$i")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(28.dp, 20.dp)) { drawChip(center, size.width * 0.42f, RedChip, thickness = 4f) }
                Spacer(Modifier.width(6.dp))
                Text("Банк ", style = MaterialTheme.typography.titleSmall, color = MastColors.TextSecondary)
                RollingNumber(view.pot, MaterialTheme.typography.titleLarge.copy(fontFamily = Playfair, fontWeight = FontWeight.Bold), color = MastColors.GoldLight)
                if (view.toCall > 0 && view.awaitingHero && view.result == null) {
                    Text("  · к оплате ${view.toCall}", style = MaterialTheme.typography.labelLarge, color = MastColors.Wrong)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                view.hero.forEachIndexed { i, c -> DealtCard(c, w * 1.1f, 2 + i, dealKey = "h$handNo") }
            }
            Spacer(Modifier.height(6.dp))
            Text("Ты · ${view.position.short}", style = MaterialTheme.typography.labelMedium, color = MastColors.GoldLight)
        }
    }
}

@Composable
private fun VerdictPanel(v: Verdict, onNext: () -> Unit) {
    val tone = when (v.quality) {
        Quality.BEST -> MastColors.Correct
        Quality.OK -> MastColors.Gold
        Quality.MISTAKE -> MastColors.Wrong
    }
    GlassPanel(Modifier.fillMaxWidth(), tint = MastColors.FeltDark.copy(alpha = 0.97f), border = tone.copy(alpha = 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.quality.ruName, style = MaterialTheme.typography.headlineSmall, color = tone, modifier = Modifier.weight(1f))
            Text("${v.street} · ${v.chosen.ruName}", style = MaterialTheme.typography.labelLarge, color = MastColors.TextMuted)
        }
        if (v.quality != Quality.BEST) {
            Text("Лучше: ${v.best.ruName.lowercase()}", style = MaterialTheme.typography.titleSmall, color = MastColors.GoldLight)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            RichText(v.text, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Дальше", onNext, Modifier.fillMaxWidth(), tone = if (v.quality == Quality.MISTAKE) ButtonTone.Gold else ButtonTone.Correct, shimmer = false)
    }
}

@Composable
private fun HandOver(view: SimView, isLast: Boolean, onReview: () -> Unit, onNext: () -> Unit) {
    val r = view.result ?: return
    val tone = if (r.heroNet > 0) MastColors.Correct else if (r.heroNet < 0) MastColors.Wrong else MastColors.GoldLight
    GlassPanel(Modifier.fillMaxWidth(), tint = MastColors.FeltDark.copy(alpha = 0.97f), gilded = true) {
        val chips = Exercises.plural(kotlin.math.abs(r.heroNet), "фишка", "фишки", "фишек")
        Text(
            when {
                r.heroNet > 0 -> "+${r.heroNet} $chips"
                r.heroNet < 0 -> "−${-r.heroNet} $chips"
                else -> "При своих"
            },
            fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = tone,
        )
        Text(r.text, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        val good = view.verdicts.count { it.quality != Quality.MISTAKE }
        Text(
            "Решений в раздаче: ${view.verdicts.size}, верных: $good. Исход одной раздачи — случайность, важны решения.",
            style = MaterialTheme.typography.bodySmall, color = MastColors.TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Разбор", onReview, Modifier.weight(1f))
            PrimaryButton(if (isLast) "Итоги" else "Дальше", onNext, Modifier.weight(1.4f))
        }
    }
}

@Composable
private fun StyleChip(style: VillainStyle) {
    Text(
        style.ruName,
        style = MaterialTheme.typography.labelSmall,
        color = MastColors.GoldLight,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MastColors.FeltDeep.copy(alpha = 0.6f))
            .border(1.dp, MastColors.Gold.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** The whole hand street by street, with the coach's verdict on every decision. */
@Composable
private fun HistorySheet(view: SimView, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MastColors.Scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        GlassPanel(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .padding(10.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            tint = MastColors.FeltDark.copy(alpha = 0.98f),
            gilded = true,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Разбор раздачи", style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary, modifier = Modifier.weight(1f))
                RoundIconButton(MastIcons.Close, "Закрыть", onClose, size = 40.dp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Твои карты · ${view.position.short}", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { view.hero.forEach { MiniCard(it, 30.dp) } }
                }
                Column(Modifier.weight(1f)) {
                    Text("Соперник · ${view.style.ruName.lowercase()}", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { view.villain.forEach { MiniCard(it, 30.dp) } }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                view.history.forEach { e ->
                    when (e) {
                        is HistoryEntry.Street -> StreetHeader(e)
                        is HistoryEntry.Line -> HistoryLine(e)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StreetHeader(e: HistoryEntry.Street) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(e.street.ruName.uppercase(), style = MaterialTheme.typography.labelMedium, color = MastColors.Gold)
        Spacer(Modifier.width(10.dp))
        e.cards.forEach {
            MiniCard(it, 24.dp)
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("банк ${e.pot}", style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted)
    }
}

@Composable
private fun HistoryLine(e: HistoryEntry.Line) {
    val v = e.verdict
    when (e.who) {
        Who.TABLE -> Text(e.text, style = MaterialTheme.typography.bodySmall, color = MastColors.TextMuted)
        Who.VILLAIN -> Text(e.text, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
        Who.HERO -> {
            val tone = when (v?.quality) {
                Quality.BEST -> MastColors.Correct
                Quality.OK -> MastColors.Gold
                Quality.MISTAKE -> MastColors.Wrong
                null -> MastColors.TextPrimary
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(tone.copy(alpha = 0.08f))
                    .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.text, style = MaterialTheme.typography.titleSmall, color = MastColors.TextPrimary, modifier = Modifier.weight(1f))
                    if (v != null) Text(v.quality.ruName, style = MaterialTheme.typography.labelMedium, color = tone)
                }
                if (v != null) {
                    if (v.quality != Quality.BEST) {
                        Text("Лучше: ${v.best.ruName.lowercase()}", style = MaterialTheme.typography.labelMedium, color = MastColors.GoldLight)
                    }
                    Spacer(Modifier.height(4.dp))
                    RichText(v.text, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                }
            }
        }
    }
}
