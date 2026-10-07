package app.mast.poker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mast.poker.content.Question
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.cards
import app.mast.poker.practice.Answer
import app.mast.poker.practice.Explainer
import app.mast.poker.ui.components.FeltScreen
import app.mast.poker.ui.quiz.FeedbackSheet
import app.mast.poker.ui.quiz.QuestionState
import app.mast.poker.ui.quiz.QuestionView
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class QuizScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, state: QuestionState) {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                MastTheme {
                    FeltScreen {
                        Column(Modifier.statusBarsPadding().padding(20.dp)) { QuestionView(state, key = name) }
                        state.feedback?.let { f -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { FeedbackSheet(f, {}) } }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/quiz_$name.png")
    }

    @Test
    fun nameHandWrong() {
        val q = Question.NameHand(
            "Какая лучшая комбинация из семи карт?",
            cards("Ah 6h"), cards("Kh 9h 7c 3d 2c"),
            listOf(HandCategory.FLUSH, HandCategory.PAIR, HandCategory.HIGH_CARD, HandCategory.STRAIGHT),
            explanation = Explainer.anatomy(cards("Ah 6h"), cards("Kh 9h 7c 3d 2c")),
        )
        val s = QuestionState(q)
        s.answer = Answer.Pick(0)
        s.check()
        shot("name_hand_wrong", s)
    }

    @Test
    fun winnerChecked() {
        val board = cards("Kc Kd 6h 3s 2c")
        val hands = listOf(cards("6c 5d"), cards("3c Ah"))
        val q = Question.PickWinner("Кто выиграл?", board, hands, Explainer.winner(board, hands, listOf("Игрок 1", "Игрок 2")))
        val s = QuestionState(q)
        s.answer = Answer.Pick(1)
        s.check()
        shot("winner_wrong", s)
    }
}
