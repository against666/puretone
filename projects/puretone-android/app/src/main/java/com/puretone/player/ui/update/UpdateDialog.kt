package com.puretone.player.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puretone.player.data.update.ApkUpdater
import com.puretone.player.data.update.UpdateInfo

/**
 * 更新弹窗：点「立即更新」直接在应用内下载 APK，下完自动弹系统安装器。
 *
 * 不走浏览器——Release 页面在手机上常被引导到源码 zip，解压再安装太折腾。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    updater: ApkUpdater,
    onDismiss: () -> Unit,
) {
    val downloadState by updater.state.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column {
                if (info.note.isNotBlank()) {
                    Text(
                        text = info.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                when (val state = downloadState) {
                    is ApkUpdater.State.Downloading -> {
                        Text(
                            text = "正在下载更新包 ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            // 约 30MB，提醒别切网
                            text = "约 30 MB，请保持网络连接。可以关掉弹窗，下载会在后台继续。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is ApkUpdater.State.Ready,
                    is ApkUpdater.State.Installing,
                    -> Text(
                        text = "下载完成，正在打开安装界面…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    is ApkUpdater.State.Failed -> Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    ApkUpdater.State.Idle -> Text(
                        text = "当前版本 $currentVersion",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                ApkUpdater.State.Idle -> TextButton(
                    onClick = { updater.download(info.downloadUrl, info.versionName) },
                ) { Text("立即更新") }

                is ApkUpdater.State.Failed -> TextButton(
                    onClick = { updater.download(info.downloadUrl, info.versionName) },
                ) { Text("重试") }

                else -> Unit
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    // 下载中关掉弹窗不打断下载；下次打开时若还没装完，进度会接着显示
                    if (downloadState !is ApkUpdater.State.Downloading) updater.reset()
                    onDismiss()
                },
            ) {
                Text(if (downloadState is ApkUpdater.State.Downloading) "后台下载" else "稍后")
            }
        },
    )
}
