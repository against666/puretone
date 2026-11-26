package com.puretone.player.data.model

/**
 * 下载任务状态机。
 *
 * PENDING → PROBING（探测真实时长）→ DOWNLOADING → DONE
 * 任一环节失败进入 FAILED，可由用户重试。
 */
enum class TaskState {
    PENDING,
    PROBING,
    DOWNLOADING,
    DONE,
    FAILED,
}
