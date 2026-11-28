package com.puretone.player.data.file

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * 本地音频导入：读取元数据 → 复制进公共音乐目录。
 *
 * 走 SAF 选择文件，因此**不需要申请任何存储权限**；写入时通过 MediaStore 落到
 * `Music/PureTone/`，卸载 App 文件仍然保留，其他播放器也能看到。
 */
class LocalImporter(private val context: Context) {

    data class LocalAudioMeta(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val displayName: String,
        val mimeType: String,
    )

    fun readMeta(uri: Uri): LocalAudioMeta {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri) ?: "audio_${System.currentTimeMillis()}.mp3"
        val fallbackTitle = displayName.substringBeforeLast('.')

        var title = ""
        var artist = ""
        var album = ""
        var durationMs = 0L

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }

        return LocalAudioMeta(
            title = title.ifBlank { fallbackTitle },
            artist = artist.ifBlank { "未知艺术家" },
            album = album,
            durationMs = durationMs,
            displayName = displayName,
            mimeType = resolver.getType(uri) ?: "audio/mpeg",
        )
    }

    /**
     * 复制到 `Music/PureTone/`。返回新建的 MediaStore 地址，失败返回 null。
     */
    fun copyToLibrary(uri: Uri, meta: LocalAudioMeta): Uri? {
        val resolver = context.contentResolver
        val extension = meta.displayName.substringAfterLast('.', "mp3")

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${sanitize(meta.artist)} - ${sanitize(meta.title)}.$extension")
            put(MediaStore.Audio.Media.MIME_TYPE, meta.mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.TITLE, meta.title)
            put(MediaStore.Audio.Media.ARTIST, meta.artist)
            if (meta.album.isNotBlank()) {
                put(MediaStore.Audio.Media.ALBUM, meta.album)
            }
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null

        var copied = false
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                resolver.openOutputStream(target)?.use { output ->
                    input.copyTo(output)
                    copied = true
                }
            }
        }

        if (!copied) {
            runCatching { resolver.delete(target, null, null) }
            return null
        }
        return target
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    private fun sanitize(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(80)

    private companion object {
        const val RELATIVE_PATH = "Music/PureTone"
    }
}
