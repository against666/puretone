package com.puretone.player.data.repo

import com.puretone.player.data.db.Playlist
import com.puretone.player.data.db.PlaylistDao
import com.puretone.player.data.db.PlaylistTrack
import com.puretone.player.data.db.PlaylistTrackDao
import com.puretone.player.data.db.PlaylistWithCount
import com.puretone.player.data.db.Track
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val playlistTrackDao: PlaylistTrackDao,
) {

    fun observePlaylists(): Flow<List<PlaylistWithCount>> = playlistDao.observeWithCount()

    fun observePlaylist(id: Long): Flow<Playlist?> = playlistDao.observeById(id)

    fun observeTracks(id: Long): Flow<List<Track>> = playlistTrackDao.observeTracks(id)

    suspend fun trackIds(id: Long): Set<Long> = playlistTrackDao.trackIds(id).toSet()

    /** 某首歌所在的所有歌单名 */
    suspend fun playlistNamesForTrack(trackId: Long): List<String> =
        playlistTrackDao.playlistNamesForTrack(trackId)

    suspend fun create(name: String): Long =
        playlistDao.insert(Playlist(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = playlistDao.rename(id, name.trim())

    suspend fun delete(id: Long) = playlistDao.delete(id)

    /**
     * 批量加入歌单。已在歌单里的会被主键冲突挡掉（IGNORE），不会重复。
     * position 从当前最大值往后排，保持加入顺序。
     */
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>): Int {
        if (trackIds.isEmpty()) return 0

        var position = playlistTrackDao.maxPosition(playlistId) + 1
        val now = System.currentTimeMillis()
        val refs = trackIds.map { trackId ->
            PlaylistTrack(
                playlistId = playlistId,
                trackId = trackId,
                addedAt = now,
                position = position++,
            )
        }
        playlistTrackDao.addAll(refs)
        return refs.size
    }

    suspend fun removeTrack(playlistId: Long, trackId: Long) =
        playlistTrackDao.remove(playlistId, trackId)
}
