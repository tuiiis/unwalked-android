package lt.ehu.unwalked.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun darkScheme(p: PaletteColors) = darkColorScheme(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = p.accentDeep,
    onPrimaryContainer = p.foreground,
    secondary = p.accent,
    onSecondary = p.onAccent,
    secondaryContainer = p.accentDeep,
    onSecondaryContainer = p.foreground,
    tertiary = p.warning,
    onTertiary = p.onAccent,
    background = p.background,
    onBackground = p.foreground,
    surface = p.background,
    onSurface = p.foreground,
    surfaceVariant = p.panel,
    onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.background,
    surfaceContainerLow = p.panel,
    surfaceContainer = p.panel,
    surfaceContainerHigh = p.panelHigh,
    surfaceContainerHighest = p.panelHigh,
    outline = p.line,
    outlineVariant = p.line,
    error = p.danger,
    onError = p.onDanger,
    inverseSurface = p.foreground,
    inverseOnSurface = p.background,
)

private fun lightScheme(p: PaletteColors) = lightColorScheme(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = p.accentDeep,
    onPrimaryContainer = p.foreground,
    secondary = p.accent,
    onSecondary = p.onAccent,
    secondaryContainer = p.accentDeep,
    onSecondaryContainer = p.foreground,
    tertiary = p.warning,
    onTertiary = p.onAccent,
    background = p.background,
    onBackground = p.foreground,
    surface = p.background,
    onSurface = p.foreground,
    surfaceVariant = p.panel,
    onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.background,
    surfaceContainerLow = p.panel,
    surfaceContainer = p.panel,
    surfaceContainerHigh = p.panelHigh,
    surfaceContainerHighest = p.panelHigh,
    outline = p.line,
    outlineVariant = p.line,
    error = p.danger,
    onError = p.onDanger,
    inverseSurface = p.foreground,
    inverseOnSurface = p.background,
)

private val roboto = FontFamily.Default

private val typography = Typography(
    headlineSmall = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = roboto, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = roboto, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = roboto, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = roboto, fontSize = 14.sp, lineHeight = 18.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun UnwalkedTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkScheme(palette) else lightScheme(palette)
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
            Surface(color = palette.background, contentColor = palette.foreground, content = content)
        }
    }
}
