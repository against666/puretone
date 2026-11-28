package com.puretone.player.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * 应用级偏好设置：主题、播放模式、歌词显示、更新检查状态。
 *
 * 读取是 Flow，写入走内部 IO 作用域，调用方不需要自己开协程。
 */
class AppPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val themeMode: Flow<ThemeMode> = data().map { ThemeMode.fromKey(it[KEY_THEME_MODE]) }

    fun setThemeMode(mode: ThemeMode) {
        write { it[KEY_THEME_MODE] = mode.storageKey }
    }

    val playMode: Flow<PlayMode> = data().map { PlayMode.fromKey(it[KEY_PLAY_MODE]) }

    fun setPlayMode(mode: PlayMode) {
        write { it[KEY_PLAY_MODE] = mode.storageKey }
    }

    suspend fun currentPlayMode(): PlayMode = PlayMode.fromKey(read()[KEY_PLAY_MODE])

    val lyricSize: Flow<LyricSize> = data().map { LyricSize.fromKey(it[KEY_LYRIC_SIZE]) }

    fun setLyricSize(size: LyricSize) {
        write { it[KEY_LYRIC_SIZE] = size.storageKey }
    }

    val lyricOffsetMs: Flow<Int> = data().map { it[KEY_LYRIC_OFFSET] ?: 0 }

    fun setLyricOffsetMs(value: Int) {
        write { it[KEY_LYRIC_OFFSET] = value }
    }

    /** 远程配置下发的 API 密钥，空串表示用内置密钥 */
    val remoteApiKey: Flow<String> = data().map { it[KEY_REMOTE_API_KEY] ?: "" }

    fun setRemoteApiKey(value: String) {
        write { it[KEY_REMOTE_API_KEY] = value }
    }

    suspend fun currentRemoteApiKey(): String = read()[KEY_REMOTE_API_KEY] ?: ""

    /** 在线服务只读模式：密钥失效/额度不足时置位，新密钥下发后解除 */
    val onlineReadOnly: Flow<Boolean> = data().map { it[KEY_ONLINE_READ_ONLY] ?: false }

    fun setOnlineReadOnly(value: Boolean) {
        write { it[KEY_ONLINE_READ_ONLY] = value }
    }

    suspend fun currentOnlineReadOnly(): Boolean = read()[KEY_ONLINE_READ_ONLY] ?: false

    /** 最近搜索的关键字，新的在前 */
    val searchHistory: Flow<List<String>> = data().map { decodeHistory(it[KEY_SEARCH_HISTORY]) }

    /** 记一条搜索：去重、挪到最前、最多留 20 条 */
    suspend fun addSearchHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        val current = decodeHistory(read()[KEY_SEARCH_HISTORY])
        val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_HISTORY)
        write { it[KEY_SEARCH_HISTORY] = encodeHistory(updated) }
    }

    suspend fun removeSearchHistory(keyword: String) {
        val current = decodeHistory(read()[KEY_SEARCH_HISTORY])
        write {
            it[KEY_SEARCH_HISTORY] = encodeHistory(current.filterNot { it == keyword })
        }
    }

    suspend fun clearSearchHistory() {
        write { it[KEY_SEARCH_HISTORY] = encodeHistory(emptyList()) }
    }

    private fun decodeHistory(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
    }

    private fun encodeHistory(list: List<String>): String {
        val array = org.json.JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }



    private fun data(): Flow<Preferences> = appContext.settingsDataStore.data
        .catch { e ->
            // 文件损坏时退回默认值，不让整个界面崩掉
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    private suspend fun read(): Preferences = data().first()

    private fun write(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch {
            appContext.settingsDataStore.edit { block(it) }
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_PLAY_MODE = stringPreferencesKey("play_mode")
        val KEY_LYRIC_SIZE = stringPreferencesKey("lyric_size")
        val KEY_LYRIC_OFFSET = intPreferencesKey("lyric_offset_ms")
        val KEY_REMOTE_API_KEY = stringPreferencesKey("remote_api_key")
        val KEY_ONLINE_READ_ONLY = booleanPreferencesKey("online_read_only")
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
        const val MAX_HISTORY = 20
    }
}
