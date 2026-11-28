package com.puretone.player.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用内更新：自己下载 APK，再交给系统安装器。
 *
 * 不去浏览器——浏览器下载 Release 资产常常拿到的是 zip（整个仓库源码包），
 * 用户还要解压，体验很差。这里直连 APK 地址，下完直接弹安装。
 */
class ApkUpdater(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data class Downloading(val progress: Float) : State
        data class Ready(val file: File) : State
        data class Failed(val message: String) : State
        data object Installing : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val dir: File by lazy {
        File(context.filesDir, "updates").apply { mkdirs() }
    }

    fun download(url: String, versionName: String) {
        if (_state.value is State.Downloading) return
        _state.value = State.Downloading(0f)

        scope.launch {
            val target = File(dir, "puretone-$versionName.apk")
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        _state.value = State.Failed("下载失败（HTTP ${connection.responseCode}）")
                        return@launch
                    }
                    val total = connection.contentLengthLong.takeIf { it > 0 }
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total != null) {
                                    _state.value = State.Downloading(
                                        (written.toFloat() / total).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                if (target.length() < 1024 * 1024) {
                    // 不到 1MB 基本不是正常安装包（多半是错误页/HTML）
                    target.delete()
                    _state.value = State.Failed("下载内容异常，请稍后重试")
                    return@launch
                }

                Log.i(TAG, "更新包已下载: ${target.absolutePath} (${target.length()})")
                _state.value = State.Ready(target)
                install()
            } catch (e: Exception) {
                Log.w(TAG, "更新包下载失败", e)
                target.delete()
                _state.value = State.Failed(e.message ?: "下载失败")
            }
        }
    }

    /** 拉起系统安装器；用户未授权「安装未知应用」时系统会引导开启 */
    fun install() {
        val file = (_state.value as? State.Ready)?.file ?: return
        _state.value = State.Installing

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "拉起安装器失败", it)
                _state.value = State.Failed("无法启动安装器，请检查「安装未知应用」权限")
            }
    }

    fun reset() {
        _state.value = State.Idle
    }

    private companion object {
        const val TAG = "ApkUpdater"
    }
}
