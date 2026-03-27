package com.lele.llmonitor.data.soc

data class CpuTimes(
    val total: Long,
    val idle: Long
)

data class SourceProbe(
    val source: String,
    val value: String,
    val success: Boolean
)

data class CpuCoreFrequencySample(
    val coreId: Int,
    val online: Boolean,
    val policy: String?,
    val currentMHz: Int?,
    val maxMHz: Int?
)

data class ThermalZoneReading(
    val zone: String,
    val type: String,
    val tempC: Float,
    val tempFractionDigits: Int
)

data class CpuUsageResult(
    val usagePercent: Float?,
    val nextCpuTimes: CpuTimes?,
    val probes: List<SourceProbe>
)

data class LoadAvgResult(
    val loadAvg1: Float?,
    val loadAvg5: Float?,
    val loadAvg15: Float?,
    val probes: List<SourceProbe>
)

data class TemperatureResult(
    val temperatureC: Float?,
    val temperatureFractionDigits: Int?,
    val selectedZone: String?,
    val probes: List<SourceProbe>,
    val zones: List<ThermalZoneReading>
)

data class MemoryInfoResult(
    val totalBytes: Long?,
    val availableBytes: Long?,
    val probes: List<SourceProbe>
)

data class CpuModelResult(
    val modelName: String?,
    val probes: List<SourceProbe>
)

data class SocSnapshot(
    val timestamp: Long,
    val cpuUsagePercent: Float?,
    val onlineCores: Int,
    val totalCores: Int,
    val loadAvg1: Float?,
    val loadAvg5: Float?,
    val loadAvg15: Float?,
    val socTemperatureC: Float?,
    val socTemperatureFractionDigits: Int?,
    val socTemperatureSource: String?,
    val cpuUsageSources: List<SourceProbe>,
    val loadAvgSources: List<SourceProbe>,
    val socTemperatureSources: List<SourceProbe>,
    val memoryTotalBytes: Long?,
    val memoryAvailableBytes: Long?,
    val memorySources: List<SourceProbe>,
    val cpuModelName: String?,
    val cpuModelSources: List<SourceProbe>,
    val thermalZones: List<ThermalZoneReading>,
    val coreFrequencies: List<CpuCoreFrequencySample>
)
