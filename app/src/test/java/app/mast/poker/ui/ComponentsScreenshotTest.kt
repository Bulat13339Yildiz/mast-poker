package app.mast.poker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import app.mast.poker.ui.components.CasinoChip
import app.mast.poker.ui.components.ChapterArt
import app.mast.poker.ui.components.DecoBand
import app.mast.poker.ui.components.GoldLabel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mast.poker.achievements.Tier
import app.mast.poker.core.poker.Suit
import app.mast.poker.core.poker.cards
import app.mast.poker.ui.components.ButtonTone
import app.mast.poker.ui.components.CardState
import app.mast.poker.ui.components.FeltScreen
import app.mast.poker.ui.components.GlassPanel
import app.mast.poker.ui.components.Medal
import app.mast.poker.ui.components.OptionState
import app.mast.poker.ui.components.OptionTile
import app.mast.poker.ui.components.PlayingCard
import app.mast.poker.ui.components.PrimaryButton
import app.mast.poker.ui.components.ProgressRing
import app.mast.poker.ui.components.SecondaryButton
import app.mast.poker.ui.components.XpBar
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastColors
import app.mast.poker.ui.theme.MastTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ComponentsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun gallery() {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                MastTheme {
                    FeltScreen {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Масть", style = MaterialTheme.typography.displayMedium, color = MastColors.Gold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                cards("As Kh Qd Jc Th").forEach { PlayingCard(it, 64.dp) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val row = cards("9s 7h 5d 2c")
                                PlayingCard(row[0], 64.dp, state = CardState.Selected)
                                PlayingCard(row[1], 64.dp, state = CardState.Wrong)
                                PlayingCard(row[2], 64.dp, state = CardState.Dimmed)
                                PlayingCard(row[3], 64.dp)
                                PlayingCard(null, 64.dp, flip = 0f)
                            }
                            GlassPanel(Modifier.fillMaxWidth()) {
                                Text("Уровень 3 · Любитель", style = MaterialTheme.typography.titleMedium, color = MastColors.TextPrimary)
                                Spacer(Modifier.height(10.dp))
                                XpBar(0.62f)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Medal(Tier.BRONZE, Suit.CLUBS, 64.dp)
                                Medal(Tier.SILVER, Suit.HEARTS, 64.dp)
                                Medal(Tier.GOLD, Suit.SPADES, 64.dp)
                                Medal(Tier.GOLD, Suit.DIAMONDS, 64.dp, locked = true)
                                ProgressRing(0.7f, 64.dp) { Text("42", color = MastColors.GoldLight) }
                            }
                            OptionTile("Флеш", OptionState.Correct, {})
                            OptionTile("Стрит", OptionState.Wrong, {})
                            OptionTile("Фулл-хаус", OptionState.Idle, {})
                            PrimaryButton("Продолжить", {}, Modifier.fillMaxWidth(), shimmer = false)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SecondaryButton("Шпаргалка", {})
                                PrimaryButton("Верно", {}, tone = ButtonTone.Correct, shimmer = false)
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/components.png")
    }

    @Test
    fun deck() {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                MastTheme {
                    FeltScreen {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("2h 3s 4d 5c 6h", "7s 8d 9c Th Ah", "Js Qh Kd Ac As").forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    cards(row).forEach { PlayingCard(it, 70.dp) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlayingCard(null, 120.dp, flip = 0f)
                                PlayingCard(cards("Kh")[0], 120.dp)
                                PlayingCard(cards("Ts")[0], 120.dp)
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/deck.png")
    }

    @Test
    fun featureTiles() {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                MastTheme {
                    FeltScreen {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            GoldLabel("Глава 2")
                            listOf(
                                Triple("basics", "Знакомство", "Колода, цель игры и правила холдема"),
                                Triple("hands", "Комбинации", "Десять рук — от старшей карты до роял-флеша"),
                                Triple("strategy", "Стратегия", "Агрессия, блеф, борд и голова"),
                            ).forEach { (id, title, sub) ->
                                GlassPanel(Modifier.fillMaxWidth(), gilded = true) {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        ChapterArt(id, 96.dp)
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            GoldLabel("Глава", lines = false)
                                            Spacer(Modifier.height(6.dp))
                                            Text(title, style = MaterialTheme.typography.headlineMedium, color = MastColors.TextPrimary)
                                            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MastColors.TextSecondary)
                                        }
                                    }
                                }
                            }
                            DecoBand()
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                ChapterArt("flow", 90.dp)
                                ChapterArt("preflop", 90.dp)
                                ChapterArt("math", 90.dp)
                                CasinoChip(90.dp)
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/feature_tiles.png")
    }
}
