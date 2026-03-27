package com.lele.llmonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Insert
    suspend fun insert(entry: BatteryEntity)

    // (可选) 如果你还想保留 Flow 监听，可以留着这个方法，但新架构主要用下面的 static 方法
    @Query("SELECT * FROM battery_history WHERE timestamp > :since ORDER BY timestamp ASC")
    fun getRecentHistory(since: Long): Flow<List<BatteryEntity>>

    /**
     * 【核心新增】获取静态历史数据 (非 Flow)
     * 作用：App 启动时一次性调用，将数据库中的旧数据加载到 Repository 内存中。
     * 之后的新数据完全靠 Service 推送，不再轮询数据库，从而实现低功耗。
     */
    @Query("SELECT * FROM battery_history WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getStaticHistory(since: Long): List<BatteryEntity>

    @Query("DELETE FROM battery_history WHERE id IN (SELECT id FROM battery_history WHERE timestamp < :before LIMIT 100)")
    suspend fun clearOldData(before: Long)

    @Query("DELETE FROM battery_history")
    suspend fun deleteAll()

}