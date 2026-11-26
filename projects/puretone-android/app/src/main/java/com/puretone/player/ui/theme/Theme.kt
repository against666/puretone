package com.puretone.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFFA78BFA)
private val AccentDeep = Color(0xFF6C4FD0)
private val Mint = Color(0xFF7FD8C4)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1B1032),
    primaryContainer = Color(0xFF322457),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Mint,
    onSecondary = Color(0xFF06312A),
    background = Color(0xFF0E0D12),
    onBackground = Color(0xFFE8E6F0),
    surface = Color(0xFF0E0D12),
    onSurface = Color(0xFFE8E6F0),
    surfaceVariant = Color(0xFF1A1822),
    onSurfaceVariant = Color(0xFFA9A5BA),
    surfaceContainer = Color(0xFF16141D),
    outline = Color(0xFF3A3746),
    outlineVariant = Color(0xFF262331),
)

private val LightColors = lightColorScheme(
    primary = AccentDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7FF),
    onPrimaryContainer = Color(0xFF231445),
    secondary = Color(0xFF1F8E77),
    onSecondary = Color.White,
    background = Color(0xFFFBFAFE),
    onBackground = Color(0xFF16141D),
    surface = Color(0xFFFBFAFE),
    onSurface = Color(0xFF16141D),
    surfaceVariant = Color(0xFFF1EEF8),
    onSurfaceVariant = Color(0xFF57536A),
    surfaceContainer = Color(0xFFF4F1FA),
    outline = Color(0xFFC9C4D9),
    outlineVariant = Color(0xFFE4E0EE),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun PureToneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
