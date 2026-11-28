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
import com.puretone.player.data.online.OnlineSongDetail
import com.puretone.player.data.repo.OnlineMusicRepository
import com.puretone.player.data.repo.PlaylistRepository
import com.puretone.player.player.PreviewPlayer
import com.puretone.player.player.PreviewSession
import com.puretone.player.player.PreviewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 在线搜索的界面状态。
 *
 * 结果按「音源 + 关键字」缓存，切回来不用重新请求；下载中的歌不能重复点。
 */
class OnlineSearchViewModel(
    private val repository: OnlineMusicRepository,
    private val previewPlayer: PreviewPlayer,
    playlists: PlaylistRepository,
) : ViewModel() {

    /** 下载目标候选歌单 */
    val playlists: StateFlow<List<com.puretone.player.data.db.Playlist>> =
        playlists.observePlaylists()
            .map { list -> list.map { it.playlist } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /** 试听状态，直接来自试听播放器 */
    val previewState: StateFlow<PreviewState> = previewPlayer.state

    /** 试听信息缓存（地址 + 歌词），同一首反复点不重复请求 */
    private val previewDetails = mutableMapOf<String, OnlineSongDetail>()

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

    fun download(
        song: OnlineSong,
        targetPlaylistId: Long? = null,
        targetPlaylistName: String? = null,
    ) {
        Log.i(TAG, "download clicked: ${song.key} -> ${targetPlaylistId ?: "音乐库"}")
        if (song.key in _downloading.value) return
        _downloading.update { it + song.key }

        viewModelScope.launch {
            val result = runCatching {
                repository.download(song, targetPlaylistId, targetPlaylistName)
            }
                .getOrElse { OnlineMusicRepository.DownloadResult.Failed(it.message ?: "下载失败") }

            _downloading.update { it - song.key }
            Log.i(TAG, "download result: $result")
            when (result) {
                is OnlineMusicRepository.DownloadResult.Added -> {
                    _downloaded.update { it + song.key }
                    val where = result.playlistName?.let { "《$it》" } ?: "音乐库"
                    _message.value = if (result.trialClip) {
                        "《${song.title}》源站只提供 30 秒试听片段（版权限制），已存入$where"
                    } else {
                        "《${song.title}》已下载到$where"
                    }
                }

                is OnlineMusicRepository.DownloadResult.AddedToPlaylist -> {
                    _downloaded.update { it + song.key }
                    _message.value = "《${song.title}》已加入歌单《${result.playlistName}》"
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

    /** 左侧按钮：播放/暂停当前试听，或开始试听这一首 */
    fun togglePreview(song: OnlineSong) {
        val current = previewState.value
        if (current.key == song.key && current.session != null) {
            previewPlayer.toggle()
            return
        }
        loadAndStart(song)
    }

    /** 点中间信息区：进入歌词页前确保这首歌正在试听（暂停状态则继续播） */
    fun playPreview(song: OnlineSong) {
        val current = previewState.value
        if (current.key == song.key && current.session != null) {
            if (current.status == PreviewState.Status.PAUSED) previewPlayer.toggle()
            return
        }
        loadAndStart(song)
    }

    fun stopPreview() = previewPlayer.stop()

    private fun loadAndStart(song: OnlineSong) {
        previewDetails[song.key]?.let { detail ->
            // 先置加载态：start() 只认当前挂起的加载，
            // 这样「加载途中用户去播了本地歌」时这次启动会被丢弃
            previewPlayer.loading(song.key)
            previewPlayer.start(sessionOf(song, detail), detail.mp3Url!!)
            return
        }
        if (previewState.value.key == song.key &&
            previewState.value.status == PreviewState.Status.LOADING
        ) {
            return
        }

        viewModelScope.launch {
            previewPlayer.loading(song.key)
            var detail = repository.previewDetail(song)

            // 主音源解析不出来：换个音源兜底，别让用户干等
            if (detail?.mp3Url.isNullOrBlank()) {
                val alternative = repository.previewElsewhere(song)
                if (alternative != null) {
                    val (source, altDetail) = alternative
                    _message.value = "${song.source.displayName}暂无播放地址，已改用${source.displayName}试听"
                    detail = altDetail
                }
            }

            if (detail?.mp3Url.isNullOrBlank()) {
                previewPlayer.stop()
                _message.value = "暂时拿不到播放地址，稍后再试或换个音源"
                return@launch
            }
            previewDetails[song.key] = detail!!
            previewPlayer.start(sessionOf(song, detail), detail.mp3Url)
        }
    }

    private fun sessionOf(song: OnlineSong, detail: OnlineSongDetail) = PreviewSession(
        key = song.key,
        title = song.title.ifBlank { detail.song.title },
        artist = song.artist.ifBlank { detail.song.artist },
        album = song.album.ifBlank { detail.song.album },
        lrc = detail.lrc,
        coverUrl = detail.picUrl,
    )

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        private const val TAG = "OnlineSearch"

        fun factory(app: PureToneApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OnlineSearchViewModel(app.onlineRepository, app.previewPlayer, app.playlistRepository)
            }
        }
    }
}
