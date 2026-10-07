package app.mast.poker.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import app.mast.poker.ui.theme.MastColors

private const val FELT_SHADER = """
uniform float2 resolution;
uniform float2 light;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

half4 main(float2 coord) {
    float2 uv = coord / resolution.x;
    float2 l = light / resolution.x;
    float d = distance(uv, l);
    half3 hi = half3(0.090, 0.455, 0.255);
    half3 mid = half3(0.047, 0.333, 0.188);
    half3 low = half3(0.016, 0.145, 0.078);
    half3 col = mix(hi, mid, smoothstep(0.0, 0.55, d));
    col = mix(col, low, smoothstep(0.45, 1.45, d));
    float grain = hash(floor(coord)) - 0.5;
    float fibre = hash(floor(coord * float2(0.5, 2.0))) - 0.5;
    col += half3(grain * 0.030 + fibre * 0.018);
    return half4(col, 1.0);
}
"""

private val isRobolectric: Boolean = Build.FINGERPRINT == "robolectric"

/** Deep green felt with a soft top light. Uses an AGSL grain shader on Android 13+. */
fun Modifier.feltBackground(lightY: Float = 0.22f): Modifier =
    if (Build.VERSION.SDK_INT >= 33 && !isRobolectric) feltShader(lightY) else feltGradient(lightY)

@RequiresApi(33)
private fun Modifier.feltShader(lightY: Float): Modifier = drawWithCache {
    val shader = RuntimeShader(FELT_SHADER)
    shader.setFloatUniform("resolution", size.width, size.height)
    shader.setFloatUniform("light", size.width * 0.5f, size.height * lightY)
    val brush = ShaderBrush(shader)
    onDrawBehind { drawRect(brush) }
}

private fun Modifier.feltGradient(lightY: Float): Modifier = drawWithCache {
    val base = Brush.radialGradient(
        colorStops = arrayOf(
            0f to MastColors.FeltLight,
            0.42f to MastColors.Felt,
            0.8f to MastColors.FeltDark,
            1f to MastColors.FeltDeep,
        ),
        center = Offset(size.width * 0.5f, size.height * lightY),
        radius = size.maxDimension * 0.95f,
    )
    onDrawBehind { drawRect(base) }
}

@Composable
fun FeltScreen(modifier: Modifier = Modifier, lightY: Float = 0.22f, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(MastColors.FeltDeep)) {
        // The felt lives in its own offscreen layer: it is rasterised once and then only
        // composited, so animations on top never re-run the grain shader.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .then(remember(lightY) { Modifier.feltBackground(lightY) }),
        )
        content()
    }
}

/** Thin gold hairline used to separate sections. */
val GoldHairline = Brush.horizontalGradient(
    listOf(Color.Transparent, MastColors.Gold.copy(alpha = 0.6f), Color.Transparent),
)
