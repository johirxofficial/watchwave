package com.watchwave.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Midnight-cinema palette shared with the browser client.
 * The accent is an electric blue with a cool teal secondary, tuned for long
 * viewing sessions in a dark room.
 */
val WaveCyan = Color(0xFF5B9DFF)
val WaveTeal = Color(0xFF5EEAD4)
val WaveViolet = Color(0xFF8B7CFF)
val WaveAmber = Color(0xFFFFB74D)
val WaveGreen = Color(0xFF4ADE80)
val WaveRed = Color(0xFFFF6B6B)
val Ink = Color(0xFF08090C)
val Surface = Color(0xFF0C0E12)
val SurfaceRaised = Color(0xFF11141A)
val SurfaceHigh = Color(0xFF161A21)
val OutlineSoft = Color(0xFF232A36)
val Muted = Color(0xFF8B93A3)
val Soft = Color(0xFFC4CBD8)

private val DarkScheme = darkColorScheme(
    primary = WaveCyan,
    onPrimary = Color(0xFF04162E),
    primaryContainer = Color(0xFF1B3B66),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = WaveTeal,
    onSecondary = Color(0xFF042B26),
    secondaryContainer = Color(0xFF10332F),
    onSecondaryContainer = Color(0xFFC9FFF6),
    tertiary = WaveAmber,
    onTertiary = Color(0xFF2A1C00),
    background = Ink,
    onBackground = Color(0xFFF3F5F9),
    surface = Surface,
    onSurface = Color(0xFFF3F5F9),
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Soft,
    outline = OutlineSoft,
    outlineVariant = Color(0xFF1A1F28),
    error = WaveRed,
    onError = Color(0xFF2B0808),
)

private val WatchWaveTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val WatchWaveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun WatchWaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = WatchWaveTypography,
        shapes = WatchWaveShapes,
        content = content,
    )
}
