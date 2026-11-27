package com.puretone.player.player

import kotlin.random.Random

/**
 * 加权随机引擎——随机播放的核心。
 *
 * 规则（用户定义）：
 * 1. 队列里每首歌初始权重相等
 * 2. 每次随机选中一首歌，它的权重重置回 1，其余全部 +1
 * 3. 于是「一直没被随机到」的歌权重越来越高，被抽中的概率越来越大，
 *    刚听过的歌则最不容易马上再来一遍
 *
 * 独立成纯 Kotlin 类是为了能跑 JVM 单元测试，不依赖播放器环境。
 */
class ShuffleEngine {

    private val weights = LinkedHashMap<Long, Double>()

    /** 播放队列变化时重置：所有歌回到平等起点 */
    fun reset(trackIds: List<Long>) {
        weights.clear()
        trackIds.forEach { weights[it] = 1.0 }
    }

    /**
     * 按权重随机挑一首。[excludeId]（当前正在播的歌）不参与，避免「随机」变成原地踏步。
     * 队列只有一首或为空时返回 null。
     */
    fun pick(excludeId: Long, random: Random = Random.Default): Long? {
        val candidates = weights.filterKeys { it != excludeId }
        if (candidates.isEmpty()) return null

        val total = candidates.values.sum()
        if (total <= 0.0) return candidates.keys.random(random)

        var roll = random.nextDouble() * total
        for ((id, weight) in candidates) {
            roll -= weight
            if (roll <= 0.0) return id
        }
        return candidates.keys.last()
    }

    /** 选走一首：它的使命完成，权重归 1；没被选中的每首都攒一分 */
    fun onPicked(id: Long) {
        weights.keys.forEach { key ->
            weights[key] = if (key == id) 1.0 else (weights[key] ?: 1.0) + 1.0
        }
    }

    fun weightOf(id: Long): Double = weights[id] ?: 1.0
}
