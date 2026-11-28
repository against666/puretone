package com.puretone.player.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户自建歌单。音乐库本身不是歌单，它是所有歌曲的集合，
 * 界面上用固定的「全部歌曲」入口表示。
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** 歌单列表用：歌单本体 + 歌曲数 + 封面（最早加入那首的专辑图） */
data class PlaylistWithCount(
    @Embedded val playlist: Playlist,
    val trackCount: Int,
    val coverUri: String? = null,
)
