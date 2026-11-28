package com.puretone.player.player

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 正在试听的歌：信息来自搜索接口，歌词来自 info 的 lrc 字段 */
data class PreviewSession(
    val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val lrc: String?,
    val coverUrl: String?,
)

data class PreviewState(
    val key: String? = null,
    val session: PreviewSession? = null,
    val status: Status = Status.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    enum class Status { IDLE, LOADING, PLAYING, PAUSED }
}

/**
 * 在线试听专用播放器。
 *
 * 和主播放器（MediaSessionService 里的那个）完全独立：试听不打断正在听的歌，
 * 不污染播放队列和通知栏。同一时刻只试听一首，切歌即停上一首。
 * 对外暴露带播放进度的状态，供歌词页（试听模式）绑定。
 */
class PreviewPlayer(context: Context) {

    // 懒初始化：没人试听时不为它占一份 ExoPlayer 资源。
    // 监听器和进度轮询都挂在首次创建时，构造 PreviewPlayer 本身不碰播放器。
    private val player: ExoPlayer by lazy { buildPlayer(context) }

    private fun buildPlayer(context: Context): ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // 播完不停在「空态」：回到开头并暂停，歌词页还留着这首歌，方便重听
                        if (playbackState == Player.STATE_ENDED) {
                            seekTo(0L)
                            pause()
                        }
                        publish()
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.w(TAG, "试听失败: ${error.message}")
                        stop()
                    }
                },
            )
            // 歌词页需要进度：播放中每 400ms 推一次位置
            scope.launch {
                while (true) {
                    if (isPlaying) publish()
                    delay(400L)
                }
            }
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    private var session: PreviewSession? = null
    private var loadingKey: String? = null

    fun loading(key: String) {
        loadingKey = key
        _state.value = PreviewState(key = key, status = PreviewState.Status.LOADING)
    }

    fun start(session: PreviewSession, url: String) {
        // 加载途中试听被取消（例如用户先播了本地歌）：放弃这次启动
        if (loadingKey != session.key) return
        this.session = session
        loadingKey = null
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        Log.i(TAG, "试听开始 ${session.key}")
        publish()
    }

    /** 播放/暂停当前试听 */
    fun toggle() {
        if (session == null) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        if (session == null) return
        player.seekTo(positionMs.coerceAtLeast(0L))
        publish()
    }

    fun seekToFraction(fraction: Float) {
        val duration = player.duration.takeIf { it > 0L } ?: return
        player.seekTo((fraction.coerceIn(0f, 1f) * duration).toLong().coerceIn(0L, duration))
        publish()
    }

    fun stop() {
        // 从未试听过：什么都不用做，不触发 ExoPlayer 初始化
        if (session == null && loadingKey == null) return
        Log.i(TAG, "试听停止")
        session = null
        loadingKey = null
        player.stop()
        player.clearMediaItems()
        _state.value = PreviewState()
    }

    private fun publish() {
        val current = session
        if (current == null) {
            val key = loadingKey ?: return
            _state.value = PreviewState(key = key, status = PreviewState.Status.LOADING)
            return
        }

        _state.value = PreviewState(
            key = current.key,
            session = current,
            status = if (player.isPlaying) {
                PreviewState.Status.PLAYING
            } else {
                PreviewState.Status.PAUSED
            },
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
        )
    }

    private companion object {
        const val TAG = "PreviewPlayer"
    }
}
