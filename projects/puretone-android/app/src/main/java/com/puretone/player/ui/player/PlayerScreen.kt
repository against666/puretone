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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.puretone.player.data.db.Track
import com.puretone.player.data.model.LyricState
import com.puretone.player.data.prefs.LyricSize
import com.puretone.player.data.prefs.PlayMode
import com.puretone.player.lyric.LrcParser
import com.puretone.player.player.PreviewState
import com.puretone.player.lyric.LyricLine
import com.puretone.player.lyric.indexAt
import com.puretone.player.ui.components.AlbumArtBox
import com.puretone.player.ui.components.DotSeekBar
import com.puretone.player.ui.components.formatDuration
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as PureToneApplication
    val state by app.player.state.collectAsStateWithLifecycle()
    val playMode by app.player.playMode.collectAsStateWithLifecycle()
    val queue by app.player.queue.collectAsStateWithLifecycle()
    val lyricSize by app.preferences.lyricSize.collectAsStateWithLifecycle(LyricSize.NORMAL)
    val lyricOffsetMs by app.preferences.lyricOffsetMs.collectAsStateWithLifecycle(0)
    val track by remember(state.currentTrackId) {
        if (state.currentTrackId <= 0L) {
            flowOf(null)
        } else {
            app.repository.observeTrack(state.currentTrackId)
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    val lyricLines = remember(track?.id, track?.lyricText, track?.offsetMs, lyricOffsetMs) {
        val raw = track?.lyricText
        val offset = (track?.offsetMs ?: 0) + lyricOffsetMs
        if (raw.isNullOrBlank()) emptyList() else LrcParser.parse(raw, offset)
    }
    val preview by app.previewPlayer.state.collectAsStateWithLifecycle()

    // 试听中：这个页面切换成试听模式（同一套歌词/进度/控制，数据源换成试听播放器）
    if (preview.session != null) {
        PreviewPlayerContent(
            preview = preview,
            lyricSize = lyricSize,
            lyricOffsetMs = lyricOffsetMs,
            onBack = onBack,
            onToggle = app.previewPlayer::toggle,
            onSeekMs = app.previewPlayer::seekTo,
            onSeekFraction = app.previewPlayer::seekToFraction,
            modifier = modifier,
        )
        return
    }

    var showQueue by remember { mutableStateOf(false) }
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

        Spacer(Modifier.height(4.dp))
        AlbumArtBox(
            coverUri = state.coverUri,
            size = 96.dp,
            corner = 16.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))

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
                scale = lyricSize.scale,
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
            // 时长在提交时由播放器实时解析（见 seekToFraction），
            // 不再因为个别歌读不到时长而把整条拖动禁用
            enabled = state.hasMedia,
            onFractionChange = { dragFraction = it },
            onSeekCommit = { fraction ->
                app.player.seekToFraction(fraction)
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
            val modeIcon = when (playMode) {
                PlayMode.SEQUENCE -> R.drawable.ic_repeat
                PlayMode.REPEAT_ONE -> R.drawable.ic_repeat_one
                PlayMode.SHUFFLE -> R.drawable.ic_shuffle
            }
            IconButton(onClick = app.player::cyclePlayMode) {
                Icon(
                    painter = painterResource(modeIcon),
                    contentDescription = playMode.label,
                    tint = if (playMode == PlayMode.SEQUENCE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = app.player::previous, enabled = state.hasMedia) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = "上一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(8.dp))
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
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = app.player::next, enabled = state.hasMedia) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = "下一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { showQueue = true }, enabled = queue.isNotEmpty()) {
                Icon(
                    painter = painterResource(R.drawable.ic_queue),
                    contentDescription = "播放队列",
                    tint = if (queue.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueSheet(
                queue = queue,
                currentTrackId = state.currentTrackId,
                onSelect = { index ->
                    app.player.playAt(index)
                    showQueue = false
                },
            )
        }
    }
}

@Composable
private fun QueueSheet(
    queue: List<Track>,
    currentTrackId: Long,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "播放队列",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${queue.size} 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(queue, key = { _, track -> track.id }) { index, track ->
                val isCurrent = track.id == currentTrackId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(28.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsPanel(
    lines: List<LyricLine>,
    positionMs: Long,
    scale: Float,
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
        // 指针所指的那一行。只在手动滑动期间有意义，其余时候为 null
        val targetIndex = if (followPausedUntil > 0L) targetLineIndex(listState) else null
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = edgePadding),
        ) {
            itemsIndexed(lines) { index, line ->
                LyricRow(
                    text = line.text,
                    isCurrent = index == currentIndex,
                    isTarget = index == targetIndex,
                    scale = scale,
                    onClick = {
                        // 点了具体某一句，立即恢复跟随并跳过去
                        followPausedUntil = 0L
                        onSeek(line.timeMs)
                    },
                )
            }
        }
        // 上下渐隐遮罩：让滚到边缘的歌词淡出去，而不是被硬生生切一刀
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                    ),
                ),
        )

        if (targetIndex != null) {
            val target = lines.getOrNull(targetIndex)
            if (target != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (target.timeMs <= 0L) "00:00" else formatDuration(target.timeMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }
    }
}
/** 手动滑动后暂停自动跟随的时长 */

private const val FOLLOW_RESUME_DELAY_MS = 5_000L
/**
 * 取「指针所指」的那一行：右侧指针固定在歌词区竖直中央，
 * 所以取跨越该中线的可见行；取不到就取中心最近的一行。
 */

private fun targetLineIndex(listState: LazyListState): Int? {
    val info = listState.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    val anchorY = (info.viewportStartOffset + info.viewportEndOffset) / 2
    visible.firstOrNull { anchorY >= it.offset && anchorY < it.offset + it.size }
        ?.let { return it.index }
    return visible.minByOrNull { abs(it.offset + it.size / 2 - anchorY) }?.index
}

@Composable
private fun LyricRow(
    text: String,
    isCurrent: Boolean,
    isTarget: Boolean,
    scale: Float,
    onClick: () -> Unit,
) {
    // 三级层次：正在播放 > 指针所指 > 其余
    val color by animateColorAsState(
        targetValue = when {
            isCurrent -> MaterialTheme.colorScheme.primary
            isTarget -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "lyricColor",
    )
    val fontSize by animateFloatAsState(
        targetValue = when {
            isCurrent -> 18f
            isTarget -> 16.5f
            else -> 15f
        } * scale,
        label = "lyricSize",
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            isCurrent -> 1f
            isTarget -> 0.92f
            else -> 0.65f
        },
        label = "lyricAlpha",
    )
    Text(
        text = text,
        color = color.copy(alpha = alpha),
        fontSize = fontSize.sp,
        lineHeight = (26f * scale).sp,
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
        // 不再画「圆圈 + 首字母」的占位：没有歌词就直说，
        // 一个大字母既丑又没有任何信息量
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 试听模式：歌词、进度、播放控制全部绑定到试听播放器 */
@Composable
private fun PreviewPlayerContent(
    preview: PreviewState,
    lyricSize: LyricSize,
    lyricOffsetMs: Int,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeekMs: (Long) -> Unit,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = preview.session ?: return
    val lyricLines = remember(session.lrc, lyricOffsetMs) {
        val raw = session.lrc
        if (raw.isNullOrBlank()) emptyList() else LrcParser.parse(raw, lyricOffsetMs)
    }

    // 版权受限的歌源站只给几十秒试听片段：音频短、但歌词时间轴明显更长
    val trialClip = preview.durationMs in 1L..40_000L &&
        LrcParser.lastTimestampMs(session.lrc) > preview.durationMs + 20_000L

    // 这首歌是否已下载、下到了哪：已入库的歌不该再显示「未保存到音乐库」
    val app = LocalContext.current.applicationContext as com.puretone.player.PureToneApplication
    var destination by remember(session.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(session.key) {
        val code = session.key.substringBefore(':')
        val songId = session.key.substringAfter(':', "")
        val type = com.puretone.player.data.model.SourceType.fromCode(code)
        destination = if (type == com.puretone.player.data.model.SourceType.LOCAL) {
            null
        } else {
            runCatching { app.onlineRepository.downloadDestination(type, songId) }.getOrNull()
        }
    }

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = preview.durationMs
    val baseFraction = if (duration > 0L) {
        (preview.positionMs.toFloat() / duration).coerceIn(0f, 1f)
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
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
            Text("← 返回")
        }

        Spacer(Modifier.height(4.dp))
        AlbumArtBox(
            coverUri = session.coverUrl,
            size = 96.dp,
            corner = 16.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = session.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = session.artist.ifBlank { session.album },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        if (trialClip) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Text(
                    text = "该音乐因平台版权限制，当前仅支持试听",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (lyricLines.isNotEmpty()) {
            LyricsPanel(
                lines = lyricLines,
                positionMs = preview.positionMs,
                scale = lyricSize.scale,
                onSeek = onSeekMs,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            NoLyricsPanel(
                lyricState = if (session.lrc.isNullOrBlank()) {
                    com.puretone.player.data.model.LyricState.MISSING
                } else {
                    com.puretone.player.data.model.LyricState.PLAIN
                },
                title = session.title,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }

        DotSeekBar(
            fraction = shownFraction,
            enabled = preview.status != PreviewState.Status.LOADING,
            onFractionChange = { dragFraction = it },
            onSeekCommit = { fraction ->
                onSeekFraction(fraction)
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

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (preview.status == PreviewState.Status.PLAYING) {
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
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = destination ?: "在线试听 · 未保存到音乐库",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        )
    }
}
