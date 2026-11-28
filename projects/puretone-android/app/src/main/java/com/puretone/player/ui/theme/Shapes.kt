package com.puretone.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MD3 圆角令牌：4 / 8 / 12 / 16 / 28。
 *
 * 业务代码里不要再写 RoundedCornerShape(16.dp) 这类魔法值，
 * 统一走 `MaterialTheme.shapes.*`，换主题时形状才会一起变。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
