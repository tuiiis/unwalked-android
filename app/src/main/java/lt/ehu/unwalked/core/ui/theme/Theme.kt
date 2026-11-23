package lt.ehu.unwalked.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.AccentDeep,
    onPrimaryContainer = Palette.Foreground,
    secondary = Palette.Accent,
    onSecondary = Palette.OnAccent,
    secondaryContainer = Palette.AccentDeep,
    onSecondaryContainer = Palette.Foreground,
    tertiary = Palette.Warning,
    onTertiary = Palette.OnAccent,
    background = Palette.Background,
    onBackground = Palette.Foreground,
    surface = Palette.Background,
    onSurface = Palette.Foreground,
    surfaceVariant = Palette.Panel,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.PanelHigh,
    surfaceContainerHighest = Palette.PanelHigh,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Danger,
    onError = Palette.OnDanger,
    inverseSurface = Palette.Foreground,
    inverseOnSurface = Palette.Background,
)

private val roboto = FontFamily.Default

private val typography = Typography(
    headlineSmall = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = roboto, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = roboto, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = roboto, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = roboto, fontSize = 11.sp, lineHeight = 14.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun UnwalkedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
        Surface(color = Palette.Background, contentColor = Palette.Foreground, content = content)
    }
}
