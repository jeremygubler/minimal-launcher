package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
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
import dev.minimal.launcher.R
import dev.minimal.launcher.data.HomeFont
import dev.minimal.launcher.data.KansoStyle
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
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

val ACCENT_COLORS: List<Pair<Int, String>> get() = listOf(
    0 to tr("Systemfarbe (Material You)", "System color (Material You)"),
    0xFF8AB4F8.toInt() to tr("Blau", "Blue"),
    0xFF81C995.toInt() to tr("Grün", "Green"),
    0xFFF28B82.toInt() to tr("Rot", "Red"),
    0xFFFDD663.toInt() to tr("Gelb", "Yellow"),
    0xFFC58AF9.toInt() to tr("Lila", "Purple"),
    0xFFFCAD70.toInt() to "Orange",
    0xFF78D9EC.toInt() to tr("Türkis", "Turquoise"),
    0xFFFF8BCB.toInt() to "Pink",
    0xFFFFFFFF.toInt() to tr("Weiß", "White"),
)

/** Aktiver Kanso-Stil (nur mit Pro wirksam). */
@Composable
fun activeStyle(settings: LauncherSettings): KansoStyle =
    if (settings.kansoStyle.active && isPro()) settings.kansoStyle else KansoStyle.NONE

@Composable
fun isDark(settings: LauncherSettings): Boolean {
    val style = activeStyle(settings)
    if (style.active) return style.dark
    return when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/** Mitgelieferte Schriften (nur Light-Schnitt; andere Stärken werden angenähert). */
private val CormorantFamily = FontFamily(Font(R.font.cormorant_light, FontWeight.Light))
private val InterFamily = FontFamily(Font(R.font.inter_light, FontWeight.Light))

/** Material-Farben passend zum Stil – Dialoge und Einstellungen wirken wie aus einem Guss. */
private fun styleScheme(style: KansoStyle): ColorScheme {
    val bg = Color(style.background)
    val text = Color(style.text)
    val accent = Color(style.accent)
    val raised = lerp(bg, text, 0.06f)
    val higher = lerp(bg, text, 0.10f)
    return if (style.dark) {
        darkColorScheme(
            primary = accent, onPrimary = bg, secondary = accent, onSecondary = bg,
            background = bg, onBackground = text, surface = bg, onSurface = text,
            surfaceVariant = higher, onSurfaceVariant = text.copy(alpha = 0.7f),
            surfaceContainerLowest = bg, surfaceContainerLow = raised, surfaceContainer = raised,
            surfaceContainerHigh = higher, surfaceContainerHighest = lerp(bg, text, 0.14f),
            outline = text.copy(alpha = 0.35f), outlineVariant = text.copy(alpha = 0.15f),
            secondaryContainer = lerp(bg, accent, 0.25f), onSecondaryContainer = text,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = bg, secondary = accent, onSecondary = bg,
            background = bg, onBackground = text, surface = bg, onSurface = text,
            surfaceVariant = higher, onSurfaceVariant = text.copy(alpha = 0.7f),
            surfaceContainerLowest = bg, surfaceContainerLow = raised, surfaceContainer = raised,
            surfaceContainerHigh = higher, surfaceContainerHighest = lerp(bg, text, 0.14f),
            outline = text.copy(alpha = 0.35f), outlineVariant = text.copy(alpha = 0.15f),
            secondaryContainer = lerp(bg, accent, 0.2f), onSecondaryContainer = text,
        )
    }
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
    val style = activeStyle(settings)
    val scheme = when {
        style.active -> styleScheme(style)
        settings.accent != 0 -> base.copy(primary = Color(settings.accent))
        else -> base
    }

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

    val homeColors = if (style.active) {
        // Volltonhintergrund: kein Schatten nötig, gedämpfte Zweitfarbe.
        val text = Color(style.text)
        HomeColors(text, text.copy(alpha = 0.62f), Color.Transparent, Color(style.background), style.dark)
    } else if (dark) {
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
                HomeFont.KANSO_SERIF -> if (isPro()) CormorantFamily else FontFamily.Serif
                HomeFont.KANSO_SANS -> if (isPro()) InterFamily else FontFamily.Default
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
