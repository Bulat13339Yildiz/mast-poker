package app.mast.poker.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mast.poker.core.poker.cards
import app.mast.poker.ui.LocalApp
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.ChipStacks
import app.mast.poker.ui.components.DealtCardRow
import app.mast.poker.ui.components.GoldLabel
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.rememberHaptics
import app.mast.poker.ui.visuals.cardWidthFor
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun OnboardingScreen(nav: Nav) {
    val app = LocalApp.current
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var goal by remember { mutableIntStateOf(60) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.graphicsLayer { translationX = offset * size.width * 0.35f; alpha = 1f - offset.absoluteValue.coerceAtMost(1f) }) {
                    when (page) {
                        0 -> BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            DealtCardRow(cards("Ts Js Qs Ks As"), cardWidthFor(5, maxWidth, cap = 66.dp), dealKey = "intro", baseDelayMs = 250)
                        }
                        1 -> ChipStacks(170.dp)
                        else -> GoalPicker(goal) { goal = it; haptics.tick() }
                    }
                }
                Spacer(Modifier.height(36.dp))
                when (page) {
                    0 -> PageText(
                        "Покер с нуля",
                        "Научим играть в техасский холдем: от названий карт до стратегии. Короткие уроки, тренажёры с разбором ошибок. Всё офлайн и без рекламы.",
                    )
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Как это работает", style = MaterialTheme.typography.displaySmall, color = MastColors.TextPrimary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        StepRow(MastIcons.Learn, "Учись", "Уроки на 3–5 минут с примерами прямо на картах")
                        StepRow(MastIcons.Practice, "Тренируйся", "Бесконечные тренажёры: каждый раз новые раздачи")
                        StepRow(MastIcons.Replay, "Закрепляй", "Ошибки возвращаются через 1, 3 и 7 дней — пока не запомнишь")
                    }
                    else -> PageText(
                        "Сколько времени в день?",
                        "Цель можно поменять в профиле. Это приложение только для обучения — без игры на деньги. Игра на деньги — 18+.",
                    )
                }
            }
        }
        Dots(pager, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            if (pager.currentPage == 2) "Начать" else "Дальше",
            {
                haptics.tap()
                if (pager.currentPage < 2) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                else {
                    app.completeOnboarding(goal)
                    nav.finishOnboarding()
                }
            },
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PageText(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GoldLabel("Масть")
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, color = MastColors.TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MastColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StepRow(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MastColors.Gold.copy(alpha = 0.15f)).border(1.dp, MastColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MastColors.GoldLight)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
        }
    }
}

@Composable
private fun GoalPicker(goal: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple(30, "Лёгкий темп", "5 минут в день"), Triple(60, "Обычный темп", "10 минут в день"), Triple(120, "Серьёзный темп", "20 минут в день")).forEach { (xp, title, sub) ->
            val on = goal == xp
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) MastColors.Gold.copy(alpha = 0.16f) else MastColors.Glass)
                    .border(1.5.dp, if (on) MastColors.Gold else MastColors.GlassStroke, RoundedCornerShape(18.dp))
                    .clickable(role = Role.RadioButton) { onPick(xp) }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = if (on) MastColors.GoldLight else MastColors.TextPrimary)
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MastColors.TextSecondary)
                }
                Text("$xp XP", style = MaterialTheme.typography.labelLarge, color = MastColors.Gold)
            }
        }
    }
}

@Composable
private fun Dots(pager: PagerState, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pager.pageCount) { i ->
            val on = pager.currentPage == i
            val w by animateDpAsState(if (on) 26.dp else 8.dp, Motion.uiSpring(), label = "dot")
            Box(Modifier.height(8.dp).width(w).clip(RoundedCornerShape(4.dp)).background(if (on) MastColors.Gold else MastColors.GlassStroke))
        }
    }
}
