package app.mast.poker.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object Motion {
    const val Short = 150
    const val Medium = 300
    const val Long = 500
    const val DealStagger = 60

    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> cardSpring(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 300f)
    fun <T> uiSpring(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 400f)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> enter(duration: Int = Medium) = tween<T>(duration, easing = EmphasizedDecelerate)
    fun <T> exit(duration: Int = Short) = tween<T>(duration, easing = EmphasizedAccelerate)
}
