package com.puretone.player.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.puretone.player.ui.components.ScreenPlaceholder

@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    ScreenPlaceholder(
        title = "搜索",
        subtitle = "音源：QQ音乐 · 网易云音乐 · 酷狗音乐 · 酷我音乐\nM5 接入搜索与下载。",
        modifier = modifier,
    )
}
