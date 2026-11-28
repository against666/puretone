package com.puretone.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.*,
            (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
            (SELECT t.coverUri FROM playlist_tracks pt2
                INNER JOIN tracks t ON t.id = pt2.trackId
                WHERE pt2.playlistId = p.id
                ORDER BY pt2.position ASC LIMIT 1) AS coverUri
        FROM playlists p
        ORDER BY p.createdAt ASC
        """,
    )
    fun observeWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<Playlist?>

    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PlaylistTrackDao {

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position DESC, pt.addedAt DESC
        """,
    )
    fun observeTracks(playlistId: Long): Flow<List<Track>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun trackIds(playlistId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAll(refs: List<PlaylistTrack>)

    /** 某首歌所在的所有歌单名（试听页显示「已下载到《xx》」用） */
    @Query(
        "SELECT p.name FROM playlists p " +
            "INNER JOIN playlist_tracks pt ON p.id = pt.playlistId " +
            "WHERE pt.trackId = :trackId",
    )
    suspend fun playlistNamesForTrack(trackId: Long): List<String>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun remove(playlistId: Long, trackId: Long)
}
