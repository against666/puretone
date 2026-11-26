package com.puretone.player.lyric

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * LRC 解析。
 *
 * 支持的形式：
 * - 一行一个时间戳：`[00:12.34]歌词`
 * - 一行多个时间戳（副歌复用）：`[00:12.34][01:05.20]歌词`
 * - 1/2/3 位小数：[00:12.3] = 300ms，[00:12.34] = 340ms，[00:12.345] = 345ms
 * - 头部元信息 `[ti:][ar:][al:]` 直接丢弃
 * - `[offset:500]` 整体偏移，正值表示歌词延后
 */
object LrcParser {

    private val TIME_TAG = Regex("""^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val META_TAG = Regex("""^\[(ti|ar|al|by|offset)\s*:(.*)]""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, userOffsetMs: Int = 0): List<LyricLine> {
        if (raw.isBlank()) return emptyList()

        var fileOffset = 0
        val result = ArrayList<LyricLine>(64)

        raw.lineSequence().forEach { sourceLine ->
            val line = sourceLine.trimEnd()

            val meta = META_TAG.find(line)
            if (meta != null) {
                if (meta.groupValues[1].equals("offset", ignoreCase = true)) {
                    fileOffset = meta.groupValues[2].trim().toIntOrNull() ?: 0
                }
                return@forEach
            }

            var rest = line
            val stamps = ArrayList<Long>(2)
            while (true) {
                val match = TIME_TAG.find(rest) ?: break
                stamps += toMillis(
                    minutes = match.groupValues[1],
                    seconds = match.groupValues[2],
                    fraction = match.groupValues[3],
                )
                rest = rest.substring(match.range.last + 1)
            }

            val text = rest.trim()
            if (stamps.isEmpty() || text.isEmpty()) return@forEach

            stamps.forEach { stamp ->
                result += LyricLine(
                    timeMs = (stamp + fileOffset + userOffsetMs).coerceAtLeast(0L),
                    text = text,
                )
            }
        }

        result.sortBy { it.timeMs }
        return result
    }

    private fun toMillis(minutes: String, seconds: String, fraction: String): Long {
        var ms = minutes.toLong() * 60_000L + seconds.toLong() * 1_000L
        ms += when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.take(3).toLong()
        }
        return ms
    }
}

/**
 * 二分查找当前行：最后一个 time <= position 的行。
 * 返回 -1 表示还没到第一句。
 */
fun List<LyricLine>.indexAt(positionMs: Long): Int {
    if (isEmpty()) return -1
    var low = 0
    var high = lastIndex
    var answer = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].timeMs <= positionMs) {
            answer = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return answer
}
