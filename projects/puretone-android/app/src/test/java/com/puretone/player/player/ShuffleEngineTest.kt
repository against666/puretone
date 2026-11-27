package com.puretone.player.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleEngineTest {

    private val ids = listOf(1L, 2L, 3L, 4L, 5L)

    @Test
    fun `初始权重全部相等`() {
        val engine = ShuffleEngine().apply { reset(ids) }
        ids.forEach { assertEquals(1.0, engine.weightOf(it), 0.0) }
    }

    @Test
    fun `不会选中正在播放的那首`() {
        val engine = ShuffleEngine().apply { reset(ids) }
        val random = Random(42)
        repeat(200) {
            val picked = engine.pick(excludeId = 3L, random = random)
            assertNotEquals(3L, picked)
        }
    }

    @Test
    fun `单曲队列没有可随机目标`() {
        val engine = ShuffleEngine().apply { reset(listOf(9L)) }
        assertNull(engine.pick(excludeId = 9L))
    }

    @Test
    fun `被选中的权重归一其余递增`() {
        val engine = ShuffleEngine().apply { reset(ids) }
        engine.onPicked(2L)

        assertEquals(1.0, engine.weightOf(2L), 0.0)
        ids.filter { it != 2L }.forEach {
            assertEquals(2.0, engine.weightOf(it), 0.0)
        }

        // 再选一次别人，剩下的继续涨
        engine.onPicked(4L)
        assertEquals(3.0, engine.weightOf(1L), 0.0)
        assertEquals(3.0, engine.weightOf(3L), 0.0)
        assertEquals(3.0, engine.weightOf(5L), 0.0)
        assertEquals(2.0, engine.weightOf(2L), 0.0)
        assertEquals(1.0, engine.weightOf(4L), 0.0)
    }

    @Test
    fun `长期播放大体公平`() {
        val engine = ShuffleEngine().apply { reset(ids) }
        val random = Random(7)
        val counts = mutableMapOf<Long, Int>()

        var current = 1L
        repeat(500) {
            val picked = engine.pick(excludeId = current, random = random)!!
            engine.onPicked(picked)
            counts.merge(picked, 1, Int::plus)
            current = picked
        }

        // 5 首歌各应被选 80-130 次（理想均值 100），验证没有谁被饿死
        ids.forEach { id ->
            val n = counts[id] ?: 0
            assertTrue("歌曲 $id 只被选了 $n 次", n in 60..140)
        }
    }

    @Test
    fun `权重高的歌明显更容易被选中`() {
        val engine = ShuffleEngine().apply { reset(listOf(1L, 2L)) }
        // 把 2 的权重抬到 9:1
        repeat(8) { engine.onPicked(1L) } // 1 归 1，2 涨到 9
        assertEquals(9.0, engine.weightOf(2L), 0.0)

        val random = Random(11)
        var picked2 = 0
        repeat(100) {
            // 当前播的是 1，随机下一首只剩 2 —— 排除机制生效
            engine.pick(excludeId = 1L, random = random)?.let { if (it == 2L) picked2++ }
        }
        assertEquals(100, picked2)
    }
}
