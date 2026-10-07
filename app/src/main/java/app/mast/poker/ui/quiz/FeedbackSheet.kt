package app.mast.poker.ui.quiz

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.mast.poker.practice.Feedback
import app.mast.poker.ui.components.ButtonTone
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.RichText
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion

/** Bottom panel after checking an answer: verdict, explanation, why-not and "Дальше". */
@Composable
fun FeedbackSheet(feedback: Feedback, onNext: () -> Unit, modifier: Modifier = Modifier, nextLabel: String = "Дальше") {
    val tone = if (feedback.correct) MastColors.Correct else MastColors.Wrong
    val pop = remember(feedback) { Animatable(0.4f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(feedback) { if (reduced) pop.snapTo(1f) else pop.animateTo(1f, Motion.bouncy()) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(tone.copy(alpha = 0.16f).compositeOverFelt(), MastColors.FeltDeep)))
            .border(1.dp, tone.copy(alpha = 0.5f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .clip(CircleShape)
                    .background(tone),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (feedback.correct) MastIcons.Check else MastIcons.Close, null, tint = MastColors.FeltDeep, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(feedback.headline, style = MaterialTheme.typography.headlineSmall, color = tone)
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RichText(feedback.explanation, style = MaterialTheme.typography.bodyMedium)
            feedback.whyNot?.let { why ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MastColors.Glass)
                        .border(1.dp, MastColors.GlassStroke, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Text("ПОЧЕМУ НЕ ТАК", style = MaterialTheme.typography.labelSmall, color = MastColors.GoldLight)
                    Spacer(Modifier.height(6.dp))
                    RichText(why, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(nextLabel, onNext, Modifier.fillMaxWidth(), tone = if (feedback.correct) ButtonTone.Correct else ButtonTone.Gold, shimmer = false)
    }
}

private fun Color.compositeOverFelt() = compositeOver(MastColors.FeltDark)
