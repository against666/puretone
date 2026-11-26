package com.puretone.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 圆点跟着线走的进度条。
 *
 * 不用 Material3 的 Slider：它默认在滑块两侧留空隙，看起来断开。
 * 这里自己画——先画整条底轨，再画到圆点中心的已播段，最后把圆点盖上去，
 * 连接处被圆点覆盖，所以不会出现缺口。
 */
@Composable
fun DotSeekBar(
    fraction: Float,
    enabled: Boolean,
    onFractionChange: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeekCommit(offset.x.toFraction(size.width.toFloat(), DOT_INSET_DP.dp.toPx()))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var current = fraction
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        current = offset.x.toFraction(size.width.toFloat(), DOT_INSET_DP.dp.toPx())
                        onFractionChange(current)
                    },
                    onDragEnd = { onSeekCommit(current) },
                    onDragCancel = { },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        current = change.position.x.toFraction(size.width.toFloat(), DOT_INSET_DP.dp.toPx())
                        onFractionChange(current)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = DOT_INSET_DP.dp.toPx()
            val trackHeight = 4.dp.toPx()
            val dotRadius = 7.dp.toPx()

            val cy = size.height / 2f
            val startX = inset
            val endX = size.width - inset
            val safeFraction = fraction.coerceIn(0f, 1f)
            val dotX = startX + (endX - startX) * safeFraction

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(startX, cy - trackHeight / 2f),
                size = Size(endX - startX, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )

            if (safeFraction > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(startX, cy - trackHeight / 2f),
                    size = Size((dotX - startX).coerceAtLeast(0f), trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f),
                )
            }

            drawCircle(
                color = activeColor,
                radius = dotRadius,
                center = Offset(dotX, cy),
            )
        }
    }
}

private fun Float.toFraction(width: Float, inset: Float): Float {
    val usable = (width - inset * 2f).coerceAtLeast(1f)
    return ((this - inset) / usable).coerceIn(0f, 1f)
}

private const val DOT_INSET_DP = 8
