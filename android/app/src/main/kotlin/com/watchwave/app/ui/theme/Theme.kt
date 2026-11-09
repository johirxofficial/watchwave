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

val WaveCyan = Color(0xFF22D3EE)
val WaveViolet = Color(0xFF8B5CF6)
val WaveAmber = Color(0xFFFBBF24)
val WaveGreen = Color(0xFF34D399)
val WaveRed = Color(0xFFF87171)
val Ink = Color(0xFF070A10)
val Surface = Color(0xFF10151F)
val SurfaceRaised = Color(0xFF171E2B)
val OutlineSoft = Color(0xFF2A3446)
val Muted = Color(0xFF8A93A6)

private val DarkScheme = darkColorScheme(
    primary = WaveCyan,
    onPrimary = Color(0xFF00202A),
    primaryContainer = Color(0xFF0E4A57),
    onPrimaryContainer = Color(0xFFB9F4FF),
    secondary = WaveViolet,
    onSecondary = Color(0xFF1A0B33),
    secondaryContainer = Color(0xFF34205C),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = WaveAmber,
    onTertiary = Color(0xFF2A1C00),
    background = Ink,
    onBackground = Color(0xFFF5F7FF),
    surface = Surface,
    onSurface = Color(0xFFF5F7FF),
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Color(0xFFC2CADA),
    outline = OutlineSoft,
    outlineVariant = Color(0xFF1D2635),
    error = WaveRed,
    onError = Color(0xFF2B0808),
)

private val WatchWaveTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
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
