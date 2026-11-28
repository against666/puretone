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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.puretone.player.ui.theme.Space
import com.puretone.player.PureToneApplication
import com.puretone.player.data.db.Track
import com.puretone.player.ui.components.formatDuration

@Composable
fun AddSongsScreen(
    playlistId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as PureToneApplication
    val viewModel: AddSongsViewModel = viewModel(
        key = "add-$playlistId",
        factory = AddSongsViewModel.factory(app, playlistId),
    )

    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val sourceId by viewModel.sourceId.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val existing by viewModel.existing.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var query by remember { mutableStateOf("") }

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
                .padding(start = Space.xs, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("取消") }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "添加歌曲",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "已选 ${selected.size} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    viewModel.add(selected.toList()) { onBack() }
                },
                enabled = selected.isNotEmpty(),
            ) { Text("添加") }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = sourceId == 0L,
                    onClick = { viewModel.selectSource(0L) },
                    label = { Text("全部歌曲") },
                )
            }
            items(sources, key = { it.playlist.id }) { item ->
                FilterChip(
                    selected = sourceId == item.playlist.id,
                    onClick = { viewModel.selectSource(item.playlist.id) },
                    label = { Text(item.playlist.name) },
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = 4.dp),
            singleLine = true,
            placeholder = { Text("搜索歌曲") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Space.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (tracks.isEmpty()) "这个来源还没有歌曲。" else "没有匹配的歌曲。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(filtered, key = { _, track -> track.id }) { index, track ->
                    val already = track.id in existing
                    val checked = already || track.id in selected
                    SelectableTrackRow(
                        track = track,
                        checked = checked,
                        enabled = !already,
                        onClick = {
                            selected = if (track.id in selected) {
                                selected - track.id
                            } else {
                                selected + track.id
                            }
                        },
                    )
                    if (index != filtered.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableTrackRow(
    track: Track,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = Space.sm, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = track.title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(Space.s150))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = if (enabled) {
                    "${track.artist} · ${formatDuration(track.durationMs)}"
                } else {
                    "已在歌单中"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
