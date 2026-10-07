package app.mast.poker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mast.poker.content.Question
import app.mast.poker.progress.Reward
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.ProgressRing
import app.mast.poker.ui.components.RollingNumber
import app.mast.poker.ui.quiz.FeedbackSheet
import app.mast.poker.ui.quiz.QuestionState
import app.mast.poker.ui.quiz.QuestionView
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.Playfair
import app.mast.poker.ui.theme.rememberHaptics
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How a finished session is summarised on the result screen. */
data class SessionSummary(val title: String, val subtitle: String, val scoreLabel: String? = null)

/**
 * Runs a stream of questions. With [timeLimitSec] it is a blitz: answers submit
 * instantly and the clock decides the end; otherwise [total] questions with full feedback.
 */
@Composable
fun QuizSession(
    title: String,
    total: Int?,
    timeLimitSec: Int?,
    nextQuestion: () -> Question,
    onFinish: suspend (results: List<Pair<Question, Boolean>>) -> Pair<Reward, SessionSummary>,
    onExit: () -> Unit,
) {
    var round by remember { mutableIntStateOf(0) }
    key(round) {
        SessionRound(title, total, timeLimitSec, nextQuestion, onFinish, onExit, onRestart = { round++ })
    }
}

@Composable
private fun SessionRound(
    title: String,
    total: Int?,
    timeLimitSec: Int?,
    nextQuestion: () -> Question,
    onFinish: suspend (List<Pair<Question, Boolean>>) -> Pair<Reward, SessionSummary>,
    onExit: () -> Unit,
    onRestart: () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val blitz = timeLimitSec != null

    val results = remember { mutableStateListOf<Pair<Question, Boolean>>() }
    var current by remember { mutableStateOf<QuestionState?>(null) }
    var pending by remember { mutableStateOf<Deferred<Question>?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableFloatStateOf(timeLimitSec?.toFloat() ?: 0f) }
    var finished by remember { mutableStateOf<Pair<Reward, SessionSummary>?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    fun prefetch() {
        pending = scope.async(Dispatchers.Default) { nextQuestion() }
    }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch { finished = onFinish(results.toList()) }
    }

    fun next() {
        if (total != null && results.size >= total) {
            finish()
            return
        }
        scope.launch {
            val q = (pending ?: scope.async(Dispatchers.Default) { nextQuestion() }).await()
            current = QuestionState(q)
            prefetch()
        }
    }

    fun submit(state: QuestionState) {
        if (state.checked || finishing) return
        val f = state.check()
        results += state.question to f.correct
        if (f.correct) {
            streak++
            haptics.success()
        } else {
            streak = 0
            shake++
            haptics.error()
        }
        scope.launch { app.answer(state.question, f.correct) }
        if (blitz) {
            scope.launch {
                delay(if (f.correct) 350 else 1200)
                if (!finishing) next()
            }
        }
    }

    LaunchedEffect(Unit) { next() }
    if (blitz) {
        LaunchedEffect(Unit) {
            var last = withFrameMillis { it }
            while (timeLeft > 0f && !finishing) {
                withFrameMillis { now ->
                    timeLeft = (timeLeft - (now - last) / 1000f).coerceAtLeast(0f)
                    last = now
                }
            }
            finish()
        }
    }

    val done = finished
    if (done != null) {
        val (reward, summary) = done
        ResultScreen(
            title = summary.title,
            subtitle = summary.subtitle,
            correct = results.count { it.second },
            total = results.size,
            reward = reward,
            onContinue = onExit,
            secondary = "Ещё раз" to onRestart,
            scoreLabel = summary.scoreLabel,
        )
        return
    }

    BackHandler { confirmExit = true }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(MastIcons.Close, "Завершить", { confirmExit = true })
                Spacer(Modifier.width(14.dp))
                if (blitz) {
                    ProgressRing(timeLeft / timeLimitSec!!, 44.dp, stroke = 4.dp, color = if (timeLeft < 10f) MastColors.Wrong else MastColors.Gold) {
                        Text("${timeLeft.toInt()}", style = MaterialTheme.typography.labelLarge, color = MastColors.TextPrimary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MastColors.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1)
                    RollingNumber(results.count { it.second }, MaterialTheme.typography.headlineMedium.copy(fontFamily = Playfair, fontWeight = FontWeight.Bold), color = MastColors.GoldLight)
                } else {
                    StepProgress(results.size, total ?: 1, Modifier.weight(1f))
                    Spacer(Modifier.width(14.dp))
                    Text("${results.size}/${total}", style = MaterialTheme.typography.labelLarge, color = MastColors.TextMuted)
                }
            }
            AnimatedVisibility(streak >= 3, enter = scaleIn(Motion.bouncy()) + fadeIn(), exit = fadeOut()) {
                Text(
                    "Серия: $streak подряд",
                    style = MaterialTheme.typography.labelLarge,
                    color = MastColors.Gold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            val q = current
            AnimatedContent(
                targetState = q,
                transitionSpec = {
                    (slideInHorizontally(Motion.cardSpring()) { it / 2 } + fadeIn(tween(Motion.Medium)))
                        .togetherWith(slideOutHorizontally(tween(Motion.Short)) { -it / 3 } + fadeOut(tween(Motion.Short)))
                },
                modifier = Modifier.weight(1f),
                label = "question",
            ) { state ->
                if (state == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ProgressRing(0.3f, 40.dp)
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 120.dp),
                    ) {
                        QuestionView(
                            state,
                            key = state,
                            shakeKey = if (state == current) shake else 0,
                            onInstant = if (blitz) ({ submit(state) }) else null,
                        )
                    }
                }
            }
        }

        val q = current
        if (!blitz && q != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (!q.checked) {
                    PrimaryButton("Проверить", { submit(q) }, Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), enabled = q.canCheck, shimmer = false)
                }
                AnimatedVisibility(
                    visible = q.feedback != null,
                    enter = slideInVertically(Motion.cardSpring()) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    q.feedback?.let { f -> FeedbackSheet(f, onNext = { next() }, nextLabel = if (total != null && results.size >= total) "Итоги" else "Дальше") }
                }
            }
        }

        MastDialog(
            visible = confirmExit,
            title = "Завершить тренировку?",
            text = if (results.isEmpty()) "Ты ещё не ответил ни на один вопрос." else "Засчитаем ответы, которые ты уже дал.",
            confirm = "Завершить",
            dismiss = "Продолжить",
            onConfirm = {
                confirmExit = false
                if (results.isEmpty()) onExit() else finish()
            },
            onDismiss = { confirmExit = false },
        )
    }
}
