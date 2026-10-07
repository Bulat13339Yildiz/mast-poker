package app.mast.poker.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mast.poker.content.Reference
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.MiniCard
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.visuals.StartingHandsChart
import kotlin.math.roundToInt

private enum class Sheet(val title: String) { HANDS("Комбинации"), TERMS("Термины"), PREFLOP("Префлоп"), MATH("Математика") }

@Composable
fun CheatsheetTab() {
    var sheet by rememberSaveable { mutableStateOf(Sheet.HANDS) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text("Шпаргалка", style = MaterialTheme.typography.displaySmall, color = MastColors.TextPrimary)
            Text("Всё самое нужное — всегда под рукой и без интернета", style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
            Spacer(Modifier.height(14.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sheet.entries.forEach { s ->
                val on = s == sheet
                Text(
                    s.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MastColors.FeltDeep else MastColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) MastColors.Gold else MastColors.Glass)
                        .border(1.dp, if (on) MastColors.Gold else MastColors.GlassStroke, RoundedCornerShape(50))
                        .clickable(role = Role.Tab) { sheet = s }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(sheet, transitionSpec = { fadeIn(tween(Motion.Medium)).togetherWith(fadeOut(tween(Motion.Short))) }, label = "sheet") { s ->
            when (s) {
                Sheet.HANDS -> HandsSheet()
                Sheet.TERMS -> TermsSheet()
                Sheet.PREFLOP -> PreflopSheet()
                Sheet.MATH -> MathSheet()
            }
        }
    }
}

private val sheetPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomBarSpace + 16.dp)

@Composable
private fun HandsSheet() {
    LazyColumn(contentPadding = sheetPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("От сильной к слабой. Любая рука выше по списку бьёт любую ниже.", style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary) }
        items(Reference.ladder.size) { i ->
            val ex = Reference.ladder[i]
            GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${10 - i}", style = MaterialTheme.typography.headlineSmall, color = MastColors.Gold, modifier = Modifier.width(34.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ex.category.ruName, style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                        Text(ex.description, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                    }
                    Text(ex.odds, style = MaterialTheme.typography.labelSmall, color = MastColors.TextMuted, textAlign = TextAlign.End)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ex.cards.forEach { MiniCard(it, 34.dp, dimmed = it in ex.kickers) }
                }
            }
        }
        item {
            Text(
                "Частота — как часто рука собирается из 7 карт, если досматривать раздачу до ривера. Тусклые карты — кикеры: они решают, только если комбинации равны.",
                style = MaterialTheme.typography.bodySmall, color = MastColors.TextMuted,
            )
        }
    }
}

@Composable
private fun TermsSheet() {
    LazyColumn(contentPadding = sheetPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Reference.glossary, key = { it.word }) { t ->
            GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Text(t.word, style = MaterialTheme.typography.titleMedium, color = MastColors.GoldLight)
                Text(t.meaning, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun PreflopSheet() {
    LazyColumn(contentPadding = sheetPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                Text("С какими руками повышать первым на столе из шести", style = MaterialTheme.typography.titleSmall, color = MastColors.TextPrimary)
                Spacer(Modifier.height(10.dp))
                StartingHandsChart()
            }
        }
        items(SeatGroup.entries) { seat ->
            GlassPanel(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Text(seat.ruName, style = MaterialTheme.typography.titleMedium, color = MastColors.GoldLight)
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (seat == SeatGroup.EARLY) "" else "Всё, что раньше, плюс: ") + PreflopChart.describe(PreflopChart.addedAt(seat)),
                    style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary,
                )
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth(), gilded = true) {
                GoldLabel("Правила входа")
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Первым в банк — только рейзом, без лимпа.",
                    "Размер рейза — 2,5–3 больших блайнда.",
                    "Против чужого рейза: QQ+, AK — 3-бет; JJ, TT, AQ — колл; остальное — пас.",
                    "s — одной масти, o — разных. AA–77 значит: тузы, короли… до семёрок.",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MastColors.TextPrimary, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        }
    }
}

private data class Draw(val name: String, val outs: Int)

@Composable
private fun MathSheet() {
    val draws = listOf(
        Draw("Флеш-дро + двусторонний", 15),
        Draw("Флеш-дро", 9),
        Draw("Двусторонний стрит-дро", 8),
        Draw("Сет → фулл-хаус или каре", 7),
        Draw("Две старшие карты (до пары)", 6),
        Draw("Гатшот", 4),
    )
    LazyColumn(contentPadding = sheetPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassPanel(Modifier.fillMaxWidth()) {
                GoldLabel("Ауты и шансы")
                Spacer(Modifier.height(10.dp))
                TableRow("Дро", "Аутов", "След. карта", "К риверу", header = true)
                draws.forEach { d ->
                    val next = (PotOdds.exactHitChance(d.outs, 47, 1) * 100).roundToInt()
                    val river = (PotOdds.exactHitChance(d.outs, 47, 2) * 100).roundToInt()
                    TableRow(d.name, "${d.outs}", "$next%", "$river%")
                }
                Spacer(Modifier.height(8.dp))
                Text("Шансы — с флопа, 47 неизвестных карт. Правило 2 и 4: ауты × 2 на одну карту, × 4 на две.", style = MaterialTheme.typography.bodySmall, color = MastColors.TextMuted)
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth()) {
                GoldLabel("Шансы банка")
                Spacer(Modifier.height(10.dp))
                TableRow("Ставка соперника", "", "", "Нужно для колла", header = true)
                listOf("⅓ банка" to 1.0 / 3, "½ банка" to 0.5, "⅔ банка" to 2.0 / 3, "банк" to 1.0, "1,5 банка" to 1.5, "2 банка" to 2.0).forEach { (label, f) ->
                    val pot = 300
                    val bet = (pot * f).roundToInt()
                    val need = (PotOdds.requiredEquity(pot + bet, bet) * 100).roundToInt()
                    TableRow(label, "", "", "$need%")
                }
                Spacer(Modifier.height(8.dp))
                Text("Нужный шанс = колл ÷ (банк со ставкой + колл).", style = MaterialTheme.typography.bodySmall, color = MastColors.TextMuted)
            }
        }
    }
}

@Composable
private fun TableRow(a: String, b: String, c: String, d: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    val color = if (header) MastColors.TextMuted else MastColors.TextPrimary
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(a, style = style, color = color, modifier = Modifier.weight(2.2f))
        Text(b, style = style, color = if (header) color else MastColors.GoldLight, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
        Text(c, style = style, color = color, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
        Text(d, style = style, color = if (header) color else MastColors.GoldLight, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
    }
}
