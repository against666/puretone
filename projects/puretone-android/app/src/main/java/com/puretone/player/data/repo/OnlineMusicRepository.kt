package com.puretone.player.data.repo

import android.content.Context
import android.util.Log
import com.puretone.player.data.db.Track
import com.puretone.player.data.db.TrackDao
import com.puretone.player.data.file.OnlineTrackSaver
import com.puretone.player.data.model.LyricState
import com.puretone.player.data.model.SourceType
import com.puretone.player.data.online.OnlineApiException
import com.puretone.player.data.online.OnlineMusicApi
import com.puretone.player.data.online.OnlineSong
import com.puretone.player.data.online.OnlineSongDetail
import com.puretone.player.data.prefs.AppPreferences
import com.puretone.player.lyric.LrcParser
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 在线歌曲的搜索与下载。
 *
 * 下载流程：查重 → info 拿全信息 → 下载 MP3 → 下载封面 → 读时长 → 入库。
 * 中途失败要清掉半成品，不留孤儿文件。
 */
class OnlineMusicRepository(
    private val context: Context,
    private val api: OnlineMusicApi,
    private val saver: OnlineTrackSaver,
    private val trackDao: TrackDao,
    private val preferences: AppPreferences,
    private val playlists: PlaylistRepository,
) {

    sealed interface DownloadResult {
        /**
         * [trialClip] 为 true 表示源站只提供了试听片段（版权受限歌曲常见）；
         * [playlistName] 非空表示这次是下载进某个歌单而不是光进音乐库
         */
        data class Added(
            val trialClip: Boolean = false,
            val playlistName: String? = null,
        ) : DownloadResult

        /** 歌本来就在库里，这次只是加进了某个歌单 */
        data class AddedToPlaylist(val playlistName: String) : DownloadResult
        data object AlreadyExists : DownloadResult
        data class Failed(val message: String) : DownloadResult
    }

    suspend fun search(source: SourceType, keyword: String, limit: Int = 50): List<OnlineSong> =
        withContext(Dispatchers.IO) {
            try {
                api.search(source, keyword, limit)
            } catch (e: Exception) {
                markReadOnlyIfNeeded(e)
                throw e
            }
        }

    /** 搜索成功后记一条历史（去重、置顶、上限 20） */
    suspend fun recordKeyword(keyword: String) = preferences.addSearchHistory(keyword)

    /**
     * 试听用：取播放地址与歌词，不落盘。
     *
     * QQ 等音源的播放地址由服务端临时解析，第一次请求常常拿不到（或超时），
     * 服务端解析完会缓存，几秒后重试就能成功——所以这里做三次短重试，
     * 别让用户第一次点就吃「暂时无法试听」。
     */
    suspend fun previewDetail(song: OnlineSong): OnlineSongDetail? = withContext(Dispatchers.IO) {
        var lastError = "接口未返回播放地址"
        repeat(2) { attempt ->
            if (attempt > 0) delay(600L)
            try {
                val detail = api.info(song.source, song.songId)
                if (!detail.mp3Url.isNullOrBlank()) return@withContext detail
                // 接口正常应答但就是没地址：这不是瞬时故障，立刻交给换源兜底，别干等
                return@withContext null
            } catch (e: Exception) {
                markReadOnlyIfNeeded(e)
                lastError = e.message ?: "请求失败"
            }
        }
        Log.w(TAG, "试听地址获取失败（网络异常，已重试）: $lastError")
        null
    }

    /** [targetPlaylistId] 非空时，入库后顺手加进该歌单 */
    /**
     * 当前音源拿不到播放地址时（服务端解析时好时坏），去其它音源找同名同歌手的版本。
     * 只用于试听兜底；下载不做替换，避免用户拿到和所选版本不一致的文件。
     */
    suspend fun previewElsewhere(song: OnlineSong): Pair<SourceType, OnlineSongDetail>? =
        withContext(Dispatchers.IO) {
            val candidates = SourceType.entries
                .filter { it != SourceType.LOCAL && it != song.source }

            for (source in candidates) {
                val list = runCatching { api.search(source, "${song.title} ${song.artist}", limit = 10) }
                    .getOrNull()
                    .orEmpty()
                val match = list.firstOrNull {
                    it.title.equals(song.title, ignoreCase = true) &&
                        (song.artist.isBlank() || it.artist.contains(song.artist, ignoreCase = true) ||
                            song.artist.contains(it.artist, ignoreCase = true))
                } ?: continue

                // 兜底路径用单次请求，不叠加 previewDetail 的重试，避免等待过久
                val detail = runCatching { api.info(source, match.songId) }.getOrNull()
                if (detail?.mp3Url.isNullOrBlank()) continue

                Log.i(TAG, "试听换源: ${song.source.code} -> ${source.code} (${match.title})")
                return@withContext source to detail
            }
            null
        }

    suspend fun download(
        song: OnlineSong,
        targetPlaylistId: Long? = null,
        targetPlaylistName: String? = null,
    ): DownloadResult = withContext(Dispatchers.IO) {
        trackDao.findOnlineDuplicate(song.source.code, song.songId)?.let { existing ->
            // 已经在库里：如果这次选的是歌单，就把它加进那个歌单
            if (targetPlaylistId != null && targetPlaylistName != null) {
                runCatching { playlists.addTracks(targetPlaylistId, listOf(existing.id)) }
                    .onFailure { Log.w(TAG, "加入歌单失败: ${it.message}") }
                return@withContext DownloadResult.AddedToPlaylist(targetPlaylistName)
            }
            return@withContext DownloadResult.AlreadyExists
        }

        val detail = try {
            api.info(song.source, song.songId)
        } catch (e: Exception) {
            markReadOnlyIfNeeded(e)
            return@withContext DownloadResult.Failed(e.message ?: "获取歌曲信息失败")
        }

        val mp3Url = detail.mp3Url
        if (mp3Url.isNullOrBlank()) {
            return@withContext DownloadResult.Failed("该歌曲没有可用的 MP3 资源")
        }

        Log.i(TAG, "开始下载音频: $mp3Url")
        val audioUri = saver.saveAudio(mp3Url, detail.song.title, detail.song.artist, detail.song.album)
        Log.i(TAG, "音频结果: $audioUri")
        if (audioUri == null) {
            return@withContext DownloadResult.Failed("MP3 下载失败")
        }

        // 封面失败不影响入库
        val coverPath = detail.picUrl?.let { saver.saveCover(it, "${song.source.code}_${song.songId}") }

        Log.i(TAG, "封面结果: ${coverPath != null}")
        // MediaMetadataRetriever 偶发卡死（文件刚写完被 MediaScanner 扫描时），
        // 给它 5 秒预算，超时就按 0 时长入库，别让整个下载挂在半路
        val duration = withTimeoutOrNull(5_000L) {
            runCatching { saver.readDuration(audioUri) }.getOrDefault(0L)
        } ?: 0L
        Log.i(TAG, "时长: $duration")
        Log.i(TAG, "开始入库")

        val lyricState = classifyLyrics(detail.lrc)
        val inserted = runCatching {
            trackDao.insert(
                Track(
                    sourceType = song.source,
                    sourceId = song.songId,
                    title = detail.song.title,
                    artist = detail.song.artist,
                    album = detail.song.album,
                    durationMs = duration,
                    fileUri = audioUri.toString(),
                    coverUri = coverPath,
                    lyricText = detail.lrc,
                    lyricState = lyricState,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }.getOrDefault(-1L)

        Log.i(TAG, "入库结果: $inserted")
        if (inserted == -1L) {
            // 入库失败就把文件清掉，别留孤儿
            runCatching { context.contentResolver.delete(audioUri, null, null) }
            return@withContext DownloadResult.Failed("写入音乐库失败")
        }

        // 版权受限的歌源站只返回 30 秒试听片段（网易云 URL 带 -ts 后缀）。
        // 判据：文件短于 40 秒，而歌词时间轴明显超出这个长度
        val lyricEndMs = LrcParser.lastTimestampMs(detail.lrc)
        val trialClip = duration in 1L..40_000L && lyricEndMs > duration + 20_000L
        if (trialClip) {
            Log.i(TAG, "疑似试听片段: 时长 $duration, 歌词末尾 $lyricEndMs, url=$mp3Url")
        }

        // 指定了歌单：入库后再加入歌单（已在歌单里会被主键冲突挡掉）
        if (targetPlaylistId != null) {
            runCatching { playlists.addTracks(targetPlaylistId, listOf(inserted)) }
                .onFailure { Log.w(TAG, "加入歌单失败: ${it.message}") }
        }

        DownloadResult.Added(
            trialClip = trialClip,
            playlistName = targetPlaylistId?.let { targetPlaylistName },
        )
    }

    /**
     * 这首歌已经下载到哪了：音乐库 / 某个歌单 / 没下载。
     * 试听页据此显示「已下载到音乐库」或「已下载到《歌单》」。
     */
    suspend fun downloadDestination(source: SourceType, songId: String): String? =
        withContext(Dispatchers.IO) {
            val track = trackDao.findOnlineDuplicate(source.code, songId) ?: return@withContext null
            val names = playlists.playlistNamesForTrack(track.id)
            when (names.size) {
                0 -> "已下载到音乐库"
                1 -> "已下载到《${names.first()}》"
                else -> "已下载到音乐库（${names.size} 个歌单）"
            }
        }


    /**
     * 接口报密钥类错误（密钥失效、额度不足）时进入只读模式：
     * 本地库、歌单、播放不受影响，只有在线搜索/下载停用，
     * 等远程配置下发新密钥后由 UpdateChecker 自动解除。
     */
    private fun markReadOnlyIfNeeded(e: Exception) {
        val msg = e.message.orEmpty()
        if (e is OnlineApiException && (msg.contains("密钥") || msg.contains("额度"))) {
            preferences.setOnlineReadOnly(true)
        }
    }

    private fun classifyLyrics(lrc: String?): LyricState {
        if (lrc.isNullOrBlank()) return LyricState.MISSING
        if (lrc.contains("纯音乐") || lrc.contains("请欣赏")) return LyricState.INSTRUMENTAL
        val synced = TIME_TAG.matcher(lrc).find()
        return if (synced) LyricState.SYNCED else LyricState.PLAIN
    }

    private companion object {
        const val TAG = "OnlineRepo"
        val TIME_TAG = Pattern.compile("""\[\d{1,2}:\d{1,2}([.:]\d{1,3})?]""")
    }
}
