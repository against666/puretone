package com.puretone.player.ui.player

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puretone.player.PureToneApplication
import com.puretone.player.R
import com.puretone.player.data.model.LyricState
import com.puretone.player.lyric.LyricLine
import com.puretone.player.lyric.LrcParser
import com.puretone.player.lyric.indexAt
import com.puretone.player.ui.components.DotSeekBar
import com.puretone.player.ui.components.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as PureToneApplication
    val state by app.player.state.collectAsStateWithLifecycle()

    val track by remember(state.currentTrackId) {
        if (state.currentTrackId <= 0L) {
            flowOf(null)
        } else {
            app.repository.observeTrack(state.currentTrackId)
        }
    }.collectAsStateWithLifecycle(initialValue = null)

    val lyricLines = remember(track?.id, track?.lyricText, track?.offsetMs) {
        val raw = track?.lyricText
        val offset = track?.offsetMs ?: 0
        if (raw.isNullOrBlank()) emptyList() else LrcParser.parse(raw, offset)
    }

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs
    val baseFraction = if (duration > 0L) {
        (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownFraction = dragFraction ?: baseFraction
    val shownPositionMs = if (duration > 0L) (shownFraction * duration).toLong() else 0L

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("← 返回")
        }

        Text(
            text = state.title.ifBlank { "还没有播放中的歌曲" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.artist.ifBlank { "从音乐库选一首开始" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        if (lyricLines.isNotEmpty()) {
            LyricsPanel(
                lines = lyricLines,
                positionMs = state.positionMs,
                onSeek = app.player::seekToAndPlay,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            NoLyricsPanel(
                lyricState = track?.lyricState,
                title = state.title,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }

        DotSeekBar(
            fraction = shownFraction,
            enabled = state.hasMedia && duration > 0L,
            onFractionChange = { dragFraction = it },
            onSeekCommit = { fraction ->
                if (duration > 0L) {
                    app.player.seekTo((fraction * duration).toLong())
                }
                dragFraction = null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(shownPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (duration > 0L) formatDuration(duration) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = app.player::previous, enabled = state.hasMedia) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = "上一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.width(20.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.hasMedia) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable(enabled = state.hasMedia) { app.player.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                if (state.isPlaying) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = "暂停",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        tint = if (state.hasMedia) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(Modifier.width(20.dp))

            IconButton(onClick = app.player::next, enabled = state.hasMedia) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = "下一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LyricsPanel(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = remember(lines, positionMs) { lines.indexAt(positionMs) }

    // 手动滑动后的一段时间内不自动跟随，让用户能停下来看、找准要跳的那一句。
    // 用时间戳而不是布尔值：到点自动恢复，不需要用户再点一下。
    var followPausedUntil by remember { mutableStateOf(0L) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                followPausedUntil = SystemClock.elapsedRealtime() + FOLLOW_RESUME_DELAY_MS
            }
        }
    }

    LaunchedEffect(followPausedUntil) {
        if (followPausedUntil > 0L) {
            val remain = followPausedUntil - SystemClock.elapsedRealtime()
            if (remain > 0L) delay(remain)
            followPausedUntil = 0L
        }
    }

    LaunchedEffect(currentIndex, followPausedUntil) {
        if (currentIndex >= 0 && followPausedUntil == 0L) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    BoxWithConstraints(modifier) {
        // 上下留白，让第一句和最后一句也能滚到中间
        val edgePadding = maxHeight * 0.35f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = edgePadding),
        ) {
            itemsIndexed(lines) { index, line ->
                LyricRow(
                    text = line.text,
                    isCurrent = index == currentIndex,
                    onClick = {
                        // 点了具体某一句，立即恢复跟随并跳过去
                        followPausedUntil = 0L
                        onSeek(line.timeMs)
                    },
                )
            }
        }

        if (followPausedUntil > 0L) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
            ) {
                Text(
                    text = "已暂停跟随 · 点歌词跳转，15 秒后回到当前播放",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 手动滑动后暂停自动跟随的时长 */
private const val FOLLOW_RESUME_DELAY_MS = 15_000L

@Composable
private fun LyricRow(
    text: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "lyricColor",
    )
    val fontSize by animateFloatAsState(
        targetValue = if (isCurrent) 18f else 15f,
        label = "lyricSize",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.65f,
        label = "lyricAlpha",
    )

    Text(
        text = text,
        color = color.copy(alpha = alpha),
        fontSize = fontSize.sp,
        lineHeight = 26.sp,
        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
    )
}

@Composable
private fun NoLyricsPanel(
    lyricState: LyricState?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val message = when (lyricState) {
        LyricState.INSTRUMENTAL -> "该音乐为纯音乐，请享受"
        LyricState.LOCAL -> "本地导入 · 无歌词"
        LyricState.MISSING -> "暂无歌词"
        LyricState.PLAIN -> "暂无同步歌词"
        LyricState.SYNCED -> "暂无同步歌词"
        null -> "还没有播放中的歌曲"
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(
                    if (title.isBlank()) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(1).uppercase().ifBlank { "♪" },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
