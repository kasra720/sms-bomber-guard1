package com.smsbomberguard.app

/**
 * Theme.kt
 * -----------------------------------------------------------
 * Modern Material 3 theme: dark/light adaptive, dynamic color on
 * Android 12+, with a calm indigo/teal palette as a fallback.
 */

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Indigo = Color(0xFF5B5FEF)
private val Teal = Color(0xFF14B8A6)
private val DangerSoft = Color(0xFFF97066)
private val SurfaceDark = Color(0xFF111318)
private val SurfaceLight = Color(0xFFFAFAFC)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Teal,
    error = DangerSoft,
    background = SurfaceLight,
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B8EFF),
    secondary = Color(0xFF5EEAD4),
    error = Color(0xFFFF8A80),
    background = SurfaceDark,
    surface = Color(0xFF1A1D24)
)

val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
)

@Composable
fun BomberGuardTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current

    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !dark -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
