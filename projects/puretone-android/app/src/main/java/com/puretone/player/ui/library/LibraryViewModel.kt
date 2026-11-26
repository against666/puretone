package com.puretone.player.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.puretone.player.PureToneApplication
import com.puretone.player.data.db.PlaylistWithCount
import com.puretone.player.data.repo.MusicRepository
import com.puretone.player.data.repo.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistWithCount>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trackCount: StateFlow<Int> = musicRepository.observeTracks()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun import(uris: List<Uri>, onResult: (MusicRepository.ImportResult) -> Unit) {
        viewModelScope.launch {
            _importing.value = true
            val result = runCatching { musicRepository.importLocal(uris) }.getOrNull()
            _importing.value = false
            if (result != null) onResult(result)
        }
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { playlistRepository.create(trimmed) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { playlistRepository.delete(id) }
    }

    companion object {
        fun factory(app: PureToneApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(app.repository, app.playlistRepository) }
        }
    }
}
