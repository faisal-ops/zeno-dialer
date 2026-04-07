package com.zeno.dialer.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 35.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 41.sp,
        letterSpacing = 3.5.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 28.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 25.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.2.sp
    )
)

internal data class DialerColorTokens(
    val bgPage: Color,
    val bgSurface: Color,
    val bgElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val border: Color,
    val focusBorder: Color,
    val surfaceActive: Color,
    val accent: Color,
    val accentMuted: Color,
)

internal val LocalDialerColors = staticCompositionLocalOf<DialerColorTokens> {
    DialerColorTokens(
        bgPage = Color.Unspecified,
        bgSurface = Color.Unspecified,
        bgElevated = Color.Unspecified,
        textPrimary = Color.Unspecified,
        textSecondary = Color.Unspecified,
        textHint = Color.Unspecified,
        border = Color.Unspecified,
        focusBorder = Color.Unspecified,
        surfaceActive = Color.Unspecified,
        accent = Color.Unspecified,
        accentMuted = Color.Unspecified,
    )
}

// ── Custom muted color schemes ──────────────────────────────────────────────

private val MutedDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0F1115),
    primaryContainer = Color(0xFF283244),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFF9AA3B2),
    onSecondary = Color(0xFF0F1115),
    secondaryContainer = Color(0xFF1D2330),
    onSecondaryContainer = Color(0xFFE5EAF4),
    tertiary = Color(0xFF8AB4F8),
    onTertiary = Color(0xFF0F1115),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF1F2430),
    onSurfaceVariant = Color(0xFF9AA3B2),
    surfaceContainerLowest = Color(0xFF111318),
    surfaceContainerLow = Color(0xFF171A21),
    surfaceContainer = Color(0xFF1F2430),
    surfaceContainerHigh = Color(0xFF252B38),
    surfaceContainerHighest = Color(0xFF2C3442),
    outline = Color(0xFF3D4556),
    outlineVariant = Color(0xFF2C3442),
    error = Color(0xFFCC4444),
    onError = Color(0xFFFFFFFF),
)

private val MutedLightColorScheme = lightColorScheme(
    primary = Color(0xFF4285F4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF6F7480),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFF2F8),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF4285F4),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEFF2F8),
    onSurfaceVariant = Color(0xFF6F7480),
    surfaceContainerLowest = Color(0xFFF8F9FC),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF2F8),
    surfaceContainerHigh = Color(0xFFEFF2F8),
    surfaceContainerHighest = Color(0xFFE8ECF4),
    outline = Color(0xFFD9DEE8),
    outlineVariant = Color(0xFFE2E6EE),
    error = Color(0xFFCC4444),
    onError = Color(0xFFFFFFFF),
)

private val DarkTokens = DialerColorTokens(
    bgPage = Color(0xFF111318),
    bgSurface = Color(0xFF171A21),
    bgElevated = Color(0xFF1F2430),
    textPrimary = Color(0xFFE8EAED),
    textSecondary = Color(0xFF9AA3B2),
    textHint = Color(0xFF6F788A),
    border = Color(0xFF2C3442),
    focusBorder = Color(0xFF8AB4F8),
    surfaceActive = Color(0xFF283244),
    accent = Color(0xFF8AB4F8),
    accentMuted = Color(0xFF78A6F0),
)

private val LightTokens = DialerColorTokens(
    bgPage = Color(0xFFF8F9FC),
    bgSurface = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1F1F1F),
    textSecondary = Color(0xFF6F7480),
    textHint = Color(0xFF9AA3B2),
    border = Color(0xFFE2E6EE),
    focusBorder = Color(0xFF4285F4),
    surfaceActive = Color(0xFFE8F0FE),
    accent = Color(0xFF4285F4),
    accentMuted = Color(0xFF5A95F5),
)

@Composable
fun DialerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("zeno_settings", Context.MODE_PRIVATE)
    var themeChoice by remember { mutableIntStateOf(prefs.getInt("choose_theme", 0)) } // 0=system default, 1=light, 2=dark
    val systemDark = isSystemInDarkTheme()

    // Keep theme in sync with Display Options without forcing an app restart.
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "choose_theme") {
                themeChoice = prefs.getInt("choose_theme", 0)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val isDark = when (themeChoice) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    val colorScheme = if (isDark) MutedDarkColorScheme else MutedLightColorScheme
    val tokens = if (isDark) DarkTokens else LightTokens

    CompositionLocalProvider(LocalDialerColors provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
