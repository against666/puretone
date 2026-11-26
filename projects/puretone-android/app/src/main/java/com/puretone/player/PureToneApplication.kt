package com.puretone.player

import android.app.Application
import com.puretone.player.data.db.PureToneDatabase
import com.puretone.player.data.file.LocalImporter
import com.puretone.player.data.prefs.AppPreferences
import com.puretone.player.data.repo.MusicRepository
import com.puretone.player.data.repo.PlaylistRepository
import com.puretone.player.player.PlayerConnection

class PureToneApplication : Application() {

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

    /** 与播放服务的连接，跟着进程存活 */
    val player: PlayerConnection by lazy { PlayerConnection(this) }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(
            playlistDao = database.playlistDao(),
            playlistTrackDao = database.playlistTrackDao(),
        )
    }
}
