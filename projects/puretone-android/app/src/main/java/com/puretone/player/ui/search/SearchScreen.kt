package com.puretone.player.ui.search

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.puretone.player.PureToneApplication
import com.puretone.player.data.db.PlaylistWithCount
import com.puretone.player.data.db.Track
import com.puretone.player.data.model.SourceType
import com.puretone.player.data.online.OnlineSong
import com.puretone.player.ui.components.AlbumArtBox

/**
 * 搜索页：本地（音乐库 + 歌单）与在线搜索二选一，顶部芯片切换音源。
 * 在线结果点一下就是下载，下载完成自动进音乐库。
 */
@Composable
fun SearchScreen(
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as PureToneApplication
    val onlineViewModel: OnlineSearchViewModel = viewModel(factory = OnlineSearchViewModel.factory(app))

    val tracks by app.repository.observeTracks().collectAsStateWithLifecycle(emptyList())
    val playlists by app.playlistRepository.observePlaylists().collectAsStateWithLifecycle(emptyList())

    var source by remember { mutableStateOf(SourceType.LOCAL) }
    var query by remember { mutableStateOf("") }

    val onlineStatus by onlineViewModel.status.collectAsStateWithLifecycle()
    val onlineResults by onlineViewModel.results.collectAsStateWithLifecycle()
    val downloading by onlineViewModel.downloading.collectAsStateWithLifecycle()
    val downloaded by onlineViewModel.downloaded.collectAsStateWithLifecycle()
    val message by onlineViewModel.message.collectAsStateWithLifecycle()
    val onlineReadOnly by app.preferences.onlineReadOnly.collectAsStateWithLifecycle(false)

    val context = LocalContext.current
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

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(10.dp))

        // 芯片放横向滚动列表：窄屏也放得下五个音源，不会把「酷我」挤成竖排
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                .padding(horizontal = 24.dp),
            singleLine = true,
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

        Spacer(Modifier.height(12.dp))

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
                onDownload = onlineViewModel::download,
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
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
            contentPadding = PaddingValues(bottom = 16.dp),
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
    onDownload: (OnlineSong) -> Unit,
) {
    when {
        keyword.isBlank() -> CenterHint("选好音源，输入歌名或歌手开始搜索。")

        status == OnlineSearchViewModel.Status.LOADING -> LoadingHint()

        status == OnlineSearchViewModel.Status.FAILED ->
            CenterHint("搜索失败，稍后再试或换个音源。")

        results.isEmpty() -> CenterHint("没有找到和「${keyword.trim()}」相关的歌曲。")

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { SectionHeader("歌曲 · ${results.size}") }
            items(results, key = { it.key }) { song ->
                OnlineSongRow(
                    song = song,
                    state = when {
                        song.key in downloaded -> DownloadState.Done
                        song.key in downloading -> DownloadState.Downloading
                        else -> DownloadState.Idle
                    },
                    onClick = { onDownload(song) },
                )
            }
        }
    }
}

private enum class DownloadState { Idle, Downloading, Done }

@Composable
private fun OnlineSongRow(
    song: OnlineSong,
    state: DownloadState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state == DownloadState.Idle, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
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

        Spacer(Modifier.width(12.dp))

        when (state) {
            DownloadState.Idle -> Text(
                text = "下载",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

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
            Spacer(Modifier.height(2.dp))
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

@Composable
private fun CenterHint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
