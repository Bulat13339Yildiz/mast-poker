package app.mast.poker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mast.poker.content.Curriculum
import app.mast.poker.content.Step
import app.mast.poker.progress.Reward
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.RichText
import app.mast.poker.ui.quiz.FeedbackSheet
import app.mast.poker.ui.quiz.QuestionState
import app.mast.poker.ui.quiz.QuestionView
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.rememberHaptics
import app.mast.poker.ui.visuals.ContentVisual
import kotlinx.coroutines.launch

@Composable
fun LessonScreen(lessonId: String, nav: Nav) {
    val lesson = remember(lessonId) { Curriculum.lesson(lessonId) }
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    val states = remember(lessonId) { lesson.steps.map { (it as? Step.Ask)?.let { a -> QuestionState(a.question) } } }
    var index by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var shake by remember { mutableIntStateOf(0) }
    var reward by remember { mutableStateOf<Reward?>(null) }
    var confirmExit by remember { mutableStateOf(false) }

    val done = reward
    if (done != null) {
        ResultScreen(
            title = if (correct == lesson.questionCount) "Безупречно!" else "Урок пройден",
            subtitle = lesson.title,
            correct = correct,
            total = lesson.questionCount,
            reward = done,
            onContinue = nav::back,
        )
        return
    }

    BackHandler { confirmExit = true }

    fun advance() {
        if (index < lesson.steps.lastIndex) {
            index++
        } else {
            scope.launch { reward = app.completeLesson(lessonId, correct, lesson.questionCount) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(MastIcons.Close, "Закрыть урок", { confirmExit = true })
                Spacer(Modifier.width(14.dp))
                StepProgress(index, lesson.steps.size, Modifier.weight(1f))
                Spacer(Modifier.width(14.dp))
                Text("${index + 1}/${lesson.steps.size}", style = MaterialTheme.typography.labelLarge, color = MastColors.TextMuted)
            }
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (slideInHorizontally(Motion.cardSpring()) { it / 2 } + fadeIn(tween(Motion.Medium)))
                        .togetherWith(slideOutHorizontally(tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)) { -it / 3 } + fadeOut(tween(Motion.Short)))
                },
                modifier = Modifier.weight(1f),
                label = "step",
            ) { i ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 120.dp),
                ) {
                    when (val step = lesson.steps[i]) {
                        is Step.Theory -> TheoryStep(step, key = "$lessonId-$i")
                        is Step.Remember -> RememberStep(step, key = "$lessonId-$i")
                        is Step.Ask -> QuestionView(states[i]!!, key = "$lessonId-$i", shakeKey = if (i == index) shake else 0)
                    }
                }
            }
        }

        val step = lesson.steps[index]
        val q = states[index]
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (q == null) {
                PrimaryButton(
                    if (step is Step.Remember) "Запомнил" else "Дальше",
                    { haptics.tap(); advance() },
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                )
            } else if (!q.checked) {
                PrimaryButton(
                    "Проверить",
                    {
                        val f = q.check()
                        if (f.correct) {
                            correct++
                            haptics.success()
                        } else {
                            shake++
                            haptics.error()
                        }
                        scope.launch { app.answer(q.question, f.correct) }
                    },
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                    enabled = q.canCheck,
                    shimmer = false,
                )
            }
            AnimatedVisibility(
                visible = q?.feedback != null,
                enter = slideInVertically(Motion.cardSpring()) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                q?.feedback?.let { f -> FeedbackSheet(f, onNext = { advance() }) }
            }
        }

        MastDialog(
            visible = confirmExit,
            title = "Выйти из урока?",
            text = "Прогресс этого урока не сохранится. Пройденные уроки останутся.",
            confirm = "Выйти",
            dismiss = "Остаться",
            onConfirm = { confirmExit = false; nav.back() },
            onDismiss = { confirmExit = false },
        )
    }
}

@Composable
private fun TheoryStep(step: Step.Theory, key: String) {
    Text(step.title, style = MaterialTheme.typography.headlineMedium, color = MastColors.TextPrimary)
    Spacer(Modifier.height(14.dp))
    step.visual?.let {
        ContentVisual(it, key)
        Spacer(Modifier.height(20.dp))
    }
    step.body.split("\n\n").forEach { paragraph ->
        RichText(paragraph, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RememberStep(step: Step.Remember, key: String) {
    Spacer(Modifier.height(24.dp))
    GlassPanel(Modifier.fillMaxWidth(), gilded = true, tint = MastColors.Gold.copy(alpha = 0.08f)) {
        GoldLabel("Запомни")
        Spacer(Modifier.height(16.dp))
        RichText(step.text, style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary)
    }
    step.visual?.let {
        Spacer(Modifier.height(20.dp))
        ContentVisual(it, key)
    }
}
