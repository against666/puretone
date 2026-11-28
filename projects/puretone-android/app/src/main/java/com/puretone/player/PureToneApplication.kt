package com.puretone.player

import android.app.Application
import com.puretone.player.BuildConfig
import com.puretone.player.data.db.PureToneDatabase
import com.puretone.player.data.file.LocalImporter
import com.puretone.player.data.file.OnlineTrackSaver
import com.puretone.player.data.online.ApiKeyStore
import com.puretone.player.data.online.OnlineMusicApi
import com.puretone.player.data.prefs.AppPreferences
import com.puretone.player.data.repo.MusicRepository
import com.puretone.player.data.repo.OnlineMusicRepository
import com.puretone.player.data.repo.PlaylistRepository
import com.puretone.player.data.update.UpdateChecker
import com.puretone.player.player.PlayerConnection
import com.puretone.player.player.PreviewPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PureToneApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 把上次远程下发的密钥装进内存；启动时静默检查更新，失败不打扰用户
        appScope.launch { ApiKeyStore.update(preferences.currentRemoteApiKey()) }
        updateChecker.checkOnLaunch()

        // 主播放器一开播就结束试听：否则用户播了本地歌曲，
        // 底部迷你条还停在试听那首上（试听会话没被清掉）。
        // 必须在主线程收：ExoPlayer 不允许跨线程访问，IO 线程调 stop() 会崩
        appScope.launch(Dispatchers.Main.immediate) {
            player.state
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { playing -> if (playing) previewPlayer.stop() }
        }
    }

    /** 全进程共用一个数据库实例，首次访问时才真正打开 */
    val database: PureToneDatabase by lazy { PureToneDatabase.get(this) }

    /** 应用级偏好，DataStore 支撑 */
    val preferences: AppPreferences by lazy { AppPreferences(this) }

    val repository: MusicRepository by lazy {
        MusicRepository(
            context = this,
            trackDao = database.trackDao(),
            importer = LocalImporter(this),
        )
    }

    /** 在线搜索与下载。密钥经 ApiKeyStore 现取，支持远程轮换 */
    val onlineRepository: OnlineMusicRepository by lazy {
        OnlineMusicRepository(
            context = this,
            api = OnlineMusicApi(
                keyProvider = {
                    ApiKeyStore.effective(BuildConfig.PURETONE_API_KEY)
                },
            ),
            saver = OnlineTrackSaver(this),
            trackDao = database.trackDao(),
            preferences = preferences,
            playlists = playlistRepository,
        )
    }

    /** 在线试听用的独立播放器，与主播放器互不干扰 */
    val previewPlayer: PreviewPlayer by lazy { PreviewPlayer(this) }

    /** 与播放服务的连接，跟着进程存活 */
    val player: PlayerConnection by lazy { PlayerConnection(this, preferences) }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(
            playlistDao = database.playlistDao(),
            playlistTrackDao = database.playlistTrackDao(),
        )
    }

    val updateChecker: UpdateChecker by lazy { UpdateChecker(this, preferences) }
}
