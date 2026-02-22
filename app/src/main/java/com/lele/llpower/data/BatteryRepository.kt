package com.lele.llpower.data

import androidx.compose.runtime.mutableStateListOf
import com.lele.llpower.data.local.BatteryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全局单例仓库：连接 Service (生产者) 与 UI (消费者)
 * 作用：在内存中维护实时数据，确保 UI 能瞬间响应，无视数据库 IO 延迟。
 */
object BatteryRepository {
    // 使用 SnapshotStateList，Compose 会自动监听它的变化
    // 保留 2000 条数据用于高频实时绘图，覆盖约 8 小时 (考虑 2-3s/点)
    val latestHistory = mutableStateListOf<BatteryEntity>()
    private var lastEmitTime = 0L

    /**
     * 由 Service 调用：推送新数据
     */
    suspend fun emitNewEntry(entry: BatteryEntity) {
        val now = System.currentTimeMillis()
        // 增加 2 秒防抖，避免极短时间内的重复点导致过度重绘
        if (now - lastEmitTime < 2000L) return
        lastEmitTime = now

        // 必须切换到主线程来更新 Compose 的 StateList
        withContext(Dispatchers.Main) {
            latestHistory.add(entry)
            // 历史点管理，保持 8 小时左右的跨度 (约 2000 点)
            if (latestHistory.size > 2000) {
                latestHistory.removeAt(0)
            }
        }
    }

    /**
     * 由 ViewModel 调用：初始化加载历史数据
     */
    suspend fun loadInitialData(data: List<BatteryEntity>) {
        withContext(Dispatchers.Main) {
            if (latestHistory.isEmpty()) {
                latestHistory.addAll(data)
            }
        }
    }
}