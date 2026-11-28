package com.puretone.player.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.IntOffset

/**
 * MD3 动效令牌。
 *
 * 三套缓动 × 四档时长，和设计规范一一对应：
 * - emphasized：进入/离开主界面、需要「被注意到」的转场
 * - standard：通用（开关、展开）
 * - decelerate / accelerate：仅进入 / 仅离开
 */
object Motion {
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val standardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    const val SHORT_1 = 50
    const val SHORT_2 = 100
    const val SHORT_3 = 150
    const val SHORT_4 = 200
    const val MEDIUM_1 = 250
    const val MEDIUM_2 = 300
    const val MEDIUM_3 = 350
    const val MEDIUM_4 = 400
    const val LONG_1 = 450
    const val LONG_2 = 500
    const val LONG_3 = 550
    const val LONG_4 = 600
}

/**
 * 宽窄档位：和 MD3 的 window size class 对应，但不引额外依赖
 * （material3-window-size-class 是一个独立 artifact，这里用 Configuration 宽度算）。
 */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED;

    companion object {
        fun of(widthDp: Int): WidthClass = when {
            widthDp < 600 -> COMPACT
            widthDp < 840 -> MEDIUM
            else -> EXPANDED
        }
    }
}

/**
 * 触觉/位移辅助：列表项按下时的缩放反馈，保持克制（0.98）。
 * 不是 MD3 硬性要求，但配合状态层能让点击有"实体感"。
 */
internal val PressedScale = 0.98f

/** 供动画使用的零偏移 */
internal val NoOffset = IntOffset.Zero
