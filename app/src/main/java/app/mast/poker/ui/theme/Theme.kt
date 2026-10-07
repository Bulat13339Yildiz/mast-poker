package app.mast.poker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MastColorScheme = darkColorScheme(
    primary = MastColors.Gold,
    onPrimary = MastColors.FeltDeep,
    primaryContainer = MastColors.GoldDark,
    onPrimaryContainer = MastColors.GoldLight,
    secondary = MastColors.Ivory,
    onSecondary = MastColors.SuitBlack,
    background = MastColors.FeltDeep,
    onBackground = MastColors.TextPrimary,
    surface = MastColors.FeltDark,
    onSurface = MastColors.TextPrimary,
    surfaceVariant = MastColors.Felt,
    onSurfaceVariant = MastColors.TextSecondary,
    outline = MastColors.GlassStroke,
    error = MastColors.Wrong,
    onError = MastColors.FeltDeep,
)

@Composable
fun MastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MastColorScheme,
        typography = MastTypography,
        content = content,
    )
}
