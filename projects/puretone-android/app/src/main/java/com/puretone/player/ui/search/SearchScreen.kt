package com.puretone.player.ui.search

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.heightIn
import com.puretone.player.ui.theme.Space
import com.puretone.player.PureToneApplication
import com.puretone.player.R
import com.puretone.player.data.db.PlaylistWithCount
import com.puretone.player.data.db.Track
import com.puretone.player.data.model.SourceType
import com.puretone.player.data.online.OnlineSong
import com.puretone.player.player.PreviewState
import com.puretone.player.ui.components.AlbumArtBox

/**
 * 搜索页：本地（音乐库 + 歌单）与在线搜索二选一，顶部芯片切换音源。
 * 在线结果点一下就是下载，下载完成自动进音乐库。
 */
@Composable
fun SearchScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as PureToneApplication
    val onlineViewModel: OnlineSearchViewModel = viewModel(factory = OnlineSearchViewModel.factory(app))

    val tracks by app.repository.observeTracks().collectAsStateWithLifecycle(emptyList())
    val playlists by app.playlistRepository.observePlaylists().collectAsStateWithLifecycle(emptyList())

    // rememberSaveable：进歌词页再返回时，音源和关键字要还在，
    // 否则回到搜索页看到的是默认「本地」空页，像是被踢回了音乐库
    var source by rememberSaveable { mutableStateOf(SourceType.LOCAL) }
    var query by rememberSaveable { mutableStateOf("") }

    val onlineStatus by onlineViewModel.status.collectAsStateWithLifecycle()
    val onlineResults by onlineViewModel.results.collectAsStateWithLifecycle()
    val downloading by onlineViewModel.downloading.collectAsStateWithLifecycle()
    val downloaded by onlineViewModel.downloaded.collectAsStateWithLifecycle()
    val message by onlineViewModel.message.collectAsStateWithLifecycle()
    val onlineReadOnly by app.preferences.onlineReadOnly.collectAsStateWithLifecycle(false)
    val previewState by onlineViewModel.previewState.collectAsStateWithLifecycle()
    val targetPlaylists by onlineViewModel.playlists.collectAsStateWithLifecycle()
    val searchHistory by app.preferences.searchHistory.collectAsStateWithLifecycle(emptyList())
    // 点「下载」时先选去处；没有歌单就直接进音乐库
    var downloadTarget by remember { mutableStateOf<OnlineSong?>(null) }
    val scope = rememberCoroutineScope()
    // 点了「进歌词页」后先不跳：等试听真正开始（session 就绪）再跳，
    // 解析失败则留在搜索页，只弹提示
    var pendingOpenKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(previewState.session?.key, previewState.status) {
        val key = pendingOpenKey ?: return@LaunchedEffect
        when {
            previewState.session?.key == key &&
                previewState.status != PreviewState.Status.LOADING -> {
                pendingOpenKey = null
                onOpenPlayer()
            }

            previewState.status == PreviewState.Status.IDLE -> {
                // 地址没解析出来，试听被取消：不跳页
                pendingOpenKey = null
            }
        }
    }

    val context = LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onlineViewModel.consumeMessage()
        }
    }

    // 在线模式下输入变化触发防抖搜索
    LaunchedEffect(source, query) {
        if (source != SourceType.LOCAL) {
            onlineViewModel.searchDebounced(source, query)
        }
    }

    // 下载目标选择：音乐库 or 某个歌单（放在根布局外，切页不影响）
    downloadTarget?.let { song ->
        AlertDialog(
            onDismissRequest = { downloadTarget = null },
            title = { Text("下载《${song.title}》到") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            downloadTarget = null
                            onlineViewModel.download(song)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "音乐库",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    targetPlaylists.forEach { playlist ->
                        TextButton(
                            onClick = {
                                downloadTarget = null
                                onlineViewModel.download(song, playlist.id, playlist.name)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "歌单 · ${playlist.name}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { downloadTarget = null }) { Text("取消") }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(Space.xl))
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Space.lg),
        )
        Spacer(Modifier.height(Space.s150))

        // 芯片放横向滚动列表：窄屏也放得下五个音源，不会把「酷我」挤成竖排
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item {
                SourceChip(
                    label = "本地",
                    selected = source == SourceType.LOCAL,
                    onClick = { source = SourceType.LOCAL },
                )
            }
            items(SourceType.searchable) { item ->
                SourceChip(
                    label = item.displayName.removeSuffix("音乐"),
                    selected = source == item,
                    onClick = { source = item },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    if (source != SourceType.LOCAL && query.isNotBlank()) {
                        scope.launch { app.preferences.addSearchHistory(query) }
                    }
                    keyboard?.hide()
                },
            ),
            placeholder = {
                Text(
                    if (source == SourceType.LOCAL) {
                        "歌名、歌手或歌单名"
                    } else {
                        "歌名或歌手"
                    },
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        Spacer(Modifier.height(Space.s150))

        if (source != SourceType.LOCAL && onlineReadOnly) {
            ReadOnlyBanner(
                onRetry = {
                    app.preferences.setOnlineReadOnly(false)
                    onlineViewModel.searchDebounced(source, query)
                },
            )
            Spacer(Modifier.height(10.dp))
        }

        if (source == SourceType.LOCAL) {
            LocalResults(
                query = query,
                tracks = tracks,
                playlists = playlists,
                onOpenPlaylist = onOpenPlaylist,
                onPlayTrack = { list, index -> app.player.play(list, index) },
            )
        } else {
            val cacheKey = "${source.code}:${query.trim()}"
            OnlineResults(
                status = onlineStatus,
                keyword = query,
                results = onlineResults[cacheKey].orEmpty(),
                downloading = downloading,
                downloaded = downloaded,
                previewKey = previewState.key,
                previewStatus = previewState.status,
                onTogglePreview = { song ->
                    if (query.isNotBlank()) scope.launch { app.preferences.addSearchHistory(query) }
                    onlineViewModel.togglePreview(song)
                },
                onOpenPlayer = { song ->
                    if (query.isNotBlank()) scope.launch { app.preferences.addSearchHistory(query) }
                    onlineViewModel.playPreview(song)
                    pendingOpenKey = song.key
                },
                history = searchHistory,
                onPickKeyword = { keyword -> query = keyword },
                onRemoveKeyword = { keyword -> scope.launch { app.preferences.removeSearchHistory(keyword) } },
                onClearHistory = { scope.launch { app.preferences.clearSearchHistory() } },
                onDownload = { song ->
                    if (query.isNotBlank()) scope.launch { app.preferences.addSearchHistory(query) }
                    if (targetPlaylists.isEmpty()) {
                        onlineViewModel.download(song)
                    } else {
                        // 缓一拍再弹：弹窗若在当前点击的抬起事件前出现，
                        // 这次点击的 UP 会被当成点遮罩，窗口一闪就被关掉
                        scope.launch {
                            delay(180)
                            downloadTarget = song
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** 密钥失效/额度耗尽后的提示条：本地功能照常，在线功能等新密钥 */
@Composable
private fun ReadOnlyBanner(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.s150),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "在线服务已停用",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "密钥失效或额度不足。本地音乐不受影响，新密钥下发后自动恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onRetry) {
                Text("重试", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun LocalResults(
    query: String,
    tracks: List<Track>,
    playlists: List<PlaylistWithCount>,
    onOpenPlaylist: (Long) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
) {
    val matchedTracks = remember(tracks, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            emptyList()
        } else {
            tracks.filter {
                it.title.contains(keyword, true) ||
                    it.artist.contains(keyword, true) ||
                    it.album.contains(keyword, true)
            }
        }
    }

    val matchedPlaylists = remember(playlists, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            emptyList()
        } else {
            playlists.filter { it.playlist.name.contains(keyword, true) }
        }
    }

    when {
        query.isBlank() -> CenterHint("输入歌名、歌手或歌单名开始搜索。")

        matchedTracks.isEmpty() && matchedPlaylists.isEmpty() ->
            CenterHint("没有找到和「${query.trim()}」相关的内容。")

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.lg),
        ) {
            if (matchedTracks.isNotEmpty()) {
                item { SectionHeader("歌曲 · ${matchedTracks.size}") }
                itemsIndexed(matchedTracks, key = { _, track -> track.id }) { index, track ->
                    TrackResultRow(
                        track = track,
                        onClick = { onPlayTrack(matchedTracks, index) },
                    )
                }
            }

            if (matchedPlaylists.isNotEmpty()) {
                item { SectionHeader("歌单 · ${matchedPlaylists.size}") }
                items(matchedPlaylists, key = { it.playlist.id }) { item ->
                    PlaylistResultRow(
                        item = item,
                        onClick = { onOpenPlaylist(item.playlist.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineResults(
    status: OnlineSearchViewModel.Status,
    keyword: String,
    results: List<OnlineSong>,
    downloading: Set<String>,
    downloaded: Set<String>,
    previewKey: String?,
    previewStatus: PreviewState.Status,
    history: List<String>,
    onPickKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTogglePreview: (OnlineSong) -> Unit,
    onOpenPlayer: (OnlineSong) -> Unit,
    onDownload: (OnlineSong) -> Unit,
) {
    when {
        keyword.isBlank() -> SearchHistoryView(
            history = history,
            onPick = onPickKeyword,
            onRemove = onRemoveKeyword,
            onClear = onClearHistory,
        )

        status == OnlineSearchViewModel.Status.LOADING -> LoadingHint()

        status == OnlineSearchViewModel.Status.FAILED ->
            CenterHint("搜索失败，稍后再试或换个音源。")

        results.isEmpty() -> CenterHint("没有找到和「${keyword.trim()}」相关的歌曲。")

        else -> {
            val listState = rememberLazyListState()
            // 从歌词页返回时不要停在顶部：定位到当前正在试听的那一首。
            // 只在本次进入页面后执行一次，之后用户怎么滚都不打扰
            var didRestoreScroll by remember { mutableStateOf(false) }
            LaunchedEffect(results, previewKey) {
                if (didRestoreScroll || previewKey == null) return@LaunchedEffect
                val index = results.indexOfFirst { it.key == previewKey }
                if (index < 0) return@LaunchedEffect
                didRestoreScroll = true
                listState.scrollToItem(index + 1) // +1 是顶部「歌曲 · N」那一行
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.lg),
            ) {
                item { SectionHeader("歌曲 · ${results.size}") }
                items(results, key = { it.key }) { song ->
                    OnlineSongRow(
                        song = song,
                        downloadState = when {
                            song.key in downloaded -> DownloadState.Done
                            song.key in downloading -> DownloadState.Downloading
                            else -> DownloadState.Idle
                        },
                        previewStatus = if (previewKey == song.key) previewStatus else null,
                        onTogglePreview = { onTogglePreview(song) },
                        onOpenPlayer = { onOpenPlayer(song) },
                        onDownload = { onDownload(song) },
                    )
                }
            }
        }
    }
}

private enum class DownloadState { Idle, Downloading, Done }

@Composable
private fun OnlineSongRow(
    song: OnlineSong,
    downloadState: DownloadState,
    previewStatus: PreviewState.Status?,
    onTogglePreview: () -> Unit,
    onOpenPlayer: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.listRowMin)
            .padding(horizontal = Space.md, vertical = Space.s75),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧按钮：只负责播放/暂停，不和「进歌词页」冲突
        Box(
            modifier = Modifier
                .size(Space.minTouch)
                .clip(CircleShape)
                .clickable(onClick = onTogglePreview),
            contentAlignment = Alignment.Center,
        ) {
            when (previewStatus) {
                PreviewState.Status.LOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )

                PreviewState.Status.PLAYING -> Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = "暂停试听",
                    tint = MaterialTheme.colorScheme.primary,
                )

                PreviewState.Status.PAUSED -> Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "继续试听",
                    tint = MaterialTheme.colorScheme.primary,
                )

                else -> Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "试听",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Space.xxs))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onOpenPlayer)
                .padding(horizontal = Space.s75, vertical = Space.s75),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = listOf(song.artist, song.album)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(Space.sm))

        when (downloadState) {
            // 下载是独立按钮：点它不触发试听。
            // 用 TextButton 而不是给 Text 挂 clickable——自带 48dp 触控区，窄屏也好点
            DownloadState.Idle -> TextButton(
                onClick = onDownload,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "下载",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            DownloadState.Downloading -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )

            DownloadState.Done -> Text(
                text = "已入库",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "搜索中…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun TrackResultRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaylistResultRow(item: PlaylistWithCount, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtBox(
            coverUri = item.coverUri,
            size = 40.dp,
            corner = 10.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.trackCount} 首",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 空状态：靠近内容起点而不是屏幕正中。
 * 居中的空提示会让人以为是加载卡住，紧贴搜索框下方才像"这里可以开始"。
 */
@Composable
private fun CenterHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.s500, start = Space.lg, end = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 空搜索时的「最近搜索」：点一条直接搜，右侧 ✕ 单删，顶部可清空 */
@Composable
private fun SearchHistoryView(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        CenterHint("选好音源，输入歌名或歌手开始搜索。")
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.md, top = Space.s75, bottom = Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近搜索",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("清空", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.lg),
        ) {
            items(history, key = { it }) { keyword ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(keyword) }
                        .heightIn(min = Space.minTouch)
                        .padding(start = Space.lg, end = Space.sm, top = Space.xs, bottom = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = keyword,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onRemove(keyword) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
