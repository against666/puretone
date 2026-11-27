package com.puretone.player

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.puretone.player.data.prefs.ThemeMode
import com.puretone.player.ui.PureToneApp
import com.puretone.player.ui.theme.PureToneTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app: PureToneApplication
        get() = application as PureToneApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIncomingAudio(intent)

        setContent {
            val themeMode by app.preferences.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            PureToneTheme(darkTheme = useDarkTheme) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !useDarkTheme
                        controller.isAppearanceLightNavigationBars = !useDarkTheme
                    }
                }

                PureToneApp(
                    themeMode = themeMode,
                    onThemeModeChange = app.preferences::setThemeMode,
                )

                RequestNotificationPermission()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从后台切回来补一次节流检查：App 更新配置可能在进程还活着时变了
        app.updateChecker.checkOnForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAudio(intent)
    }

    /**
     * 从文件管理器或别的 App「用 PureTone 打开」音频时，直接导入。
     * ACTION_VIEW 带来的 content:// 已经带读权限，不需要额外申请存储权限。
     */
    private fun handleIncomingAudio(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return

        lifecycleScope.launch {
            val result = runCatching { app.repository.importLocal(listOf(uri)) }.getOrNull()
            val message = when {
                result == null -> "导入失败"
                result.added > 0 -> "已导入到音乐库"
                result.skipped > 0 -> "这个文件已经在库里了"
                else -> "导入失败"
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Android 13+ 要展示媒体通知必须先拿到通知权限。
 * 没授权也不影响播放，只是通知栏看不到控制条。
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝也不阻断使用，下一次启动还会再问 */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
