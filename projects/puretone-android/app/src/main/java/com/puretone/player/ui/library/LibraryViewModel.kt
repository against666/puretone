package com.puretone.player.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.puretone.player.PureToneApplication
import com.puretone.player.data.db.Track
import com.puretone.player.data.repo.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: MusicRepository) : ViewModel() {

    val tracks: StateFlow<List<Track>> = repository.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun import(uris: List<Uri>, onResult: (MusicRepository.ImportResult) -> Unit) {
        viewModelScope.launch {
            _importing.value = true
            val result = runCatching { repository.importLocal(uris) }.getOrNull()
            _importing.value = false
            if (result != null) onResult(result)
        }
    }

    fun delete(track: Track) {
        viewModelScope.launch { repository.delete(track) }
    }

    companion object {
        fun factory(app: PureToneApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(app.repository) }
        }
    }
}
