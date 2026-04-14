package com.lele.llmonitor.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.lele.llmonitor.data.local.AppDatabase
import com.lele.llmonitor.data.local.BatteryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 全局单例仓库：连接 Service (生产者) 与 UI (消费者)
 * 作用：在内存中维护实时数据，确保 UI 能瞬间响应，无视数据库 IO 延迟。
 */
object BatteryRepository {
    private const val SNAPSHOT_FILE_NAME = "battery_history_snapshot.json"
    private const val SNAPSHOT_VERSION = 1
    private const val HISTORY_RETENTION_MS = 48 * 60 * 60 * 1000L
    // 使用 SnapshotStateList，Compose 会自动监听它的变化
    // 保留 2000 条数据用于高频实时绘图，覆盖约 8 小时 (考虑 2-3s/点)
    val latestHistory = mutableStateListOf<BatteryEntity>()
    private var lastEmitTime = 0L
    private var appContext: Context? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null
    private val initialLoadGuard = Any()
    @Volatile
    private var hasInitialHistoryLoaded = false
    private var initialLoadJob: Job? = null
    private const val INITIAL_LOAD_CHUNK_SIZE = 120

    fun isInitialHistoryLoaded(): Boolean = hasInitialHistoryLoaded

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // 冷启动前置预热：异步拉取历史到内存，不阻塞界面。
    fun preloadInitialHistoryAsync(context: Context) {
        init(context)
        synchronized(initialLoadGuard) {
            if (hasInitialHistoryLoaded || initialLoadJob?.isActive == true) return
            initialLoadJob = repositoryScope.launch {
                runInitialHistoryLoad()
            }
        }
    }

    // 需要时等待历史已就绪（在 IO 协程调用，避免主线程等待）。
    suspend fun awaitInitialHistoryLoaded(context: Context) {
        init(context)
        val job = synchronized(initialLoadGuard) {
            if (hasInitialHistoryLoaded) {
                null
            } else {
                if (initialLoadJob?.isActive != true) {
                    initialLoadJob = repositoryScope.launch {
                        runInitialHistoryLoad()
                    }
                }
                initialLoadJob
            }
        }
        job?.join()
    }

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
            val last = latestHistory.lastOrNull()
            // 去重：同一时刻且采样值一致时不重复入列
            if (
                last != null &&
                last.timestamp == entry.timestamp &&
                last.level == entry.level &&
                last.voltage == entry.voltage &&
                last.current == entry.current &&
                last.power == entry.power &&
                last.temperature == entry.temperature
            ) {
                return@withContext
            }
            latestHistory.add(entry)
            trimToCapacityLocked()
            schedulePersistLocked()
        }
    }

    /**
     * 由 ViewModel 调用：初始化加载历史数据
     */
    suspend fun loadInitialData(data: List<BatteryEntity>) {
        val persisted = withContext(Dispatchers.IO) { loadPersistedSnapshot() }
        val currentSnapshot = withContext(Dispatchers.Main) { latestHistory.toList() }
        val merged = withContext(Dispatchers.Default) {
            mergeAndNormalizeEntries(currentSnapshot, data, persisted)
        }
        withContext(Dispatchers.Main) {
            if (merged.isEmpty()) return@withContext
            latestHistory.clear()
            var cursor = 0
            while (cursor < merged.size) {
                val end = minOf(cursor + INITIAL_LOAD_CHUNK_SIZE, merged.size)
                latestHistory.addAll(merged.subList(cursor, end))
                trimToCapacityLocked()
                cursor = end
                // 让出主线程，避免一次性提交大量历史导致首屏掉帧。
                if (cursor < merged.size) {
                    yield()
                }
            }
            trimToCapacityLocked()
            schedulePersistLocked()
        }
    }

    private fun trimToCapacityLocked() {
        while (latestHistory.size > 2000) {
            latestHistory.removeAt(0)
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.Main) {
            latestHistory.clear()
            lastEmitTime = 0L
            persistJob?.cancel()
        }
        withContext(Dispatchers.IO) {
            snapshotFile()?.delete()
        }
    }

    private fun schedulePersistLocked() {
        persistJob?.cancel()
        persistJob = repositoryScope.launch {
            delay(350L)
            persistSnapshot()
        }
    }

    private suspend fun persistSnapshot() {
        val context = appContext ?: return
        val snapshot = withContext(Dispatchers.Main) { latestHistory.toList() }
        if (snapshot.isEmpty()) return

        val json = JSONObject().apply {
            put("v", SNAPSHOT_VERSION)
            put(
                "items",
                JSONArray().apply {
                    snapshot.forEach { entry ->
                        put(
                            JSONObject().apply {
                                put("id", entry.id)
                                put("ts", entry.timestamp)
                                put("lv", entry.level)
                                put("v", entry.voltage.toDouble())
                                put("c", entry.current.toDouble())
                                put("p", entry.power.toDouble())
                                put("t", entry.temperature.toDouble())
                            }
                        )
                    }
                }
            )
        }

        runCatching {
            val tempFile = File(context.filesDir, "$SNAPSHOT_FILE_NAME.tmp")
            val targetFile = File(context.filesDir, SNAPSHOT_FILE_NAME)
            tempFile.writeText(json.toString())
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        }
    }

    private fun loadPersistedSnapshot(): List<BatteryEntity> {
        val raw = runCatching { snapshotFile()?.takeIf { it.exists() }?.readText() }.getOrNull()
            ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val items = root.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    add(
                        BatteryEntity(
                            id = item.optLong("id", 0L),
                            timestamp = item.optLong("ts", 0L),
                            level = item.optInt("lv", 0),
                            voltage = item.optDouble("v", 0.0).toFloat(),
                            current = item.optDouble("c", 0.0).toFloat(),
                            power = item.optDouble("p", 0.0).toFloat(),
                            temperature = item.optDouble("t", 0.0).toFloat()
                        )
                    )
                }
            }.filter { it.timestamp > 0L }
        }.getOrElse { emptyList() }
    }

    private fun snapshotFile(): File? {
        val context = appContext ?: return null
        return File(context.filesDir, SNAPSHOT_FILE_NAME)
    }

    private fun mergeAndNormalizeEntries(vararg sources: List<BatteryEntity>): List<BatteryEntity> {
        return sources.asSequence()
            .flatten()
            .sortedBy { it.timestamp }
            .distinctBy {
                listOf(
                    it.timestamp,
                    it.level,
                    it.voltage,
                    it.current,
                    it.power,
                    it.temperature
                )
            }
            .toList()
    }

    private suspend fun runInitialHistoryLoad() {
        try {
            val context = appContext ?: return
            val since = System.currentTimeMillis() - HISTORY_RETENTION_MS
            val dbHistory = runCatching {
                AppDatabase.getInstance(context).batteryDao().getStaticHistory(since)
            }.getOrElse { emptyList() }
            loadInitialData(dbHistory)
            synchronized(initialLoadGuard) {
                hasInitialHistoryLoaded = true
                initialLoadJob = null
            }
        } catch (_: Exception) {
            synchronized(initialLoadGuard) {
                initialLoadJob = null
            }
        }
    }
}
