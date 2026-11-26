package com.puretone.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.puretone.player.data.model.SourceType
import com.puretone.player.data.model.TaskState
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTask>>

    /** 未完成的任务，按创建顺序消费 */
    @Query("SELECT * FROM download_tasks WHERE state IN ('PENDING', 'PROBING', 'DOWNLOADING') ORDER BY createdAt ASC")
    suspend fun pendingTasks(): List<DownloadTask>

    @Query("SELECT * FROM download_tasks WHERE sourceType = :type AND sourceId = :sourceId LIMIT 1")
    suspend fun findBySource(type: SourceType, sourceId: String): DownloadTask?

    @Insert
    suspend fun insert(task: DownloadTask): Long

    @Update
    suspend fun update(task: DownloadTask)

    @Query("UPDATE download_tasks SET state = :state, progress = :progress, errorMsg = :error WHERE id = :id")
    suspend fun updateState(id: Long, state: TaskState, progress: Int, error: String?)

    @Query("DELETE FROM download_tasks WHERE state = 'DONE'")
    suspend fun clearFinished()

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
