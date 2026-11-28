package com.puretone.player.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3 8dp 间距刻度：spaceN = N/100 × 8dp。
 *
 * 约定：padding 是元素内边距，spacing 是兄弟元素间距，margin 是版面外缘。
 * 界面里不再出现 13.dp / 17.dp 这种随手写的值。
 */
object Space {
    val xxs: Dp = 2.dp      // s25
    val xs: Dp = 4.dp       // s50
    val s75: Dp = 6.dp
    val sm: Dp = 8.dp       // s100
    val s125: Dp = 10.dp
    val s150: Dp = 12.dp
    val s175: Dp = 14.dp
    val md: Dp = 16.dp      // s200
    val s250: Dp = 20.dp
    val lg: Dp = 24.dp      // s300
    val xl: Dp = 32.dp      // s400
    val s450: Dp = 36.dp
    val s500: Dp = 40.dp
    val s600: Dp = 48.dp
    val s700: Dp = 56.dp
    val s800: Dp = 64.dp
    val s900: Dp = 72.dp

    /** 触控目标最小值（MD3 规范 48dp） */
    val minTouch: Dp = 48.dp

    /** 大屏内容最大宽度：超过这个宽度正文会难以阅读 */
    val maxContentWidth: Dp = 840.dp

    /** 列表行最小高度（两行文本） */
    val listRowMin: Dp = 64.dp
}
