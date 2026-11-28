package com.puretone.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puretone.player.PureToneApplication
import com.puretone.player.player.PreviewState
import com.puretone.player.R
import com.puretone.player.lyric.LrcParser
import com.puretone.player.lyric.indexAt
import kotlinx.coroutines.flow.flowOf

/**
 * 迷你条的数据宿主。
 *
 * 播放状态每 500ms 变一次，如果让外面的 Scaffold 直接读，会让整棵导航树
 * 跟着重组；把读取限制在这个小组件里，界面切页时就不会被进度刷新拖累。
 */
@Composable
fun PlayerBarHost(
    app: PureToneApplication,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by app.player.state.collectAsStateWithLifecycle()

    val track by remember(state.currentTrackId) {
        if (state.currentTrackId <= 0L) {
            flowOf(null)
        } else {
            app.repository.observeTrack(state.currentTrackId)
        }
    }.collectAsStateWithLifecycle(initialValue = null)

    if (!state.hasMedia) return

    MiniPlayer(
        title = state.title,
        artist = state.artist,
        coverUri = state.coverUri,
        lyricText = track?.lyricText,
        positionMs = state.positionMs,
        isPlaying = state.isPlaying,
        progress = if (state.durationMs > 0L) {
            state.positionMs.toFloat() / state.durationMs
        } else {
            0f
        },
        onClick = onOpenPlayer,
        onTogglePlay = app.player::togglePlayPause,
        modifier = modifier,
    )
}

/**
 * 悬浮在底部导航栏上方的迷你播放条。
 *
 * 形态：左右留白、左端方角（放方形专辑图）、右端接近半圆的圆角，
 * 与导航栏之间留出缝隙，靠形状和间距把两者分开。
 */
@Composable
fun MiniPlayer(
    title: String,
    artist: String,
    coverUri: String?,
    lyricText: String?,
    positionMs: Long,
    isPlaying: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 歌词只在文本变化时解析一次，进度变化只重新定位当前行
    val lines = remember(lyricText) {
        if (lyricText.isNullOrBlank()) emptyList() else LrcParser.parse(lyricText)
    }
    val currentLyric = remember(lines, positionMs) {
        lines.getOrNull(lines.indexAt(positionMs))?.text
    }

    val shape = RoundedCornerShape(
        topStart = 10.dp,
        topEnd = 32.dp,
        bottomEnd = 32.dp,
        bottomStart = 10.dp,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtBox(
                    coverUri = coverUri,
                    size = 64.dp,
                    corner = 0.dp,
                )

                Spacer(Modifier.width(10.dp))

                Column(Modifier.widthIn(max = 96.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!currentLyric.isNullOrBlank()) {
                    Text(
                        text = currentLyric,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                IconButton(onClick = onTogglePlay) {
                    if (isPlaying) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pause),
                            contentDescription = "暂停",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "播放",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底部音频条：试听存在时优先显示试听，否则显示主播放器的迷你条。
 *
 * 单独包一层是为了让「谁该显示」的判断发生在这一层，
 * 外面的导航树不用跟着 400ms 的试听进度重组。
 */
@Composable
fun BottomAudioBar(
    app: PureToneApplication,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview by app.previewPlayer.state.collectAsStateWithLifecycle()
    val session = preview.session

    if (session == null) {
        PlayerBarHost(app = app, onOpenPlayer = onOpenPlayer, modifier = modifier)
        return
    }

    MiniPlayer(
        title = session.title,
        artist = "在线试听 · ${session.artist}",
        coverUri = null,
        lyricText = session.lrc,
        positionMs = preview.positionMs,
        isPlaying = preview.status == PreviewState.Status.PLAYING,
        progress = if (preview.durationMs > 0L) {
            preview.positionMs.toFloat() / preview.durationMs
        } else {
            0f
        },
        onClick = onOpenPlayer,
        onTogglePlay = app.previewPlayer::toggle,
        modifier = modifier,
    )
}
