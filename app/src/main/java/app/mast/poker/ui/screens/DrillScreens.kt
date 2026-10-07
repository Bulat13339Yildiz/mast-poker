package app.mast.poker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mast.poker.content.DrillType
import app.mast.poker.practice.Exercises
import app.mast.poker.progress.DrillMode
import app.mast.poker.progress.Rating
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.theme.MastColors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private const val PRACTICE_ROUND = 10
private const val BLITZ_SECONDS = 60

@Composable
fun DrillScreen(type: DrillType, mode: DrillMode, nav: Nav) {
    if (type == DrillType.HAND_SIM) {
        HandSimScreen(nav)
        return
    }
    val app = LocalApp.current
    val progress by app.progress.collectAsStateWithLifecycle()
    val exercises = remember { Exercises() }
    val random = remember { Random(System.nanoTime()) }
    val bestBefore = remember { progress?.stats?.blitzBest?.get(type) ?: 0 }
    val blitz = mode == DrillMode.BLITZ

    QuizSession(
        title = type.title,
        total = if (blitz) null else PRACTICE_ROUND,
        timeLimitSec = if (blitz) BLITZ_SECONDS else null,
        // The level follows the player's rating in this drill's concept.
        nextQuestion = { exercises.next(type, Rating.level(app.progress.value?.rating(type.concept) ?: Rating.START, random)) },
        difficultyOf = { q -> Rating.difficulty(exercises.levelOf(q)) },
        onFinish = { results ->
            val correct = results.count { it.second }
            val reward = app.finishDrill(type, mode, correct)
            val summary = if (blitz) {
                SessionSummary(
                    title = if (correct > bestBefore && correct > 0) "Новый рекорд!" else "Время вышло!",
                    subtitle = type.title,
                    scoreLabel = "$correct верных за $BLITZ_SECONDS секунд",
                )
            } else {
                SessionSummary("Тренировка завершена", type.title)
            }
            reward to summary
        },
        onExit = nav::back,
    )
}

@Composable
fun ReviewScreen(nav: Nav) {
    val app = LocalApp.current
    val progress by app.progress.collectAsStateWithLifecycle()
    val p = progress ?: return
    val exercises = remember { Exercises() }
    // Fix the queue when the screen opens so it does not shift while answering.
    val queue = remember {
        p.dueMistakes(app.today()).sortedBy { it.dueDay }.take(4).flatMap { m -> exercises.forReview(m.concept, 3) }
    }
    if (queue.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Работа над ошибками", nav::back)
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Ошибок к повторению нет", style = MaterialTheme.typography.headlineSmall, color = MastColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text("Когда ошибёшься в уроке или тренажёре, тема появится здесь — и вернётся через 1, 3 и 7 дней.", style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Назад", nav::back)
            }
        }
        return
    }
    // Read from a background thread (prefetch), so a plain counter would race.
    val cursor = remember { AtomicInteger(0) }
    QuizSession(
        title = "Работа над ошибками",
        total = queue.size,
        timeLimitSec = null,
        nextQuestion = { queue[cursor.getAndIncrement() % queue.size] },
        onFinish = { results ->
            val byConcept = results.groupBy { it.first.concept }.mapValues { (_, rs) -> rs.all { it.second } }
            val reward = app.finishReview(byConcept, results.count { it.second })
            val fixed = byConcept.count { it.value }
            reward to SessionSummary(
                "Повторение завершено",
                if (fixed == byConcept.size) "Все темы закреплены — вернутся позже для проверки"
                else "Закреплено тем: $fixed из ${byConcept.size}. Остальные вернутся завтра",
            )
        },
        onExit = nav::back,
    )
}
