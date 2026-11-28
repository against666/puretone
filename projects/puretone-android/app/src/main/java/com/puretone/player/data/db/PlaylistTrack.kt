package com.puretone.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 歌单与歌曲的多对多关联。
 *
 * 两个外键都设成 CASCADE：删歌单时清掉它的关联，删歌曲时从所有歌单里移除，
 * 不会留下指向不存在记录的脏数据。
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Track::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val addedAt: Long,
    /** 歌单内排序，新加入的排在末尾 */
    val position: Int,
)
