package com.puretone.player.data.db

import androidx.room.TypeConverter
import com.puretone.player.data.model.LyricState
import com.puretone.player.data.model.SourceType
import com.puretone.player.data.model.TaskState

/**
 * 枚举一律以字符串入库，不用序号——序号会随枚举顺序调整而错位。
 * 音源存 code（wy/qq/kg/kw/local），与 API 参数一致，方便直接复用。
 */
class Converters {

    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.code

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.fromCode(value)

    @TypeConverter
    fun fromLyricState(value: LyricState): String = value.name

    @TypeConverter
    fun toLyricState(value: String): LyricState =
        runCatching { LyricState.valueOf(value) }.getOrDefault(LyricState.MISSING)

    @TypeConverter
    fun fromTaskState(value: TaskState): String = value.name

    @TypeConverter
    fun toTaskState(value: String): TaskState =
        runCatching { TaskState.valueOf(value) }.getOrDefault(TaskState.FAILED)
}
