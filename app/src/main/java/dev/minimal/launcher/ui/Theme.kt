package dev.minimal.launcher.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.minimal.launcher.data.HomeFont
import dev.minimal.launcher.data.HomeWeight
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.ThemeMode

/** Farben für Text direkt auf dem Hintergrundbild. */
data class HomeColors(val text: Color, val secondary: Color, val shadow: Color, val scrim: Color, val dark: Boolean)

val LocalHomeColors = staticCompositionLocalOf {
    HomeColors(Color.White, Color.White.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.5f), Color.Black, true)
}

/** Schrift für Texte auf dem Startbildschirm. */
data class HomeTypeface(val family: FontFamily, val weight: FontWeight)

val LocalHomeTypeface = staticCompositionLocalOf { HomeTypeface(FontFamily.Default, FontWeight.Normal) }

/** Apps, die der Fokus-Modus gerade bremst (werden ausgegraut). */
val LocalBlockedApps = staticCompositionLocalOf<Set<String>> { emptySet() }

/** Icons gerade in Graustufen (Zeitplan)? */
val LocalGrayscale = staticCompositionLocalOf { false }

/** Ist der Fokus-Modus gerade aktiv? */
val LocalFocusActive = staticCompositionLocalOf { false }

val ACCENT_COLORS = listOf(
    0 to "Systemfarbe (Material You)",
    0xFF8AB4F8.toInt() to "Blau",
    0xFF81C995.toInt() to "Grün",
    0xFFF28B82.toInt() to "Rot",
    0xFFFDD663.toInt() to "Gelb",
    0xFFC58AF9.toInt() to "Lila",
    0xFFFCAD70.toInt() to "Orange",
    0xFF78D9EC.toInt() to "Türkis",
    0xFFFF8BCB.toInt() to "Pink",
    0xFFFFFFFF.toInt() to "Weiß",
)

@Composable
fun isDark(settings: LauncherSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LauncherTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val context = LocalContext.current
    val dynamic = settings.accent == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (settings.accent != 0) base.copy(primary = Color(settings.accent)) else base

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val homeColors = if (dark) {
        HomeColors(Color.White, Color.White.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.55f), Color.Black, true)
    } else {
        HomeColors(Color(0xFF111111), Color(0xFF111111).copy(alpha = 0.7f), Color.White.copy(alpha = 0.6f), Color.White, false)
    }

    MaterialTheme(colorScheme = scheme) {
        val typeface = HomeTypeface(
            family = when (settings.font) {
                HomeFont.SYSTEM -> FontFamily.Default
                HomeFont.SERIF -> FontFamily.Serif
                HomeFont.MONO -> FontFamily.Monospace
                HomeFont.CURSIVE -> FontFamily.Cursive
            },
            weight = when (settings.fontWeight) {
                HomeWeight.LIGHT -> FontWeight.Light
                HomeWeight.NORMAL -> FontWeight.Normal
                HomeWeight.MEDIUM -> FontWeight.Medium
            },
        )
        androidx.compose.runtime.CompositionLocalProvider(
            LocalHomeColors provides homeColors,
            LocalHomeTypeface provides typeface,
        ) {
            content()
        }
    }
}
