package com.puretone.player.player

/**
 * 播放器的界面状态。UI 只读这个结构，不直接碰 ExoPlayer。
 */
data class PlayerUiState(
    val isConnected: Boolean = false,
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTrackId: Long = -1L,
    val title: String = "",
    val artist: String = "",
    val coverUri: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
)
