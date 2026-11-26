package com.puretone.player.data.model

/**
 * 歌词状态。区分「纯音乐」与「应该有但没取到」，两者在界面上的文案不同。
 */
enum class LyricState {
    /** 带 [mm:ss.xx] 时间轴的同步歌词 */
    SYNCED,

    /** 只有纯文本，没有时间轴 */
    PLAIN,

    /** 纯音乐，本就没有歌词 */
    INSTRUMENTAL,

    /** 按元数据应该有歌词，但接口没给 */
    MISSING,

    /** 本地导入的音频，按产品约定不提供歌词 */
    LOCAL,
}
