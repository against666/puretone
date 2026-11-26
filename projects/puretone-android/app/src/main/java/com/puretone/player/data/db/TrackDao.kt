package com.puretone.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.puretone.player.data.model.SourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC, createdAt DESC")
    fun observeFavorites(): Flow<List<Track>>

    /** 最近播放，只取有播放记录的 */
    @Query("SELECT * FROM tracks WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observeById(id: Long): Flow<Track?>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun findById(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE sourceType = :type AND sourceId = :sourceId LIMIT 1")
    suspend fun findBySource(type: SourceType, sourceId: String): Track?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(track: Track): Long

    @Update
    suspend fun update(track: Track)

    @Query("UPDATE tracks SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :time WHERE id = :id")
    suspend fun markPlayed(id: Long, time: Long)

    @Query("UPDATE tracks SET offsetMs = :offsetMs WHERE id = :id")
    suspend fun updateOffset(id: Long, offsetMs: Int)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
