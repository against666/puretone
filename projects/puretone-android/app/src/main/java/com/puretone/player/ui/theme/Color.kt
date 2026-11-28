package com.puretone.player.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * MD3 色彩令牌。
 *
 * 只定义「角色」，不在业务代码里写十六进制——所有界面都从
 * `MaterialTheme.colorScheme.*` 取值，深色/浅色/高对比才能一起生效。
 * 品牌主色是紫色（种子 #6C4FD0 派生），次要色取青绿作为点缀。
 */

private val Purple = Color(0xFF5B44C0)
private val PurpleLight = Color(0xFFCBBEFF)

val LightScheme = lightColorScheme(
    primary = Purple,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF190055),

    secondary = Color(0xFF00696B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFA7F2E4),
    onSecondaryContainer = Color(0xFF00201B),

    tertiary = Color(0xFF7A4E7A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F5),
    onTertiaryContainer = Color(0xFF2F1132),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceTint = Purple,

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    surfaceDim = Color(0xFFDED8E1),
    surfaceBright = Color(0xFFFFFBFF),

    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = PurpleLight,

    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
)

val DarkScheme = darkColorScheme(
    primary = PurpleLight,
    onPrimary = Color(0xFF2E1A6B),
    primaryContainer = Color(0xFF45308F),
    onPrimaryContainer = Color(0xFFE7DEFF),

    secondary = Color(0xFF8BD5C8),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFA7F2E4),

    tertiary = Color(0xFFE9B5E5),
    onTertiary = Color(0xFF45264A),
    tertiaryContainer = Color(0xFF5E3C5F),
    onTertiaryContainer = Color(0xFFFFD7F5),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF131218),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF131218),
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = PurpleLight,

    surfaceContainerLowest = Color(0xFF0E0D13),
    surfaceContainerLow = Color(0xFF1B1A20),
    surfaceContainer = Color(0xFF1F1E24),
    surfaceContainerHigh = Color(0xFF29282F),
    surfaceContainerHighest = Color(0xFF34333A),
    surfaceDim = Color(0xFF131218),
    surfaceBright = Color(0xFF39383F),

    inverseSurface = Color(0xFFE6E1E9),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Purple,

    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
)
