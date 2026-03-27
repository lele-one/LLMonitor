package com.lele.llmonitor.data

data class BatterySourceProbe(
    val source: String,
    val value: String,
    val success: Boolean
)

data class BatteryProbeResult<T>(
    val value: T,
    val probes: List<BatterySourceProbe>
)
