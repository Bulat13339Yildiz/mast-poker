package app.mast.poker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mast.poker.content.DrillType
import app.mast.poker.ui.Nav
import app.mast.poker.ui.components.MastIcons
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.Motion
import app.mast.poker.ui.theme.rememberHaptics

enum class Tab(val title: String, val icon: ImageVector) {
    LEARN("Учёба", MastIcons.Learn),
    PRACTICE("Тренировка", MastIcons.Practice),
    CHEATSHEET("Шпаргалка", MastIcons.Cheatsheet),
    PROFILE("Профиль", MastIcons.Profile),
}

/** Space the bottom bar takes; tab content pads its end by this much. */
val BottomBarSpace = 104.dp

@Composable
fun MainScreen(nav: Nav) {
    var tab by rememberSaveable { mutableStateOf(Tab.LEARN) }
    var drillSheet by remember { mutableStateOf<DrillType?>(null) }
    BackHandler(enabled = drillSheet != null) { drillSheet = null }
    BackHandler(enabled = drillSheet == null && tab != Tab.LEARN) { tab = Tab.LEARN }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(Motion.Medium, delayMillis = 60)) + slideInVertically(Motion.uiSpring()) { it / 24 })
                    .togetherWith(fadeOut(tween(Motion.Short)))
            },
            label = "tabs",
        ) { t ->
            when (t) {
                Tab.LEARN -> LearnTab(nav, onOpenPractice = { tab = Tab.PRACTICE })
                Tab.PRACTICE -> PracticeTab(nav, onPickDrill = { drillSheet = it })
                Tab.CHEATSHEET -> CheatsheetTab(nav)
                Tab.PROFILE -> ProfileTab(nav)
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
        BottomBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
        ModeChooser(
            chosen = drillSheet,
            onDismiss = { drillSheet = null },
            onPick = { type, mode ->
                drillSheet = null
                nav.drill(type, mode)
            },
        )
    }
}

/** Fades scrolled content out under the status bar so the clock stays readable. */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(MastColors.FeltDeep, MastColors.FeltDeep.copy(alpha = 0.85f), MastColors.FeltDeep.copy(alpha = 0f))))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(16.dp),
    )
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(MastColors.FeltDeep.copy(alpha = 0f), MastColors.FeltDeep.copy(alpha = 0.92f), MastColors.FeltDeep)))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MastColors.FeltDark.copy(alpha = 0.92f))
                .border(1.dp, MastColors.GoldStroke.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
        ) {
            val itemWidth = maxWidth / Tab.entries.size
            val indicatorX by animateDpAsState(itemWidth * current.ordinal, Motion.uiSpring(), label = "indicator")
            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MastColors.Gold.copy(alpha = 0.14f))
                    .border(1.dp, MastColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
            )
            Row(Modifier.fillMaxSize()) {
                Tab.entries.forEach { t ->
                    val selected = t == current
                    val scale by animateFloatAsState(if (selected) 1.1f else 1f, Motion.bouncy(), label = "tabIcon")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { this.selected = selected }
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                                if (!selected) haptics.tick()
                                onSelect(t)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            t.icon, null,
                            tint = if (selected) MastColors.GoldLight else MastColors.TextMuted,
                            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(t.title, style = MaterialTheme.typography.labelSmall, color = if (selected) MastColors.GoldLight else MastColors.TextMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}
