package com.puretone.player.ui.playlist

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.puretone.player.PureToneApplication
import com.puretone.player.data.db.Track
import com.puretone.player.ui.components.formatDuration

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PureToneApplication
    val viewModel: PlaylistDetailViewModel = viewModel(
        key = "playlist-$playlistId",
        factory = PlaylistDetailViewModel.factory(app, playlistId),
    )

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playerState by app.player.state.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<Track?>(null) }

    val title = if (viewModel.isAllSongs) "全部歌曲" else (playlist?.name ?: "歌单")

    val filtered = remember(tracks, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(keyword, ignoreCase = true) ||
                    it.artist.contains(keyword, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("←") }

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${tracks.size} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!viewModel.isAllSongs) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                menuOpen = false
                                showRename = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除歌单") },
                            onClick = {
                                menuOpen = false
                                showDelete = true
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("搜索歌单内歌曲") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            if (!viewModel.isAllSongs) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onAddSongs) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "添加歌曲",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        when {
            tracks.isEmpty() -> EmptyHint(
                text = if (viewModel.isAllSongs) {
                    "音乐库还没有歌，回到上一页点「导入」。"
                } else {
                    "歌单还是空的。点右上角的「＋」从音乐库或其他歌单添加。"
                },
            )

            filtered.isEmpty() -> EmptyHint(text = "没有匹配「${query.trim()}」的歌曲。")

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                itemsIndexed(filtered, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        number = if (query.isBlank()) index + 1 else null,
                        track = track,
                        isCurrent = track.id == playerState.currentTrackId,
                        isAllSongs = viewModel.isAllSongs,
                        onClick = {
                            app.player.play(filtered, index)
                            // 播放后直接进播放页由迷你条承担，这里只负责起播
                        },
                        onRemove = { pendingRemove = track },
                    )
                    if (index != filtered.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 80.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        RenameDialog(
            initial = playlist?.name.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = { name ->
                viewModel.rename(name) { showRename = false }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除歌单？") },
            text = { Text("「${playlist?.name}」会被删除，歌单里的歌曲仍保留在音乐库。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        viewModel.deletePlaylist(onDone = onBack)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            },
        )
    }

    pendingRemove?.let { track ->
        val isAll = viewModel.isAllSongs
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(if (isAll) "从音乐库删除？" else "从歌单移除？") },
            text = {
                Text(
                    if (isAll) {
                        "《${track.title}》将从音乐库移除，同时删除 Music/PureTone 下的文件。此操作不可撤销。"
                    } else {
                        "《${track.title}》将从这个歌单移除，文件仍保留在音乐库，可以再次添加。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isAll) viewModel.deleteFromLibrary(track) else viewModel.removeFromPlaylist(track.id)
                        pendingRemove = null
                    },
                ) { Text(if (isAll) "删除" else "移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TrackRow(
    number: Int?,
    track: Track,
    isCurrent: Boolean,
    isAllSongs: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(14.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${track.artist} · ${formatDuration(track.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = if (isAllSongs) Icons.Filled.Delete else Icons.Filled.Clear,
                contentDescription = if (isAllSongs) "删除" else "移除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
