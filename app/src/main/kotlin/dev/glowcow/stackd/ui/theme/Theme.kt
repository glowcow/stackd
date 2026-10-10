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
import dev.glowcow.stackd.data.Palette
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

/** Neutral greys with a blue accent, light. */
val ClassicLightColors = StackdColors(
    bg = Color(0xFFFAFAFA),
    text = Color(0xFF111111),
    muted = Color(0xFF666666),
    line = Color(0xFFE2E2E2),
    chip = Color(0xFFEBEBEB),
    groupBg = Color(0xFFF1F1F1),
    group = Color(0xFFFFFFFF),
    accent = Color(0xFF1F6FEB),
    isDark = false,
)

val ClassicDarkColors = StackdColors(
    bg = Color(0xFF161616),
    text = Color(0xFFF2F2F2),
    muted = Color(0xFF9E9E9E),
    line = Color(0xFF303030),
    chip = Color(0xFF282828),
    groupBg = Color(0xFF101010),
    group = Color(0xFF202020),
    accent = Color(0xFF4C8DF6),
    isDark = true,
)

val LocalStackdColors = staticCompositionLocalOf { ClassicLightColors }

/** The dark colours of the scheme in use, for a page that is dark in any theme. */
val LocalDarkColors = staticCompositionLocalOf { ClassicDarkColors }

object StackdTheme {
    val colors: StackdColors
        @Composable get() = LocalStackdColors.current
}

val AppFont = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.arimo, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/**
 * Cormorant Garamond, the face of page titles, at its medium weight: a phone draws its hairlines
 * thinner than a page of samples does. It has no Hebrew: a title in Hebrew stays in [AppFont].
 */
val TitleFont = FontFamily(
    Font(R.font.cormorant_garamond, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

private val base = TextStyle(fontFamily = AppFont, fontSize = 15.sp)

private val typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = AppFont),
        displayMedium = t.displayMedium.copy(fontFamily = AppFont),
        displaySmall = t.displaySmall.copy(fontFamily = AppFont),
        headlineLarge = t.headlineLarge.copy(fontFamily = AppFont),
        headlineMedium = t.headlineMedium.copy(fontFamily = AppFont),
        headlineSmall = t.headlineSmall.copy(fontFamily = AppFont),
        titleLarge = t.titleLarge.copy(fontFamily = AppFont, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontFamily = AppFont, fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontFamily = AppFont),
        bodyLarge = base,
        bodyMedium = base.copy(fontSize = 14.sp),
        bodySmall = base.copy(fontSize = 12.sp),
        labelLarge = t.labelLarge.copy(fontFamily = AppFont, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontFamily = AppFont),
        labelSmall = t.labelSmall.copy(fontFamily = AppFont),
    )
}

@Composable
fun StackdTheme(mode: ThemeMode = ThemeMode.SYSTEM, palette: Palette = Palette.CLASSIC, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val (light, night) = when (palette) {
        Palette.WARM -> LightColors to DarkColors
        Palette.CLASSIC -> ClassicLightColors to ClassicDarkColors
    }
    CompositionLocalProvider(LocalDarkColors provides night) {
        StackdColorsProvider(if (dark) night else light, content)
    }
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
