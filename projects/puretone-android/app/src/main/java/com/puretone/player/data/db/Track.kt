package com.puretone.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.puretone.player.data.model.LyricState
import com.puretone.player.data.model.SourceType

/**
 * 音乐库中的一首歌。
 *
 * 唯一性靠 (sourceType, sourceId)：同一音源同一 id 不会重复入库。
 * 本地导入的 sourceId 为 null，SQLite 的唯一索引允许多个 NULL 并存，正好符合需要。
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["sourceType", "sourceId"], unique = true),
        Index(value = ["isFavorite"]),
        Index(value = ["lastPlayedAt"]),
    ],
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: SourceType,
    val sourceId: String? = null,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long,
    /** 音频文件地址，file:// 或 content:// */
    val fileUri: String,
    /** 封面图本地缓存地址 */
    val coverUri: String? = null,
    /** 歌词原文（LRC 格式），另存一份文件，这里留文本便于检索与降级 */
    val lyricText: String? = null,
    val lyricState: LyricState,
    /** 用户手动微调的整体偏移，正值让歌词延后 */
    val offsetMs: Int = 0,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val createdAt: Long,
)
