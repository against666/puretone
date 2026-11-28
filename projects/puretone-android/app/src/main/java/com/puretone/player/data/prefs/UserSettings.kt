package com.puretone.player.data.prefs

/** 播放模式：顺序 / 单曲循环 / 随机 */
enum class PlayMode(val storageKey: String, val label: String) {
    SEQUENCE("sequence", "顺序播放"),
    REPEAT_ONE("repeat_one", "单曲循环"),
    SHUFFLE("shuffle", "随机播放"),
    ;

    fun next(): PlayMode = when (this) {
        SEQUENCE -> REPEAT_ONE
        REPEAT_ONE -> SHUFFLE
        SHUFFLE -> SEQUENCE
    }

    companion object {
        fun fromKey(key: String?): PlayMode =
            entries.firstOrNull { it.storageKey == key } ?: SEQUENCE
    }
}

/** 歌词字号档位，作用于播放页歌词行 */
enum class LyricSize(val storageKey: String, val label: String, val scale: Float) {
    SMALL("small", "小", 0.86f),
    NORMAL("normal", "标准", 1f),
    LARGE("large", "大", 1.18f),
    ;

    companion object {
        fun fromKey(key: String?): LyricSize =
            entries.firstOrNull { it.storageKey == key } ?: NORMAL
    }
}
