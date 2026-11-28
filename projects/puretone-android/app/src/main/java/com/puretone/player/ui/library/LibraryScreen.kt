package com.puretone.player.ui.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalButton
import com.puretone.player.ui.theme.Space
import com.puretone.player.PureToneApplication
import com.puretone.player.core.ALL_SONGS_ID
import com.puretone.player.data.db.PlaylistWithCount
import com.puretone.player.ui.components.AlbumArtBox

@Composable
fun LibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PureToneApplication
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val trackCount by viewModel.trackCount.collectAsStateWithLifecycle()
    val allSongsCover by viewModel.allSongsCover.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PlaylistWithCount?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.import(uris) { result ->
                Toast.makeText(
                    context,
                    "新增 ${result.added} 首，跳过 ${result.skipped} 首，失败 ${result.failed} 首",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.sm, top = Space.lg, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "音乐库",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (importing) {
                        "正在导入…"
                    } else {
                        "$trackCount 首歌 · ${playlists.size} 个歌单"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = { picker.launch(arrayOf("audio/*")) },
                enabled = !importing,
            ) {
                Text("导入")
            }
            Spacer(Modifier.width(Space.xs))
            TextButton(onClick = { showCreateDialog = true }) {
                Text("新建歌单")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = Space.sm, bottom = Space.lg),
        ) {
            item {
                LibraryRow(
                    coverUri = allSongsCover,
                    title = "全部歌曲",
                    subtitle = "$trackCount 首",
                    onClick = { onOpenPlaylist(ALL_SONGS_ID) },
                )
            }

            if (playlists.isNotEmpty()) {
                item {
                    Text(
                        text = "我的歌单",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = Space.lg,
                            top = Space.s250,
                            bottom = Space.s75,
                        ),
                    )
                }
                items(playlists, key = { it.playlist.id }) { item ->
                    LibraryRow(
                        coverUri = item.coverUri,
                        title = item.playlist.name,
                        subtitle = "${item.trackCount} 首",
                        onClick = { onOpenPlaylist(item.playlist.id) },
                        onDelete = { pendingDelete = item },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 88.dp, end = Space.md),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            } else {
                item {
                    Text(
                        text = "还没有歌单。点右上角「新建歌单」开始整理。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = Space.lg,
                            end = Space.lg,
                            top = Space.s250,
                            bottom = Space.md,
                        ),
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除歌单？") },
            text = { Text("「${item.playlist.name}」会被删除，歌单内的歌曲仍保留在音乐库。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(item.playlist.id)
                        pendingDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun LibraryRow(
    coverUri: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.listRowMin)
            .clickable(onClick = onClick)
            .padding(start = Space.md, end = Space.xs, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtBox(
            coverUri = coverUri,
            size = 56.dp,
            corner = 8.dp,
        )

        Spacer(Modifier.width(Space.md))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除歌单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(Modifier.size(Space.minTouch), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("歌单名称") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
