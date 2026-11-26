package com.puretone.player.ui.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puretone.player.PureToneApplication
import com.puretone.player.data.db.PlaylistWithCount
import com.puretone.player.data.db.Track
import com.puretone.player.ui.components.AlbumArtBox

/**
 * 本地搜索：在音乐库和歌单里找歌。
 * 在线搜索等 M5 接入 API 后再叠在这里。
 */
@Composable
fun SearchScreen(
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as PureToneApplication
    val tracks by app.repository.observeTracks().collectAsStateWithLifecycle(emptyList())
    val playlists by app.playlistRepository.observePlaylists().collectAsStateWithLifecycle(emptyList())

    var query by remember { mutableStateOf("") }

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

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            singleLine = true,
            placeholder = { Text("歌名、歌手或歌单名") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        Spacer(Modifier.height(12.dp))

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
                            onClick = { app.player.play(matchedTracks, index) },
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
