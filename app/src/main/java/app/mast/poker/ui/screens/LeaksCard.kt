package app.mast.poker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import app.mast.poker.progress.Leaks
import app.mast.poker.progress.UserProgress
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.XpBar
import app.mast.poker.ui.theme.MastColors
import kotlin.math.roundToInt

private const val ENOUGH_SPOTS = 20

/** The player's costliest habits, found in drills and simulator decisions. */
@Composable
fun LeaksCard(p: UserProgress, onTrain: (DrillType) -> Unit) {
    val active = Leaks.active(p).take(3)
    val seen = Leaks.spotsSeen(p)
    GlassPanel(Modifier.fillMaxWidth(), gilded = active.isNotEmpty()) {
        Text("Твои лики", style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
        Text(
            "Ошибки одного типа, которые повторяются. Считаем по решениям в тренажёрах и симуляторе раздач.",
            style = MaterialTheme.typography.bodySmall, color = MastColors.TextMuted,
        )
        Spacer(Modifier.height(10.dp))
        when {
            active.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                active.forEach { (leak, stat) ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(leak.title, style = MaterialTheme.typography.titleSmall, color = MastColors.Wrong, modifier = Modifier.weight(1f))
                            Text("${(stat.rate * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MastColors.Wrong)
                        }
                        Spacer(Modifier.height(4.dp))
                        XpBar(stat.rate.toFloat(), height = 4.dp)
                        Spacer(Modifier.height(4.dp))
                        Text("Ошибка в ${stat.mistakes} из ${stat.spots} таких ситуаций. ${leak.advice}", style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                        val drill = leak.drill
                        if (drill != null && Curriculum.drillUnlocked(drill, p.lessons.keys)) {
                            Text(
                                "Тренировать: ${drill.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MastColors.Gold,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MastColors.Gold.copy(alpha = 0.12f))
                                    .border(1.dp, MastColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .clickable(role = Role.Button) { onTrain(drill) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            seen < ENOUGH_SPOTS -> Text(
                "Пока мало данных: решено ситуаций — $seen из $ENOUGH_SPOTS. Решай «Префлоп», «Шансы банка», «Пуш или фолд», «Размер ставки» и играй раздачи.",
                style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary,
            )
            else -> Text(
                "Повторяющихся ошибок не видно — так держать.",
                style = MaterialTheme.typography.bodyMedium, color = MastColors.Correct,
            )
        }
    }
}
