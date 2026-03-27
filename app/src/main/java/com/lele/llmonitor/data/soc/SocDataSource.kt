package com.lele.llmonitor.data.soc

interface SocDataSource {
    suspend fun probeCpuUsage(previous: CpuTimes?): CpuUsageResult
    fun readOnlineAndTotalCores(): Pair<Int, Int>
    suspend fun probeLoadAverages(): LoadAvgResult
    suspend fun probeSocTemperatureC(): TemperatureResult
    fun probeMemoryInfo(): MemoryInfoResult
    fun probeCpuModel(): CpuModelResult
    fun readCpuCoreFrequencies(): List<CpuCoreFrequencySample>
}
