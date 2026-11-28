package com.puretone.player.data.repo

import android.content.Context
import android.net.Uri
import com.puretone.player.data.db.Track
import com.puretone.player.data.db.TrackDao
import com.puretone.player.data.file.LocalImporter
import com.puretone.player.data.model.LyricState
import com.puretone.player.data.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val importer: LocalImporter,
) {

    data class ImportResult(
        val added: Int,
        val skipped: Int,
        val failed: Int,
    )

    fun observeTracks(): Flow<List<Track>> = trackDao.observeAll()

    fun observeTrack(id: Long): Flow<Track?> = trackDao.observeById(id)

    fun observeEarliestCover(): Flow<String?> = trackDao.observeEarliestCover()

    /**
     * 导入顺序很重要：先读元数据判断是否重复，**再决定要不要复制文件**。
     * 反过来的话，重复导入会在 Music/PureTone 里留下一堆孤儿文件。
     */
    suspend fun importLocal(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var added = 0
        var skipped = 0
        var failed = 0

        uris.forEach { uri ->
            val meta = runCatching { importer.readMeta(uri) }.getOrNull()
            if (meta == null) {
                failed++
                return@forEach
            }

            if (trackDao.findLocalDuplicate(meta.title, meta.artist, meta.durationMs) != null) {
                skipped++
                return@forEach
            }

            val target = importer.copyToLibrary(uri, meta)
            if (target == null) {
                failed++
                return@forEach
            }

            val rowId = trackDao.insert(
                Track(
                    sourceType = SourceType.LOCAL,
                    sourceId = null,
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    durationMs = meta.durationMs,
                    fileUri = target.toString(),
                    lyricState = LyricState.LOCAL,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (rowId == -1L) skipped++ else added++
        }

        ImportResult(added = added, skipped = skipped, failed = failed)
    }

    /** 删除记录，同时清掉 Music/PureTone 下的文件 */
    suspend fun delete(track: Track) = withContext(Dispatchers.IO) {
        trackDao.deleteById(track.id)
        runCatching {
            val uri = Uri.parse(track.fileUri)
            if (uri.scheme == "content") {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }
}
