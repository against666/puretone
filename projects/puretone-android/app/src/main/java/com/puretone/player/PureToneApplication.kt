package com.puretone.player

import android.app.Application
import com.puretone.player.data.db.PureToneDatabase
import com.puretone.player.data.prefs.AppPreferences

class PureToneApplication : Application() {

    /** 全进程共用一个数据库实例，首次访问时才真正打开 */
    val database: PureToneDatabase by lazy { PureToneDatabase.get(this) }

    /** 应用级偏好，DataStore 支撑 */
    val preferences: AppPreferences by lazy { AppPreferences(this) }
}
