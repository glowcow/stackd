package dev.glowcow.stackd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.glowcow.stackd.R
import dev.glowcow.stackd.data.ThemeMode

@Immutable
data class StackdColors(
    val bg: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val chip: Color,
    /** Page background and blocks of the grouped screens: settings and sheets. */
    val groupBg: Color,
    val group: Color,
    val accent: Color = Color(0xFFD97757),
    val onAccent: Color = Color.White,
    val isDark: Boolean,
)

val LightColors = StackdColors(
    bg = Color(0xFFFAF9F5),
    text = Color(0xFF141413),
    muted = Color(0xFF66655F),
    line = Color(0xFFE6E3DA),
    chip = Color(0xFFEDEBE3),
    groupBg = Color(0xFFF0EEE6),
    group = Color(0xFFFFFFFF),
    isDark = false,
)

val DarkColors = StackdColors(
    bg = Color(0xFF1A1A18),
    text = Color(0xFFF5F4ED),
    muted = Color(0xFFA3A198),
    line = Color(0xFF33332F),
    chip = Color(0xFF2B2B28),
    groupBg = Color(0xFF141413),
    group = Color(0xFF242422),
    isDark = true,
)

/** Scanner is dark regardless of the app theme. */
val ScannerColors = DarkColors.copy(bg = Color(0xFF141413), line = Color(0xFF45443F))

val LocalStackdColors = staticCompositionLocalOf { LightColors }

object StackdTheme {
    val colors: StackdColors
        @Composable get() = LocalStackdColors.current
}

val InstrumentSans = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.instrument_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private val base = TextStyle(fontFamily = InstrumentSans, fontSize = 15.sp)

private val typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = InstrumentSans),
        displayMedium = t.displayMedium.copy(fontFamily = InstrumentSans),
        displaySmall = t.displaySmall.copy(fontFamily = InstrumentSans),
        headlineLarge = t.headlineLarge.copy(fontFamily = InstrumentSans),
        headlineMedium = t.headlineMedium.copy(fontFamily = InstrumentSans),
        headlineSmall = t.headlineSmall.copy(fontFamily = InstrumentSans),
        titleLarge = t.titleLarge.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontFamily = InstrumentSans),
        bodyLarge = base,
        bodyMedium = base.copy(fontSize = 14.sp),
        bodySmall = base.copy(fontSize = 12.sp),
        labelLarge = t.labelLarge.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontFamily = InstrumentSans),
        labelSmall = t.labelSmall.copy(fontFamily = InstrumentSans),
    )
}

@Composable
fun StackdTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    StackdColorsProvider(if (dark) DarkColors else LightColors, content)
}

@Composable
fun StackdColorsProvider(c: StackdColors, content: @Composable () -> Unit) {
    val scheme = (if (c.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        background = c.bg,
        onBackground = c.text,
        surface = c.bg,
        onSurface = c.text,
        surfaceVariant = c.chip,
        onSurfaceVariant = c.muted,
        surfaceContainer = c.bg,
        surfaceContainerHigh = c.chip,
        surfaceContainerHighest = c.chip,
        outline = c.line,
        outlineVariant = c.line,
    )
    CompositionLocalProvider(LocalStackdColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
