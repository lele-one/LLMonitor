package com.lele.llmonitor.data.soc

import android.os.Build
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

class LinuxSocDataSource : SocDataSource {
    private val cpuDirs by lazy(LazyThreadSafetyMode.NONE) {
        val cpuRoot = File("/sys/devices/system/cpu")
        cpuRoot.listFiles { file ->
            file.isDirectory && file.name.matches(Regex("cpu\\d+"))
        }?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }.orEmpty()
    }

    @Volatile
    private var cachedCpuModelResult: CpuModelResult? = null

    override suspend fun probeCpuUsage(previous: CpuTimes?): CpuUsageResult {
        val probes = mutableListOf<SourceProbe>()
        var nextCpuTimes: CpuTimes? = previous

        val current = readCpuTimesFromProcStat()
        if (current != null) {
            nextCpuTimes = current
            val usage = calculateCpuUsagePercent(previous, current)
            probes += SourceProbe(
                source = "proc/stat(delta)",
                value = usage?.let { formatPercent(it) } ?: "delta unavailable",
                success = usage != null
            )
            if (usage != null) return CpuUsageResult(usage, nextCpuTimes, probes)
        } else {
            probes += SourceProbe("proc/stat(delta)", "read failed", false)
        }

        val shortUsage = sampleCpuUsageShortWindow()
        probes += SourceProbe(
            source = "proc/stat(short-window)",
            value = shortUsage?.let { formatPercent(it) } ?: "read failed",
            success = shortUsage != null
        )
        if (shortUsage != null) return CpuUsageResult(shortUsage, nextCpuTimes, probes)

        val freqEstimatedUsage = estimateCpuUsageFromFrequencies()
        probes += SourceProbe(
            source = "cpufreq(estimated)",
            value = freqEstimatedUsage?.let { formatPercent(it) } ?: "read failed",
            success = freqEstimatedUsage != null
        )
        if (freqEstimatedUsage != null) return CpuUsageResult(freqEstimatedUsage, nextCpuTimes, probes)

        probes += SourceProbe(
            source = "trusted-sources-only",
            value = "no reliable system cpu source",
            success = false
        )
        return CpuUsageResult(null, nextCpuTimes, probes)
    }

    override fun readOnlineAndTotalCores(): Pair<Int, Int> {
        if (cpuDirs.isEmpty()) {
            val fallback = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            return fallback to fallback
        }

        val total = cpuDirs.size
        val online = cpuDirs.count { cpuDir ->
            val onlineFile = File(cpuDir, "online")
            val raw = runCatching { onlineFile.readText().trim() }.getOrNull()
            raw?.toIntOrNull()?.let { it == 1 } ?: true
        }

        return online.coerceAtLeast(1) to total
    }

    override suspend fun probeLoadAverages(): LoadAvgResult {
        val probes = mutableListOf<SourceProbe>()
        val procLoad = readLoadAvgFromProc()
        probes += SourceProbe(
            source = "proc/loadavg",
            value = procLoad?.let {
                String.format(Locale.getDefault(), "%.2f/%.2f/%.2f", it.first, it.second, it.third)
            } ?: "read failed",
            success = procLoad != null
        )
        if (procLoad != null) {
            return LoadAvgResult(
                loadAvg1 = procLoad.first,
                loadAvg5 = procLoad.second,
                loadAvg15 = procLoad.third,
                probes = probes
            )
        }

        val oneMinFallback = readLoadAvg1FromUptime()
        probes += SourceProbe(
            source = "shell(uptime 1m)",
            value = oneMinFallback?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "read failed",
            success = oneMinFallback != null
        )

        return LoadAvgResult(
            loadAvg1 = oneMinFallback,
            loadAvg5 = null,
            loadAvg15 = null,
            probes = probes
        )
    }

    override suspend fun probeSocTemperatureC(): TemperatureResult {
        val probes = mutableListOf<SourceProbe>()
        val allZones = readThermalZones()
        val zones = filterSocThermalCandidates(allZones)
        val batteryTempForHint = readBatteryTempForHint()

        probes += SourceProbe(
            source = "sysfs(thermal zones)",
            value = "soc-candidates=${zones.size}/${allZones.size}",
            success = zones.isNotEmpty()
        )

        if (zones.isEmpty()) {
            return TemperatureResult(
                temperatureC = null,
                temperatureFractionDigits = null,
                selectedZone = null,
                probes = probes,
                zones = emptyList()
            )
        }

        val scored = zones.map { zone -> zone to scoreThermalZone(zone, batteryTempForHint) }
        val best = scored.maxByOrNull { it.second }
        val selected = best?.takeIf {
            isReliableSocTemperatureCandidate(
                zone = it.first,
                score = it.second,
                batteryHintC = batteryTempForHint,
                candidates = zones
            )
        }?.first
        probes += SourceProbe(
            source = "selector(score)",
            value = when {
                selected != null -> "${selected.zone} ${selected.type} ${formatTemp(selected.tempC, selected.tempFractionDigits)}"
                best != null -> "no reliable candidate (${best.first.zone} ${best.first.type} ${formatTemp(best.first.tempC, best.first.tempFractionDigits)} score=${best.second})"
                else -> "none"
            },
            success = selected != null
        )

        return TemperatureResult(
            temperatureC = selected?.tempC,
            temperatureFractionDigits = selected?.tempFractionDigits,
            selectedZone = selected?.let { "${it.zone}/${it.type}" },
            probes = probes,
            zones = zones
        )
    }

    override fun probeMemoryInfo(): MemoryInfoResult {
        val probes = mutableListOf<SourceProbe>()
        val memInfo = runCatching { File("/proc/meminfo").readLines() }.getOrNull()
        if (memInfo == null) {
            probes += SourceProbe("/proc/meminfo", "read failed", false)
            return MemoryInfoResult(totalBytes = null, availableBytes = null, probes = probes)
        }

        val valuesKb = mutableMapOf<String, Long>()
        memInfo.forEach { line ->
            val key = line.substringBefore(':').trim()
            val value = Regex("\\d+").find(line)?.value?.toLongOrNull() ?: return@forEach
            valuesKb[key] = value
        }

        val totalKb = valuesKb["MemTotal"]
        val availableKb = valuesKb["MemAvailable"]
            ?: run {
                val free = valuesKb["MemFree"] ?: 0L
                val buffers = valuesKb["Buffers"] ?: 0L
                val cached = valuesKb["Cached"] ?: 0L
                val sreclaimable = valuesKb["SReclaimable"] ?: 0L
                val shmem = valuesKb["Shmem"] ?: 0L
                (free + buffers + cached + sreclaimable - shmem).coerceAtLeast(0L)
            }

        probes += SourceProbe(
            source = "/proc/meminfo",
            value = "MemTotal=${totalKb ?: "?"}kB, MemAvailable=${availableKb}kB",
            success = totalKb != null
        )

        val totalBytes = totalKb?.times(1024L)
        val normalizedAvailableKb = if (totalKb != null) {
            availableKb.coerceIn(0L, totalKb)
        } else {
            availableKb
        }
        return MemoryInfoResult(
            totalBytes = totalBytes,
            availableBytes = normalizedAvailableKb.times(1024L),
            probes = probes
        )
    }

    override fun probeCpuModel(): CpuModelResult {
        cachedCpuModelResult?.let { return it }
        val probes = mutableListOf<SourceProbe>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val socModel = Build.SOC_MODEL.trim()
            if (socModel.isNotEmpty() && !socModel.equals("unknown", ignoreCase = true)) {
                probes += SourceProbe("Build.SOC_MODEL", socModel, true)
                return CpuModelResult(modelName = socModel, probes = probes).also {
                    cachedCpuModelResult = it
                }
            }
            probes += SourceProbe("Build.SOC_MODEL", "unavailable", false)
        } else {
            probes += SourceProbe("Build.SOC_MODEL", "API<31", false)
        }

        val cpuInfoLines = runCatching { File("/proc/cpuinfo").readLines() }.getOrNull()
        if (cpuInfoLines != null) {
            val candidates = listOf("Hardware", "model name", "Processor", "Model")
            for (key in candidates) {
                val matched = cpuInfoLines.firstOrNull {
                    it.substringBefore(':').trim().equals(key, ignoreCase = true)
                }
                val value = matched?.substringAfter(':', "")?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    probes += SourceProbe("/proc/cpuinfo:$key", value, true)
                    return CpuModelResult(modelName = value, probes = probes).also {
                        cachedCpuModelResult = it
                    }
                }
            }
            probes += SourceProbe("/proc/cpuinfo", "no model field", false)
        } else {
            probes += SourceProbe("/proc/cpuinfo", "read failed", false)
        }

        val props = listOf("ro.soc.model", "ro.hardware", "ro.board.platform")
        for (prop in props) {
            val value = runCommand(arrayOf("/system/bin/getprop", prop))
                ?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
            if (value != null) {
                probes += SourceProbe("getprop $prop", value, true)
                return CpuModelResult(modelName = value, probes = probes).also {
                    cachedCpuModelResult = it
                }
            }
            probes += SourceProbe("getprop $prop", "unavailable", false)
        }

        return CpuModelResult(modelName = null, probes = probes)
    }

    override fun readCpuCoreFrequencies(): List<CpuCoreFrequencySample> {
        if (cpuDirs.isEmpty()) return emptyList()

        val policyMap = readPolicyFrequencyMap()

        return cpuDirs.mapNotNull { cpuDir ->
            val coreId = cpuDir.name.removePrefix("cpu").toIntOrNull() ?: return@mapNotNull null
            val online = runCatching { File(cpuDir, "online").readText().trim().toInt() == 1 }.getOrDefault(true)

            val cpuFreqDir = File(cpuDir, "cpufreq")
            val directCurrentKhz = readLong(cpuFreqDir, "scaling_cur_freq") ?: readLong(cpuFreqDir, "cpuinfo_cur_freq")
            val directMaxKhz = readLong(cpuFreqDir, "cpuinfo_max_freq") ?: readLong(cpuFreqDir, "scaling_max_freq")

            val policy = policyMap.entries.firstOrNull { coreId in it.value.relatedCores }?.value
            val currentKhz = directCurrentKhz ?: policy?.currentKhz
            val maxKhz = directMaxKhz ?: policy?.maxKhz

            CpuCoreFrequencySample(
                coreId = coreId,
                online = online,
                policy = policy?.name,
                currentMHz = currentKhz?.div(1000L)?.toInt(),
                maxMHz = maxKhz?.div(1000L)?.toInt()
            )
        }
    }

    private fun readCpuTimesFromProcStat(): CpuTimes? {
        val line = runCatching { File("/proc/stat").useLines { it.firstOrNull() } }.getOrNull() ?: return null
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.isEmpty() || parts[0] != "cpu") return null
        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.isEmpty()) return null

        val total = values.sum()
        val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
        return CpuTimes(total = total, idle = idle)
    }

    private fun calculateCpuUsagePercent(previous: CpuTimes?, current: CpuTimes?): Float? {
        if (previous == null || current == null) return null
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        if (totalDelta <= 0L) return null
        val busyDelta = (totalDelta - idleDelta).coerceAtLeast(0L)
        return ((busyDelta.toFloat() / totalDelta.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    private suspend fun sampleCpuUsageShortWindow(): Float? {
        val first = readCpuTimesFromProcStat() ?: return null
        delay(360L)
        val second = readCpuTimesFromProcStat() ?: return null
        return calculateCpuUsagePercent(first, second)
    }

    private fun estimateCpuUsageFromFrequencies(): Float? {
        val onlineCores = readCpuCoreFrequencies().filter { it.online }
        if (onlineCores.isEmpty()) return null

        val pairs = onlineCores.mapNotNull { core ->
            val current = core.currentMHz?.toFloat()?.takeIf { it > 0f } ?: return@mapNotNull null
            val max = core.maxMHz?.toFloat()?.takeIf { it > 0f } ?: return@mapNotNull null
            current to max
        }
        if (pairs.isEmpty()) return null

        val totalCurrent = pairs.sumOf { (current, _) -> current.toDouble() }.toFloat()
        val totalMax = pairs.sumOf { (_, max) -> max.toDouble() }.toFloat()
        if (totalMax <= 0f) return null

        return ((totalCurrent / totalMax).coerceIn(0f, 1f) * 100f)
    }

    private fun readLoadAvgFromProc(): Triple<Float, Float, Float>? {
        val raw = runCatching { File("/proc/loadavg").readText().trim() }.getOrNull() ?: return null
        val parts = raw.split(Regex("\\s+"))
        if (parts.size < 3) return null
        val one = parts[0].toFloatOrNull() ?: return null
        val five = parts[1].toFloatOrNull() ?: return null
        val fifteen = parts[2].toFloatOrNull() ?: return null
        return Triple(one, five, fifteen)
    }

    private fun readLoadAvg1FromUptime(): Float? {
        val output = runCommand(arrayOf("/system/bin/uptime")) ?: return null
        val match = Regex(
            "load averages?:\\s*([0-9]+(?:\\.[0-9]+)?)[,\\s]+([0-9]+(?:\\.[0-9]+)?)[,\\s]+([0-9]+(?:\\.[0-9]+)?)",
            RegexOption.IGNORE_CASE
        ).find(output) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    private fun readThermalZones(): List<ThermalZoneReading> {
        val thermalRoot = File("/sys/class/thermal")
        val zones = thermalRoot.listFiles { file ->
            file.isDirectory && file.name.startsWith("thermal_zone")
        }?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }.orEmpty()

        return zones.mapNotNull { zone ->
            val type = runCatching { File(zone, "type").readText().trim() }.getOrNull() ?: return@mapNotNull null
            val rawText = runCatching { File(zone, "temp").readText().trim() }.getOrNull() ?: return@mapNotNull null
            val raw = rawText.toFloatOrNull() ?: return@mapNotNull null
            val normalized = normalizeThermalTemp(raw = raw, type = type, rawText = rawText) ?: return@mapNotNull null
            ThermalZoneReading(
                zone = zone.name,
                type = type,
                tempC = normalized.celsius,
                tempFractionDigits = normalized.fractionDigits
            )
        }
    }

    private fun readBatteryTempForHint(): Float? {
        val raw = runCatching { File("/sys/class/power_supply/battery/temp").readText().trim().toFloat() }.getOrNull()
            ?: return null
        return when {
            raw > 1000f -> raw / 1000f
            raw > 150f -> raw / 10f
            else -> raw
        }
    }

    private fun normalizeThermalTemp(raw: Float, type: String, rawText: String): NormalizedThermalTemp? {
        if (raw <= 0f) return null
        val loweredType = type.lowercase(Locale.getDefault())
        val rawFractionDigits = countFractionDigits(rawText)
        val candidates = listOf(1, 10, 100, 1000)
            .map { divisor ->
                ThermalTempCandidate(
                    value = raw / divisor.toFloat(),
                    divisor = divisor.toFloat(),
                    fractionDigits = (rawFractionDigits + divisorFractionDigits(divisor)).coerceIn(0, 1)
                )
            }
            .filter { it.value in 10f..150f }
        if (candidates.isEmpty()) return null

        val selected = candidates.maxByOrNull { candidate ->
            val value = candidate.value
            val divisor = candidate.divisor
            var score = 0
            score += when {
                value in 25f..95f -> 40
                value in 15f..120f -> 10
                else -> -40
            }
            score += when (divisor) {
                1000f -> if (raw >= 20000f) 25 else 5
                100f -> if (raw in 1500f..20000f) 18 else 4
                10f -> if (raw in 150f..1500f) 20 else 4
                else -> if (raw <= 150f) 15 else -20
            }
            if (loweredType.contains("cpu") || loweredType.contains("soc") || loweredType.contains("ap")) {
                if (value < 20f) score -= 30
            }
            score
        } ?: return null

        return NormalizedThermalTemp(
            celsius = roundToFractionDigits(selected.value, selected.fractionDigits),
            fractionDigits = selected.fractionDigits
        )
    }

    private fun scoreThermalZone(zone: ThermalZoneReading, batteryHintC: Float?): Int {
        val type = zone.type.lowercase(Locale.getDefault())
        var score = when {
            type.contains("soc") -> 100
            type.contains("cpu") -> 90
            isApThermalType(type) -> 80
            type.contains("tsens") -> 70
            type.contains("gpu") -> 40
            else -> -10
        }

        score += when {
            zone.tempC in 25f..95f -> 40
            zone.tempC in 15f..120f -> 10
            else -> -60
        }

        if (zone.tempC < 15f) {
            score -= 80
        } else if (zone.tempC < 20f) {
            score -= 30
        }

        if (batteryHintC != null) {
            val delta = zone.tempC - batteryHintC
            score += when {
                delta in 0f..30f -> 25
                delta in -5f..45f -> 10
                else -> -15
            }

            if (zone.tempC + 8f < batteryHintC) {
                score -= 70
            }
        }

        if (!isSocFocusedThermalType(type)) {
            score -= 25
        }

        return score
    }

    private fun filterSocThermalCandidates(zones: List<ThermalZoneReading>): List<ThermalZoneReading> {
        return zones.filter { zone ->
            val lowered = zone.type.lowercase(Locale.getDefault())
            !isIrrelevantThermalType(lowered) && isSocFocusedThermalType(lowered)
        }
    }

    private fun isSocFocusedThermalType(type: String): Boolean {
        return type.contains("soc") ||
            type.contains("cpu") ||
            type.contains("cpuss") ||
            isApThermalType(type) ||
            type.contains("tsens") ||
            type.contains("cluster") ||
            type.contains("little") ||
            type.contains("big") ||
            type.contains("gold") ||
            type.contains("silver") ||
            type.contains("prime")
    }

    private fun isApThermalType(type: String): Boolean {
        return type.contains("apss") || Regex("(^|[_-])ap([_-]|$)").containsMatchIn(type)
    }

    private fun isIrrelevantThermalType(type: String): Boolean {
        val lowered = type.lowercase(Locale.getDefault())
        val keywords = listOf(
            "battery",
            "batt",
            "usb",
            "wireless",
            "wlc",
            "charger",
            "chg",
            "skin",
            "quiet",
            "display",
            "disp",
            "camera",
            "flash",
            "modem",
            "wifi",
            "wlan",
            "pmic",
            "bcl"
        )
        return keywords.any { lowered.contains(it) }
    }

    private fun isReliableSocTemperatureCandidate(
        zone: ThermalZoneReading,
        score: Int,
        batteryHintC: Float?,
        candidates: List<ThermalZoneReading>
    ): Boolean {
        if (score < 35) return false
        if (zone.tempC > 120f) return false
        if (batteryHintC != null && zone.tempC + 10f < batteryHintC) return false

        // Cold environments are possible; for very low readings, require stronger evidence.
        if (zone.tempC < 15f) {
            val peerAgree = hasPeerAgreement(zone, candidates)
            val batterySupports = batteryHintC == null || zone.tempC + 5f >= batteryHintC
            return score >= 60 && peerAgree && batterySupports
        }
        return true
    }

    private fun hasPeerAgreement(
        zone: ThermalZoneReading,
        candidates: List<ThermalZoneReading>
    ): Boolean {
        return candidates.any { it.zone != zone.zone && abs(it.tempC - zone.tempC) <= 6f }
    }

    private fun readPolicyFrequencyMap(): Map<String, PolicyFrequency> {
        val cpufreqRoot = File("/sys/devices/system/cpu/cpufreq")
        val policies = cpufreqRoot.listFiles { file ->
            file.isDirectory && file.name.startsWith("policy")
        }?.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE }.orEmpty()

        return policies.associate { policyDir ->
            val relatedRaw = runCatching { File(policyDir, "related_cpus").readText().trim() }.getOrNull().orEmpty()
            val relatedCores = parseCpuList(relatedRaw)
            val currentKhz = readLong(policyDir, "scaling_cur_freq") ?: readLong(policyDir, "cpuinfo_cur_freq")
            val maxKhz = readLong(policyDir, "cpuinfo_max_freq") ?: readLong(policyDir, "scaling_max_freq")
            policyDir.name to PolicyFrequency(
                name = policyDir.name,
                relatedCores = relatedCores,
                currentKhz = currentKhz,
                maxKhz = maxKhz
            )
        }
    }

    private fun parseCpuList(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
        val result = mutableSetOf<Int>()
        raw.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            if (token.contains('-')) {
                val bounds = token.split('-', limit = 2)
                val start = bounds.getOrNull(0)?.toIntOrNull()
                val end = bounds.getOrNull(1)?.toIntOrNull()
                if (start != null && end != null) {
                    val from = minOf(start, end)
                    val to = maxOf(start, end)
                    for (i in from..to) result += i
                }
            } else {
                token.toIntOrNull()?.let { result += it }
            }
        }
        return result
    }

    private fun runCommand(command: Array<String>, timeoutMs: Long = 1200L): String? {
        return runCatching {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching null
            }
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun readLong(parent: File, fileName: String): Long? {
        val text = runCatching { File(parent, fileName).readText().trim() }.getOrNull() ?: return null
        text.toLongOrNull()?.let { return it }
        val values = Regex("-?\\d+")
            .findAll(text)
            .mapNotNull { it.value.toLongOrNull() }
            .toList()
        if (values.isEmpty()) return null
        if (values.size == 1) return values.first()
        return values.maxByOrNull { abs(it) }
    }

    private fun formatPercent(value: Float): String {
        return String.format(Locale.getDefault(), "%.1f%%", value)
    }

    private fun formatTemp(value: Float, fractionDigits: Int): String {
        val digits = fractionDigits.coerceIn(0, 1)
        return String.format(Locale.getDefault(), "%.${digits}f °C", value)
    }

    private fun countFractionDigits(rawText: String): Int {
        val dotIndex = rawText.indexOf('.')
        if (dotIndex < 0 || dotIndex >= rawText.lastIndex) return 0
        return rawText.substring(dotIndex + 1).takeWhile { it.isDigit() }.length
    }

    private fun divisorFractionDigits(divisor: Int): Int {
        return when (divisor) {
            1000 -> 3
            100 -> 2
            10 -> 1
            else -> 0
        }
    }

    private fun roundToFractionDigits(value: Float, digits: Int): Float {
        if (digits <= 0) return round(value)
        val factor = 10.0.pow(digits.toDouble())
        return (round(value.toDouble() * factor) / factor).toFloat()
    }

}

private data class ThermalTempCandidate(
    val value: Float,
    val divisor: Float,
    val fractionDigits: Int
)

private data class NormalizedThermalTemp(
    val celsius: Float,
    val fractionDigits: Int
)

private data class PolicyFrequency(
    val name: String,
    val relatedCores: Set<Int>,
    val currentKhz: Long?,
    val maxKhz: Long?
)
