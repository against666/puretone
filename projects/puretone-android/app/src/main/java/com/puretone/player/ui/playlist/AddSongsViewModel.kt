package com.puretone.player.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.puretone.player.PureToneApplication
import com.puretone.player.data.db.PlaylistWithCount
import com.puretone.player.data.db.Track
import com.puretone.player.data.repo.MusicRepository
import com.puretone.player.data.repo.PlaylistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AddSongsViewModel(
    private val targetId: Long,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    /** 来源列表：全部歌曲 + 其他歌单（排除目标歌单自己） */
    val sources: StateFlow<List<PlaylistWithCount>> = playlistRepository.observePlaylists()
        .map { list -> list.filter { it.playlist.id != targetId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sourceId = MutableStateFlow(0L)
    val sourceId: StateFlow<Long> = _sourceId.asStateFlow()

    val tracks: StateFlow<List<Track>> = _sourceId
        .flatMapLatest { id ->
            if (id == 0L) {
                musicRepository.observeTracks()
            } else {
                playlistRepository.observeTracks(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _existing = MutableStateFlow<Set<Long>>(emptySet())

    /** 已经在目标歌单里的歌，列表里置灰不可选 */
    val existing: StateFlow<Set<Long>> = _existing.asStateFlow()

    init {
        viewModelScope.launch {
            _existing.value = playlistRepository.trackIds(targetId)
        }
    }

    fun selectSource(id: Long) {
        _sourceId.value = id
    }

    fun add(trackIds: List<Long>, onDone: (Int) -> Unit) {
        if (trackIds.isEmpty()) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            val count = playlistRepository.addTracks(targetId, trackIds)
            _existing.value = playlistRepository.trackIds(targetId)
            onDone(count)
        }
    }

    companion object {
        fun factory(app: PureToneApplication, playlistId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AddSongsViewModel(playlistId, app.repository, app.playlistRepository)
                }
            }
    }
}
