package com.lele.llmonitor.data.soc

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

class SocRepository(
    private val dataSource: SocDataSource = LinuxSocDataSource()
) {
    companion object {
        private const val CORE_FREQUENCY_REFRESH_INTERVAL_MS = 2500L
        private const val CPU_MODEL_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
    }

    fun stream(intervalMs: Long = 1500L): Flow<SocSnapshot> = flow {
        var previousTimes: CpuTimes? = null
        var lastEmittedSnapshot: SocSnapshot? = null
        var cachedCoreFrequencies: List<CpuCoreFrequencySample> = emptyList()
        var lastCoreFrequencyReadMs = 0L
        var cachedCpuModelProbe = CpuModelResult(modelName = null, probes = emptyList())
        var lastCpuModelReadMs = 0L

        while (currentCoroutineContext().isActive) {
            val nowElapsed = SystemClock.elapsedRealtime()
            val cpuProbe = dataSource.probeCpuUsage(previousTimes)
            previousTimes = cpuProbe.nextCpuTimes ?: previousTimes
            val (online, total) = dataSource.readOnlineAndTotalCores()
            val loadProbe = dataSource.probeLoadAverages()
            val tempProbe = dataSource.probeSocTemperatureC()
            val memoryProbe = dataSource.probeMemoryInfo()
            if (
                cachedCoreFrequencies.isEmpty() ||
                nowElapsed - lastCoreFrequencyReadMs >= CORE_FREQUENCY_REFRESH_INTERVAL_MS
            ) {
                cachedCoreFrequencies = dataSource.readCpuCoreFrequencies()
                lastCoreFrequencyReadMs = nowElapsed
            }
            if (
                cachedCpuModelProbe.modelName.isNullOrBlank() ||
                nowElapsed - lastCpuModelReadMs >= CPU_MODEL_REFRESH_INTERVAL_MS
            ) {
                cachedCpuModelProbe = dataSource.probeCpuModel()
                lastCpuModelReadMs = nowElapsed
            }

            val snapshot = SocSnapshot(
                timestamp = System.currentTimeMillis(),
                cpuUsagePercent = cpuProbe.usagePercent,
                onlineCores = online,
                totalCores = total,
                loadAvg1 = loadProbe.loadAvg1,
                loadAvg5 = loadProbe.loadAvg5,
                loadAvg15 = loadProbe.loadAvg15,
                socTemperatureC = tempProbe.temperatureC,
                socTemperatureFractionDigits = tempProbe.temperatureFractionDigits,
                socTemperatureSource = tempProbe.selectedZone,
                cpuUsageSources = cpuProbe.probes,
                loadAvgSources = loadProbe.probes,
                socTemperatureSources = tempProbe.probes,
                memoryTotalBytes = memoryProbe.totalBytes,
                memoryAvailableBytes = memoryProbe.availableBytes,
                memorySources = memoryProbe.probes,
                cpuModelName = cachedCpuModelProbe.modelName,
                cpuModelSources = cachedCpuModelProbe.probes,
                thermalZones = tempProbe.zones,
                coreFrequencies = cachedCoreFrequencies
            )
            val shouldEmit = lastEmittedSnapshot
                ?.copy(timestamp = snapshot.timestamp) != snapshot
            if (shouldEmit) {
                emit(snapshot)
                lastEmittedSnapshot = snapshot
            }

            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)
}
