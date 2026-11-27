package com.puretone.player.player

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PreviewState(
    val key: String? = null,
    val status: Status = Status.IDLE,
) {
    enum class Status { IDLE, LOADING, PLAYING, PAUSED }
}

/**
 * 在线试听专用播放器。
 *
 * 和主播放器（MediaSessionService 里的那个）完全独立：
 * 试听不会打断用户正在听的歌，也不会污染播放队列和通知栏。
 * 同一时刻只试听一首，切歌即停上一首，播完自动停。
 */
class PreviewPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true,
        )
    }

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    private var currentKey: String? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val key = currentKey ?: return
                    _state.value = PreviewState(
                        key = key,
                        status = if (isPlaying) PreviewState.Status.PLAYING else PreviewState.Status.PAUSED,
                    )
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // 试听播完自动回到待命，不留一个卡住的暂停态
                    if (playbackState == Player.STATE_ENDED) stop()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.w(TAG, "试听失败: ${error.message}")
                    stop()
                }
            },
        )
    }

    fun loading(key: String) {
        _state.value = PreviewState(key, PreviewState.Status.LOADING)
    }

    /** 同一首：播放中则暂停，暂停中则继续；不同首：换歌重放 */
    fun toggle(key: String, url: String) {
        if (currentKey == key) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            return
        }

        currentKey = key
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        Log.i(TAG, "试听开始 $key")
    }

    fun stop() {
        if (currentKey != null) Log.i(TAG, "试听停止")
        player.stop()
        player.clearMediaItems()
        currentKey = null
        _state.value = PreviewState()
    }

    private companion object {
        const val TAG = "PreviewPlayer"
    }
}
