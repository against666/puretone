package com.puretone.player.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.puretone.player.PureToneApplication
import com.puretone.player.data.model.SourceType
import com.puretone.player.data.online.OnlineSong
import com.puretone.player.data.repo.OnlineMusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 在线搜索的界面状态。
 *
 * 结果按「音源 + 关键字」缓存，切回来不用重新请求；下载中的歌不能重复点。
 */
class OnlineSearchViewModel(
    private val repository: OnlineMusicRepository,
) : ViewModel() {

    enum class Status { IDLE, LOADING, DONE, FAILED }

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _results = MutableStateFlow<Map<String, List<OnlineSong>>>(emptyMap())
    val results: StateFlow<Map<String, List<OnlineSong>>> = _results.asStateFlow()

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _downloaded = MutableStateFlow<Set<String>>(emptySet())
    val downloaded: StateFlow<Set<String>> = _downloaded.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var searchJob: Job? = null

    fun resultsFor(cacheKey: String): List<OnlineSong> = _results.value[cacheKey].orEmpty()

    /** 输入停顿 400ms 才真正请求，避免每敲一个字就烧一次额度 */
    fun searchDebounced(source: SourceType, keyword: String) {
        searchJob?.cancel()
        if (keyword.isBlank()) {
            _status.value = Status.IDLE
            return
        }
        _status.value = Status.LOADING
        searchJob = viewModelScope.launch {
            delay(400)
            runSearch(source, keyword.trim())
        }
    }

    private suspend fun runSearch(source: SourceType, keyword: String) {
        val cacheKey = "${source.code}:$keyword"
        if (_results.value.containsKey(cacheKey)) {
            _status.value = Status.DONE
            return
        }
        try {
            val list = repository.search(source, keyword)
            _results.update { it + (cacheKey to list) }
            _status.value = Status.DONE
        } catch (e: Exception) {
            Log.e(TAG, "在线搜索失败", e)
            _message.value = e.message ?: "搜索失败"
            _status.value = Status.FAILED
        }
    }

    fun download(song: OnlineSong) {
        Log.i(TAG, "download clicked: ${song.key}")
        if (song.key in _downloading.value) return
        _downloading.update { it + song.key }

        viewModelScope.launch {
            val result = runCatching { repository.download(song) }
                .getOrElse { OnlineMusicRepository.DownloadResult.Failed(it.message ?: "下载失败") }

            _downloading.update { it - song.key }
            Log.i(TAG, "download result: $result")
            when (result) {
                OnlineMusicRepository.DownloadResult.Added -> {
                    _downloaded.update { it + song.key }
                    _message.value = "《${song.title}》已下载到音乐库"
                }

                OnlineMusicRepository.DownloadResult.AlreadyExists -> {
                    _downloaded.update { it + song.key }
                    _message.value = "《${song.title}》已经在音乐库里了"
                }

                is OnlineMusicRepository.DownloadResult.Failed ->
                    _message.value = result.message
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        private const val TAG = "OnlineSearch"

        fun factory(app: PureToneApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnlineSearchViewModel(app.onlineRepository) }
        }
    }
}
