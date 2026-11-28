package com.puretone.player.data.online

import android.util.Log
import com.puretone.player.BuildConfig
import com.puretone.player.data.model.SourceType
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** 一条搜索结果。API 不返回时长，时长要在下载后从文件里读 */
data class OnlineSong(
    val source: SourceType,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
) {
    /** 用于去重和下载状态标记 */
    val key: String get() = "${source.code}:$songId"
}

/** 一首歌的完整信息：MP3 / 封面 / 歌词，都是字符串 */
data class OnlineSongDetail(
    val song: OnlineSong,
    val mp3Url: String?,
    val picUrl: String?,
    val lrc: String?,
)

/** 接口返回 code=0 时抛出，msg 是服务端给的原因（额度不足、id 无效等） */
class OnlineApiException(message: String) : Exception(message)

/**
 * 明月浩空开放 API 客户端。
 *
 * 文档：https://s.myhkw.cn（开放API接口）
 * 统一返回 {code, msg, data}，code=1 成功。
 * `info` 带 url=1&pic=1&lrc=1 时一次拿全 MP3/封面/歌词，日常下载只调这一个。
 *
 * 密钥每次请求时从 [keyProvider] 现取：远程下发的 key 变了不用重启 App。
 */
class OnlineMusicApi(
    private val keyProvider: () -> String,
) {

    fun search(source: SourceType, keyword: String, limit: Int): List<OnlineSong> {
        require(source != SourceType.LOCAL) { "本地音源不走联网搜索" }

        val body = request(
            "search",
            mapOf(
                "name" to keyword,
                "type" to source.code,
                "limit" to limit.toString(),
                "format" to "1",
            ),
        )
        // 失败时 data 是字符串而不是数组，只看 data 形状会把「密钥错误」误判成「没搜到」
        if (body.optInt("code", 0) != 1) {
            throw OnlineApiException(body.optString("msg", "接口返回失败"))
        }
        val data = body.opt("data") ?: return emptyList()
        val list = when (data) {
            is org.json.JSONArray -> data
            else -> return emptyList()
        }
        return (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            OnlineSong(
                source = source,
                songId = id,
                title = item.optString("name"),
                artist = item.optString("artist"),
                album = item.optString("album"),
            )
        }
    }

    fun info(source: SourceType, songId: String): OnlineSongDetail {
        val body = request(
            "info",
            mapOf(
                "id" to songId,
                "type" to source.code,
                "url" to "1",
                "pic" to "1",
                "lrc" to "1",
            ),
        )
        val data = body.optJSONObject("data")
            ?: throw OnlineApiException(body.optString("msg", "接口未返回歌曲信息"))

        return OnlineSongDetail(
            song = OnlineSong(
                source = source,
                songId = songId,
                title = data.optString("name"),
                artist = data.optString("artist"),
                album = data.optString("album"),
            ),
            mp3Url = data.optString("url").takeIf { it.isNotBlank() && it != "null" },
            picUrl = data.optString("pic").takeIf { it.isNotBlank() && it != "null" },
            lrc = data.optString("lrc").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun request(path: String, params: Map<String, String>): JSONObject {
        val key = keyProvider()
        if (key.isBlank()) throw OnlineApiException("未配置 API 密钥")

        val query = StringBuilder()
        params.forEach { (name, value) ->
            if (query.isNotEmpty()) query.append('&')
            query.append(name).append('=').append(URLEncoder.encode(value, "UTF-8"))
        }
        query.append("&key=").append(URLEncoder.encode(key, "UTF-8"))

        val connection = (URL("$BASE$path?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (body.isBlank()) throw OnlineApiException("HTTP $code，接口无返回")
            JSONObject(body)
        } catch (e: OnlineApiException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "请求 $path 失败", e)
            throw OnlineApiException("网络请求失败：${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "OnlineMusicApi"
        const val BASE = "https://myhkw.cn/open/music/"
    }
}
