package com.puretone.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.puretone.player.data.model.SourceType
import com.puretone.player.data.model.TaskState

/**
 * 下载队列。写入后由前台服务消费，App 重启后仍能续上未完成的任务。
 */
@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: SourceType,
    val sourceId: String,
    val title: String,
    val artist: String,
    val state: TaskState,
    /** 0..100 */
    val progress: Int = 0,
    val errorMsg: String? = null,
    val createdAt: Long,
)
