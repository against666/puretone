package com.puretone.player.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.puretone.player.PureToneApplication
import com.puretone.player.core.ALL_SONGS_ID
import com.puretone.player.data.db.Playlist
import com.puretone.player.data.db.Track
import com.puretone.player.data.repo.MusicRepository
import com.puretone.player.data.repo.PlaylistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val isAllSongs: Boolean = playlistId == ALL_SONGS_ID

    val playlist: StateFlow<Playlist?> =
        (if (isAllSongs) flowOf(null) else playlistRepository.observePlaylist(playlistId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tracks: StateFlow<List<Track>> =
        (if (isAllSongs) {
            musicRepository.observeTracks()
        } else {
            playlistRepository.observeTracks(playlistId)
        }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 歌单内的歌只是解除关联，文件仍留在音乐库 */
    fun removeFromPlaylist(trackId: Long) {
        if (isAllSongs) return
        viewModelScope.launch { playlistRepository.removeTrack(playlistId, trackId) }
    }

    /** 「全部歌曲」里删除才是真删，连文件一起清掉 */
    fun deleteFromLibrary(track: Track) {
        viewModelScope.launch { musicRepository.delete(track) }
    }

    fun rename(name: String, onDone: () -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.rename(playlistId, trimmed)
            onDone()
        }
    }

    fun deletePlaylist(onDone: () -> Unit) {
        viewModelScope.launch {
            playlistRepository.delete(playlistId)
            onDone()
        }
    }

    companion object {
        fun factory(app: PureToneApplication, playlistId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlaylistDetailViewModel(playlistId, app.repository, app.playlistRepository)
                }
            }
    }
}
