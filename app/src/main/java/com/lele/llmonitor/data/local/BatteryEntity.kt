package com.lele.llmonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// 表名确认为 "battery_history"，与 Dao 中的查询语句保持一致
@Entity(
    tableName = "battery_history",
    indices = [androidx.room.Index(value = ["timestamp"])]
)
data class BatteryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: Int,
    val voltage: Float,
    val current: Float,
    val power: Float,
    val temperature: Float = 0f  // 电池温度 (°C)
)