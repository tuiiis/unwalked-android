package lt.ehu.unwalked.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PaletteColors(
    val background: Color,
    val panel: Color,
    val panelHigh: Color,
    val line: Color,
    val foreground: Color,
    val muted: Color,
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val warning: Color,
    val warningBackground: Color,
    val danger: Color,
    val onDanger: Color,
    val route: Color,
    val heat: List<Color>,
    val isDark: Boolean,
)

val DarkColors = PaletteColors(
    background = Color(0xFF0B0F18),
    panel = Color(0xFF141A26),
    panelHigh = Color(0xFF1B2333),
    line = Color(0xFF2A3446),
    foreground = Color(0xFFE3E9F2),
    muted = Color(0xFFA9B4C6),
    accent = Color(0xFF8EC5FF),
    accentDeep = Color(0xFF1F3A5C),
    onAccent = Color(0xFF0B1A2C),
    warning = Color(0xFFF6C177),
    warningBackground = Color(0xFF3A2C14),
    danger = Color(0xFFF2B8B5),
    onDanger = Color(0xFF3B1513),
    route = Color(0xFFF6C177),
    heat = listOf(Color(0xFF1F4E8C), Color(0xFF3B82D6), Color(0xFF8EC5FF), Color(0xFFEAF4FF)),
    isDark = true,
)

val LightColors = PaletteColors(
    background = Color(0xFFF7F9FC),
    panel = Color(0xFFFFFFFF),
    panelHigh = Color(0xFFEEF2F7),
    line = Color(0xFFC9D2E0),
    foreground = Color(0xFF141B26),
    muted = Color(0xFF4A5568),
    accent = Color(0xFF1F5FAF),
    accentDeep = Color(0xFFDCE9FA),
    onAccent = Color(0xFFFFFFFF),
    warning = Color(0xFF8A5A00),
    warningBackground = Color(0xFFFFF1D6),
    danger = Color(0xFFB3261E),
    onDanger = Color(0xFFFFFFFF),
    route = Color(0xFFB07A10),
    heat = listOf(Color(0xFFCFE1F7), Color(0xFF8EC5FF), Color(0xFF3B82D6), Color(0xFF1F4E8C)),
    isDark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkColors }

object Palette {
    val Background: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.background
    val Panel: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.panel
    val PanelHigh: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.panelHigh
    val Line: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.line
    val Foreground: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.foreground
    val Muted: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.muted
    val Accent: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.accent
    val AccentDeep: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.accentDeep
    val OnAccent: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.onAccent
    val Warning: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.warning
    val WarningBackground: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.warningBackground
    val Danger: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.danger
    val OnDanger: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.onDanger
    val Route: Color
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.route
    val Heat: List<Color>
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current.heat
}
