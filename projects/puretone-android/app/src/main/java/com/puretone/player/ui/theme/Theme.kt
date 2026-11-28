package com.puretone.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 应用主题：MD3 三件套（颜色 / 字体 / 形状）+ 统一动效缓动。
 *
 * 主题只负责"令牌"，具体页面从 MaterialTheme.colorScheme / typography / shapes
 * 与 ui.theme.Space 取值——这样深色模式、字号缩放、大屏适配才能一起生效。
 */
@Composable
fun PureToneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
