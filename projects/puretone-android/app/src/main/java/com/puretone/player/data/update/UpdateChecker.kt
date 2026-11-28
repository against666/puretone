package com.puretone.player.data.update

import android.content.Context
import android.util.Log
import com.puretone.player.BuildConfig
import com.puretone.player.data.online.ApiKeyStore
import com.puretone.player.data.prefs.AppPreferences
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val note: String,
)

/**
 * 轻量版更新检查：只判断版本号，不碰密钥、不做功能开关。
 *
 * 配置以 Gitee 为主地址（国内直连、实时生效），GitHub raw 与 jsDelivr 仅作备份。
 * 冷启动必查一次；回到前台按 10 分钟节流补查。
 * 任何一步失败都静默放弃，绝不打扰用户。
 */
class UpdateChecker(
    context: Context,
    private val preferences: AppPreferences,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val checkMutex = Mutex()

    @Volatile
    private var lastAttemptAt = 0L

    private val _pending = MutableStateFlow<UpdateInfo?>(null)
    val pending: StateFlow<UpdateInfo?> = _pending.asStateFlow()

    /** 打开应用时调用：每次启动都查，不受上次检查时间影响 */
    fun checkOnLaunch() {
        scope.launch { runCheck() }
    }

    /** 回到前台时调用：同样每次都查，30 秒内重复进入才防抖 */
    fun checkOnForeground() {
        scope.launch { runCheck() }
    }

    private suspend fun runCheck() {
        // 只做极短的防抖：onCreate 与首次 onResume 会连着触发，
        // 没必要为同一次「打开」请求两遍
        val now = System.currentTimeMillis()
        if (now - lastAttemptAt < MIN_CHECK_INTERVAL_MS) return
        checkMutex.withLock {
            lastAttemptAt = System.currentTimeMillis()
            check()
        }
    }

    private suspend fun check() {

        val body = CONFIG_URLS.firstNotNullOfOrNull { url ->
            runCatching { fetch(url) }
                .onFailure { Log.d(TAG, "拉取失败 $url: ${it.message}") }
                .getOrNull()
        } ?: return

        // 远程密钥下发：配置里带了 apiKey 就启用轮换；
        // 换了钥匙顺手解除只读模式——上一把钥匙的问题已经解决了
        val remoteKey = body.optString("apiKey", "").trim()
        if (remoteKey != preferences.currentRemoteApiKey()) {
            preferences.setRemoteApiKey(remoteKey)
            if (preferences.currentOnlineReadOnly()) {
                preferences.setOnlineReadOnly(false)
                Log.i(TAG, "检测到新密钥下发，解除在线只读模式")
            }
        }
        ApiKeyStore.update(remoteKey)

        val info = UpdateInfo(
            versionCode = body.optInt("versionCode", 0),
            versionName = body.optString("versionName", ""),
            downloadUrl = body.optString("downloadUrl", ""),
            note = body.optString("note", ""),
        )

        if (info.versionCode <= BuildConfig.VERSION_CODE) {
            // 已经是最新，顺手撤掉可能残留的提示
            _pending.value = null
            return
        }
        if (info.downloadUrl.isBlank()) return

        _pending.value = info
    }

    fun dismiss(info: UpdateInfo) {
        // 只关掉这一次。下次打开应用还会检查、还会提示——
        // 更新提醒不能只出现一次，否则用户点快了就永远看不到了
        _pending.value = null
    }

    private fun fetch(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 6_000
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "UpdateChecker"

        // 仅防抖用：一次「打开」会连着触发 onCreate 和 onResume 两次检查
        const val MIN_CHECK_INTERVAL_MS = 30 * 1000L

        // 地址按优先级排列，第一个能用的生效：
        // 1. Gitee —— 国内直连最快（实测 0.13s），且无缓存
        // 2. GitHub raw —— 内容最新，但国内经常连不上
        // 3. jsDelivr 镜像 —— GitHub 的国内缓存，滞后约 12 小时
        const val CONFIG_GITEE =
            "https://gitee.com/chenhao215/pure-tone/raw/master/config.json"
        const val CONFIG_RAW =
            "https://raw.githubusercontent.com/against666/puretone-release/main/config.json"
        const val CONFIG_CDN =
            "https://cdn.jsdelivr.net/gh/against666/puretone-release@main/config.json"

        val CONFIG_URLS = listOf(CONFIG_GITEE, CONFIG_RAW, CONFIG_CDN)
    }
}
