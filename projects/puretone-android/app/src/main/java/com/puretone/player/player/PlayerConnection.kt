package com.puretone.player.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.puretone.player.data.db.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 界面与播放服务之间的桥。
 *
 * UI 不直接持有 ExoPlayer，而是通过 MediaController 与 [PlaybackService] 通信，
 * 这样 Activity 重建、切页都不会影响播放。
 */
class PlayerConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    /** 服务还没连上时用户就点了播放，先记下来，连上后立刻执行 */
    private var pendingPlay: (() -> Unit)? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = sync()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = sync()

        override fun onPlaybackStateChanged(playbackState: Int) = sync()

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = sync()

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) = sync()
    }

    fun connect() {
        if (controllerFuture != null) return

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull()
                if (connected != null) {
                    controller = connected
                    connected.addListener(listener)
                    _state.update { it.copy(isConnected = true) }
                    sync()
                    startProgressTicker()

                    pendingPlay?.invoke()
                    pendingPlay = null
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    /** 以整库为播放队列，从 [startIndex] 开始播 */
    fun play(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return

        val c = controller
        if (c == null) {
            pendingPlay = { play(tracks, startIndex) }
            connect()
            return
        }

        val items = tracks.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare()
        c.play()
        sync()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    /** 播放超过 3 秒时，「上一首」先回到本曲开头，符合主流播放器习惯 */
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000L) {
            c.seekTo(0L)
        } else {
            c.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /** 点歌词行用：跳到这一句并开始播放 */
    fun seekToAndPlay(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs)
        if (!c.isPlaying) c.play()
    }

    private fun sync() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _state.update {
            it.copy(
                isConnected = true,
                isPlaying = c.isPlaying,
                hasMedia = item != null,
                currentTrackId = item?.mediaId?.toLongOrNull() ?: -1L,
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
                coverUri = item?.mediaMetadata?.artworkUri?.toString(),
                durationMs = c.duration.takeIf { d -> d > 0L } ?: 0L,
                positionMs = c.currentPosition.coerceAtLeast(0L),
            )
        }
    }

    /** 播放中每 500ms 刷新进度；暂停时不打扰 */
    private fun startProgressTicker() {
        scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.takeIf { d -> d > 0L } ?: it.durationMs,
                        )
                    }
                }
                delay(500L)
            }
        }
    }
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(fileUri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(coverUri?.let(Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build(),
    )
    .build()
