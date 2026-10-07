package app.mast.poker.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.mast.poker.content.DrillType
import app.mast.poker.progress.DrillMode
import app.mast.poker.progress.UserProgress
import app.mast.poker.ui.components.FeltScreen
import app.mast.poker.ui.screens.AchievementsScreen
import app.mast.poker.ui.screens.CelebrationOverlay
import app.mast.poker.ui.screens.ChapterScreen
import app.mast.poker.ui.screens.DrillScreen
import app.mast.poker.ui.screens.LessonScreen
import app.mast.poker.ui.screens.MainScreen
import app.mast.poker.ui.screens.OnboardingScreen
import app.mast.poker.ui.screens.ReviewScreen
import app.mast.poker.ui.screens.EquityToolScreen
import app.mast.poker.ui.screens.ExamScreen
import app.mast.poker.ui.screens.PuzzleScreen
import app.mast.poker.ui.screens.VarianceToolScreen
import app.mast.poker.ui.theme.LocalHapticsEnabled
import app.mast.poker.ui.theme.LocalReducedMotion
import app.mast.poker.ui.theme.MastTheme
import app.mast.poker.ui.theme.Motion
import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable object MainRoute
@Serializable data class ChapterRoute(val id: String)
@Serializable data class LessonRoute(val id: String)
@Serializable data class DrillRoute(val type: String, val blitz: Boolean)
@Serializable object ReviewRoute
@Serializable object AchievementsRoute
@Serializable data class ExamRoute(val chapterId: String)
@Serializable object PuzzleRoute
@Serializable object EquityToolRoute
@Serializable object VarianceToolRoute

val LocalApp = compositionLocalOf<AppViewModel> { error("AppViewModel not provided") }
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Navigation actions shared by all screens. */
class Nav(private val controller: NavHostController) {
    fun back() = controller.popBackStack()
    fun chapter(id: String) = controller.navigate(ChapterRoute(id))
    fun lesson(id: String) = controller.navigate(LessonRoute(id))
    fun drill(type: DrillType, mode: DrillMode) = controller.navigate(DrillRoute(type.name, mode == DrillMode.BLITZ))
    fun review() = controller.navigate(ReviewRoute)
    fun achievements() = controller.navigate(AchievementsRoute)
    fun exam(chapterId: String) = controller.navigate(ExamRoute(chapterId))
    fun puzzle() = controller.navigate(PuzzleRoute)
    fun equityTool() = controller.navigate(EquityToolRoute)
    fun varianceTool() = controller.navigate(VarianceToolRoute)
    fun finishOnboarding() = controller.navigate(MainRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
}

private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(Motion.cardSpring()) { it / 3 } + fadeIn(tween(Motion.Medium, easing = Motion.EmphasizedDecelerate))
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(Motion.cardSpring()) { -it / 6 } + fadeOut(tween(Motion.Short))
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(Motion.cardSpring()) { -it / 6 } + fadeIn(tween(Motion.Medium))
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    scaleOut(targetScale = 0.92f, animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)) + fadeOut(tween(Motion.Medium))
}

@Composable
fun MastApp(app: AppViewModel) {
    val progress by app.progress.collectAsStateWithLifecycle()
    val p = progress
    CompositionLocalProvider(
        LocalApp provides app,
        LocalReducedMotion provides (p?.settings?.reducedMotion ?: false),
        LocalHapticsEnabled provides (p?.settings?.haptics ?: true),
    ) {
        MastTheme {
            FeltScreen {
                if (p != null) AppNavHost(p)
                CelebrationOverlay(app)
            }
        }
    }
}

@Composable
private fun AppNavHost(progress: UserProgress) {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val start: Any = remember { if (progress.onboarded) MainRoute else OnboardingRoute }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = controller,
                startDestination = start,
                enterTransition = enter,
                exitTransition = exit,
                popEnterTransition = popEnter,
                popExitTransition = popExit,
            ) {
                composable<OnboardingRoute> { Scoped(this) { OnboardingScreen(nav) } }
                composable<MainRoute> { Scoped(this) { MainScreen(nav) } }
                composable<ChapterRoute> { Scoped(this) { ChapterScreen(it.toRoute<ChapterRoute>().id, nav) } }
                composable<LessonRoute> { Scoped(this) { LessonScreen(it.toRoute<LessonRoute>().id, nav) } }
                composable<DrillRoute> {
                    val r = it.toRoute<DrillRoute>()
                    Scoped(this) { DrillScreen(DrillType.valueOf(r.type), if (r.blitz) DrillMode.BLITZ else DrillMode.PRACTICE, nav) }
                }
                composable<ReviewRoute> { Scoped(this) { ReviewScreen(nav) } }
                composable<AchievementsRoute> { Scoped(this) { AchievementsScreen(nav) } }
                composable<ExamRoute> { Scoped(this) { ExamScreen(it.toRoute<ExamRoute>().chapterId, nav) } }
                composable<PuzzleRoute> { Scoped(this) { PuzzleScreen(nav) } }
                composable<EquityToolRoute> { Scoped(this) { EquityToolScreen(nav) } }
                composable<VarianceToolRoute> { Scoped(this) { VarianceToolScreen(nav) } }
            }
        }
    }
}

@Composable
private fun Scoped(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavScope provides scope) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}
