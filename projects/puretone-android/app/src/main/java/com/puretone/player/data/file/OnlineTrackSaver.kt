package com.puretone.player.data.file

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.net.URL

/**
 * 在线歌曲落盘：
 * - MP3 走 MediaStore，进 `Music/PureTone/`，和本地导入的文件待遇一致
 * - 封面存应用私有目录（封面是素材不是音乐，用户不需要在文件管理器里看到它）
 */
class OnlineTrackSaver(private val context: Context) {

    /** 下载 MP3 到公共音乐目录。失败时自动清掉半成品，返回 null */
    fun saveAudio(url: String, title: String, artist: String, album: String): Uri? {
        val resolver = context.contentResolver
        val name = "${sanitize(artist)} - ${sanitize(title)}.mp3"

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            if (album.isNotBlank()) {
                put(MediaStore.Audio.Media.ALBUM, album)
            }
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null

        val copied = runCatching {
            resolver.openOutputStream(target)?.use { output ->
                URL(url).openStream().use { input -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)

        if (!copied) {
            runCatching { resolver.delete(target, null, null) }
            return null
        }

        // 写完解除 pending，其他 App 才能看到
        resolver.update(
            target,
            ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
            null,
            null,
        )
        return target
    }

    /** 下载封面到私有目录，返回绝对路径。封面失败不阻断入库，返回 null 即可 */
    fun saveCover(url: String, cacheKey: String): String? {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "${sanitize(cacheKey)}.jpg")

        return runCatching {
            URL(url).openStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.length() > 0) file.absolutePath else null
        }.getOrNull()
    }

    /** 从已入库的文件读时长 */
    fun readDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun sanitize(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(80)

    private companion object {
        const val RELATIVE_PATH = "Music/PureTone"
    }
}
