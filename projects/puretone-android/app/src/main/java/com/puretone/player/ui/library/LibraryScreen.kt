package com.puretone.player.ui.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretone.player.ui.components.ScreenPlaceholder

@Composable
fun LibraryScreen(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenPlaceholder(
        title = "音乐库",
        subtitle = "导入或下载的歌曲会出现在这里。M2 接入本地导入，M5 接入在线下载。",
        modifier = modifier,
    ) {
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onOpenPlayer) {
            Text("预览播放页 →")
        }
    }
}
