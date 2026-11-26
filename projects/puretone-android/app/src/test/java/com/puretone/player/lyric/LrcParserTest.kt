package com.puretone.player.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `解析基础时间戳`() {
        val lines = LrcParser.parse(
            """
            [00:01.00]第一句
            [00:12.34]第二句
            """.trimIndent(),
        )
        assertEquals(2, lines.size)
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals("第一句", lines[0].text)
        assertEquals(12_340L, lines[1].timeMs)
    }

    @Test
    fun `一行多个时间戳会展开成多行`() {
        val lines = LrcParser.parse("[00:10.00][01:20.00]副歌")
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].timeMs)
        assertEquals(80_000L, lines[1].timeMs)
        assertTrue(lines.all { it.text == "副歌" })
    }

    @Test
    fun `小数位按位数换算`() {
        val lines = LrcParser.parse(
            """
            [00:01.5]十分之一
            [00:02.25]百分之一
            [00:03.125]毫秒
            """.trimIndent(),
        )
        assertEquals(1_500L, lines[0].timeMs)
        assertEquals(2_250L, lines[1].timeMs)
        assertEquals(3_125L, lines[2].timeMs)
    }

    @Test
    fun `元信息被丢弃且 offset 生效`() {
        val lines = LrcParser.parse(
            """
            [ti:测试]
            [ar:歌手]
            [offset:500]
            [00:01.00]延后五百毫秒
            """.trimIndent(),
        )
        assertEquals(1, lines.size)
        assertEquals(1_500L, lines[0].timeMs)
    }

    @Test
    fun `用户偏移与文件偏移叠加`() {
        val lines = LrcParser.parse("[offset:200]\n[00:01.00]词", userOffsetMs = -300)
        assertEquals(900L, lines.first().timeMs)
    }

    @Test
    fun `空行与无文字的标签行被忽略`() {
        val lines = LrcParser.parse(
            """
            [00:00.00]
            [00:01.00]有词

            """.trimIndent(),
        )
        assertEquals(1, lines.size)
    }

    @Test
    fun `乱序输入会按时间排序`() {
        val lines = LrcParser.parse(
            """
            [00:20.00]后
            [00:10.00]前
            """.trimIndent(),
        )
        assertEquals(listOf(10_000L, 20_000L), lines.map { it.timeMs })
    }

    @Test
    fun `负偏移不会产生负数时间`() {
        val lines = LrcParser.parse("[00:00.50]开头", userOffsetMs = -2000)
        assertEquals(0L, lines.first().timeMs)
    }

    @Test
    fun `indexAt 定位当前行`() {
        val lines = LrcParser.parse(
            """
            [00:01.00]一
            [00:05.00]二
            [00:09.00]三
            """.trimIndent(),
        )
        assertEquals(-1, lines.indexAt(500))
        assertEquals(0, lines.indexAt(1_000))
        assertEquals(0, lines.indexAt(4_999))
        assertEquals(1, lines.indexAt(5_000))
        assertEquals(2, lines.indexAt(100_000))
    }

    @Test
    fun `空歌词返回空列表`() {
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse("   ").isEmpty())
    }
}
