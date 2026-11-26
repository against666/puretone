package com.puretone.player.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.puretone.player.ui.components.ScreenPlaceholder

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    ScreenPlaceholder(
        title = "设置",
        subtitle = "音源切换、歌词字号、版本更新。M4 接入。",
        modifier = modifier,
    )
}
