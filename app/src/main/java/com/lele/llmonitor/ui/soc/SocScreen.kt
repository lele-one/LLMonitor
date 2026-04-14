package com.lele.llmonitor.ui.soc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lele.llmonitor.data.SettingsManager
import com.lele.llmonitor.data.soc.CpuCoreFrequencySample
import com.lele.llmonitor.data.soc.SocSnapshot
import com.lele.llmonitor.data.soc.SourceProbe
import com.lele.llmonitor.data.soc.ThermalZoneReading
import com.lele.llmonitor.ui.components.HomeCard
import com.lele.llmonitor.ui.components.InfoCard
import com.lele.llmonitor.ui.components.squishyClickable
import com.lele.llmonitor.ui.theme.AppCorners
import com.lele.llmonitor.ui.theme.AppShapes
import com.lele.llmonitor.ui.theme.llClassSectionMetaColor
import com.lele.llmonitor.ui.theme.llClassSectionTitleColor
import java.util.Locale

@Composable
fun SocScreen(
    viewModel: SocViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = uiState.snapshot
    val isDebugMode by SettingsManager.isDebugModeEnabled

    if (snapshot == null && uiState.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(com.lele.llmonitor.i18n.l10n("正在读取 SoC 指标…"), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    if (snapshot == null && uiState.error != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(com.lele.llmonitor.i18n.l10n("SoC 采集不可用"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(uiState.error ?: "--", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val cores = snapshot?.coreFrequencies.orEmpty()
    val clusters = remember(cores) { buildPolicyClusters(cores) }
    val thermalZones = snapshot?.thermalZones.orEmpty()

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize()
    ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item(key = "soc_spacer_top") { Spacer(Modifier.height(8.dp)) }

            item(key = "soc_metrics_row_1") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(
                        label = com.lele.llmonitor.i18n.l10n("CPU 占用"),
                        value = snapshot?.cpuUsagePercent?.let { formatPercent(it) } ?: com.lele.llmonitor.i18n.l10n("受限"),
                        modifier = Modifier.weight(1f).squishyClickable { /* Optional tap logic */ },
                        sourceLines = if (isDebugMode) toSourceLines(snapshot?.cpuUsageSources.orEmpty()) else emptyList()
                    )
                    InfoCard(
                        label = com.lele.llmonitor.i18n.l10n("SoC 温度"),
                        value = snapshot?.socTemperatureC?.let {
                            formatTemp(it, snapshot.socTemperatureFractionDigits ?: 1)
                        } ?: "--",
                        modifier = Modifier.weight(1f).squishyClickable { /* Optional tap logic */ },
                        sourceLines = if (isDebugMode) tempSourceLines(
                            selectedZone = snapshot?.socTemperatureSource,
                            probes = snapshot?.socTemperatureSources.orEmpty()
                        ) else emptyList()
                    )
                }
            }

            item(key = "soc_metrics_row_2") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(
                        label = com.lele.llmonitor.i18n.l10n("在线CPU核心"),
                        value = snapshot?.let { "${it.onlineCores} / ${it.totalCores}" } ?: "--",
                        modifier = Modifier.weight(1f).squishyClickable { /* Optional tap logic */ }
                    )
                    InfoCard(
                        label = com.lele.llmonitor.i18n.l10n("系统1min负载"),
                        value = formatNullable(snapshot?.loadAvg1),
                        modifier = Modifier.weight(1f).squishyClickable { /* Optional tap logic */ },
                        sourceLines = if (isDebugMode) toSourceLines(snapshot?.loadAvgSources.orEmpty()) else emptyList()
                    )
                }
            }

            item(key = "soc_metrics_row_3") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(
                        label = "${com.lele.llmonitor.i18n.l10n("内存占用")}(GB) ${formatMemoryUsagePercent(
                            totalBytes = snapshot?.memoryTotalBytes,
                            availableBytes = snapshot?.memoryAvailableBytes
                        )}",
                        value = formatMemoryUsedOverTotal(
                            totalBytes = snapshot?.memoryTotalBytes,
                            availableBytes = snapshot?.memoryAvailableBytes
                        ),
                        modifier = Modifier.weight(1f).squishyClickable { /* Optional tap logic */ },
                        singleLineAutoShrink = true,
                        singleLineAutoShrinkReferenceText = memoryValueReferenceText(snapshot?.memoryTotalBytes),
                        sourceLines = if (isDebugMode) toSourceLines(snapshot?.memorySources.orEmpty()) else emptyList()
                    )
                    InfoCard(
                        label = com.lele.llmonitor.i18n.l10n("CPU 型号"),
                        value = snapshot?.cpuModelName ?: "--",
                        modifier = Modifier.weight(1f).squishyClickable { /* Optional tap logic */ },
                        singleLineAutoShrink = true,
                        sourceLines = if (isDebugMode) toSourceLines(snapshot?.cpuModelSources.orEmpty()) else emptyList()
                    )
                }
            }

            if (cores.isEmpty()) {
                item(key = "soc_core_freq_empty") {
                    HomeCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = com.lele.llmonitor.i18n.l10n("当前设备未暴露可用的 CPU 频率节点，CPU 频率信息不可用。"),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(
                    items = clusters,
                    key = { it.key }
                ) { cluster ->
                    ClusterCoreSection(
                        cluster = cluster,
                        showClusterHeader = false
                    )
                }
            }

            if (isDebugMode) {
                item(key = "soc_thermal_zones_title") {
                    Text(
                        com.lele.llmonitor.i18n.l10n("SoC 相关 Thermal Zones"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = llClassSectionTitleColor()
                    )
                }

                if (thermalZones.isEmpty()) {
                    item(key = "soc_thermal_zones_empty") {
                        HomeCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = com.lele.llmonitor.i18n.l10n("未发现可读且与 SoC 相关的 thermal_zone 节点。"),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(
                        items = thermalZones,
                        key = { "${it.zone}-${it.type}" }
                    ) { zone ->
                        ThermalZoneCard(zone = zone)
                    }
                }
            }

                item(key = "soc_spacer_bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
}

private data class PolicyClusterUi(
    val key: String,
    val title: String,
    val onlineCount: Int,
    val totalCount: Int,
    val cores: List<CpuCoreFrequencySample>
)

private fun buildPolicyClusters(cores: List<CpuCoreFrequencySample>): List<PolicyClusterUi> {
    val grouped = cores.groupBy { normalizedPolicyKey(it.policy) }
    return grouped.entries
        .map { (key, groupedCores) ->
            val sortedCores = groupedCores.sortedBy { it.coreId }
            val onlineCount = sortedCores.count { it.online }
            PolicyClusterUi(
                key = key,
                title = if (key == UNASSIGNED_POLICY_KEY) "CPU" else "CPU $key",
                onlineCount = onlineCount,
                totalCount = sortedCores.size,
                cores = sortedCores
            )
        }
        .sortedWith(
            compareBy<PolicyClusterUi>(
                { if (it.key == UNASSIGNED_POLICY_KEY) 1 else 0 },
                { policyNumericOrder(it.key) },
                { it.key }
            )
        )
}

private val UNASSIGNED_POLICY_KEY = com.lele.llmonitor.i18n.l10n("未分簇")

private fun normalizedPolicyKey(rawPolicy: String?): String {
    val policy = rawPolicy?.trim().orEmpty()
    if (policy.isBlank()) return UNASSIGNED_POLICY_KEY
    if (policy.startsWith("policy")) {
        val digits = policy.removePrefix("policy")
        if (digits.isNotBlank() && digits.all { it.isDigit() }) {
            return "policy$digits"
        }
    }
    return policy
}

private fun policyNumericOrder(key: String): Int {
    if (!key.startsWith("policy")) return Int.MAX_VALUE
    return key.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE
}

@Composable
private fun ClusterCoreSection(
    cluster: PolicyClusterUi,
    showClusterHeader: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showClusterHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cluster.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = llClassSectionTitleColor()
                )
                Text(
                    text = com.lele.llmonitor.i18n.l10n("在线 ${cluster.onlineCount} / ${cluster.totalCount}"),
                    style = MaterialTheme.typography.labelMedium,
                    color = llClassSectionMetaColor()
                )
            }
        }
        CoreFrequencyGrid(cores = cluster.cores)
    }
}

@Composable
private fun CoreFrequencyGrid(
    cores: List<CpuCoreFrequencySample>
) {
    val columns = coreGridColumns(cores.size)
    val rowChunks = cores.chunked(columns)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val rowGap = 6.dp
    val cardHorizontalPadding = 8.dp
    val typography = remember(columns, screenWidthDp, density.fontScale) {
        coreTypographyAdaptive(
            columns = columns,
            screenWidthDp = screenWidthDp,
            density = density,
            textMeasurer = textMeasurer,
            rowGapDp = rowGap,
            cardHorizontalPaddingDp = cardHorizontalPadding
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        rowChunks.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                row.forEach { core ->
                    CoreFrequencyCell(
                        core = core,
                        typography = typography,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (rowIndex != rowChunks.lastIndex) {
                Spacer(Modifier.height(rowGap))
            }
        }
    }
}

@Composable
private fun CoreFrequencyCell(
    core: CpuCoreFrequencySample,
    typography: CoreCellTypography,
    modifier: Modifier = Modifier
) {
    val titleText = if (core.online) "CPU${core.coreId}" else com.lele.llmonitor.i18n.l10n("CPU${core.coreId} 离线")
    val policyTag = policyTag(core.policy)
    val utilization = core.currentMHz?.let { current ->
        core.maxMHz?.takeIf { it > 0 }?.let { max ->
            (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        }
    }

    HomeCard(
        modifier = modifier.squishyClickable { /* Optional tap logic */ },
        accentColor = MaterialTheme.colorScheme.primary
    ) {
        DashboardCoreCellContent(
            titleText = titleText,
            policyTag = policyTag,
            typography = typography,
            online = core.online,
            currentMHz = core.currentMHz,
            maxMHz = core.maxMHz,
            utilization = utilization
        )
    }
}

@Composable
private fun DashboardCoreCellContent(
    titleText: String,
    policyTag: String?,
    typography: CoreCellTypography,
    online: Boolean,
    currentMHz: Int?,
    maxMHz: Int?,
    utilization: Float?
) {
    val ratioText = utilization?.let { String.format(Locale.getDefault(), "%.0f%%", it * 100f) } ?: "--"
    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        CoreCardHeader(
            titleText = titleText,
            policyTag = policyTag,
            typography = typography,
            online = online
        )
        MainFrequencyLine(
            valueMHz = currentMHz,
            numberSize = typography.valueSize,
            unitSize = typography.unitSize,
            numberColor = MaterialTheme.colorScheme.onSurface,
            unitColor = MaterialTheme.colorScheme.primary
        )
        MaxFrequencyLine(
            valueMHz = maxMHz,
            numberSize = typography.maxValueSize,
            unitSize = typography.unitSize,
            numberColor = MaterialTheme.colorScheme.onSurface,
            unitColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { utilization ?: 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = com.lele.llmonitor.i18n.l10n("占比"),
                fontSize = typography.metaSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            Text(
                text = ratioText,
                fontSize = typography.metaSize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun CoreCardHeader(
    titleText: String,
    policyTag: String?,
    typography: CoreCellTypography,
    online: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titleText,
            fontSize = typography.titleSize,
            fontWeight = FontWeight.Bold,
            color = if (online) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        if (!policyTag.isNullOrBlank()) {
            Text(
                text = policyTag,
                fontSize = typography.metaSize,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun MainFrequencyLine(
    valueMHz: Int?,
    numberSize: TextUnit,
    unitSize: TextUnit,
    numberColor: Color,
    unitColor: Color
) {
    val valueText = valueMHz?.toString() ?: "--"
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(fontSize = numberSize, color = numberColor, fontWeight = FontWeight.Bold)) {
            append(valueText)
        }
        append(" ")
        withStyle(SpanStyle(fontSize = unitSize, color = unitColor)) {
            append("MHz")
        }
    }
    Text(
        text = annotated,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}

@Composable
private fun MaxFrequencyLine(
    valueMHz: Int?,
    numberSize: TextUnit,
    unitSize: TextUnit,
    numberColor: Color,
    unitColor: Color
) {
    val valueText = valueMHz?.toString() ?: "--"
    val annotated = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontSize = (unitSize.value + 1f).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                color = unitColor
            )
        ) {
            append("MAX")
        }
        append(" ")
        withStyle(SpanStyle(fontSize = numberSize, color = numberColor, fontWeight = FontWeight.Bold)) {
            append(valueText)
        }
        append(" ")
        withStyle(SpanStyle(fontSize = unitSize, color = unitColor)) {
            append("MHz")
        }
    }
    Text(
        text = annotated,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}

private data class CoreCellTypography(
    val titleSize: TextUnit,
    val valueSize: TextUnit,
    val metaSize: TextUnit,
    val maxValueSize: TextUnit,
    val unitSize: TextUnit
)

private val coreTypographyCache = mutableMapOf<String, CoreCellTypography>()

private fun coreTypographyAdaptive(
    columns: Int,
    screenWidthDp: Int,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    rowGapDp: androidx.compose.ui.unit.Dp,
    cardHorizontalPaddingDp: androidx.compose.ui.unit.Dp
): CoreCellTypography {
    val cacheKey = "${columns}_${screenWidthDp}_${density.fontScale}"
    coreTypographyCache[cacheKey]?.let { return it }

    val screenPx = with(density) { screenWidthDp.dp.toPx() }
    val outerHorizontalPx = with(density) { 32.dp.toPx() } // LazyColumn 左右总 padding: 16 + 16
    val rowGapPx = with(density) { rowGapDp.toPx() } * (columns - 1).coerceAtLeast(0)
    val cellPx = ((screenPx - outerHorizontalPx - rowGapPx) / columns).coerceAtLeast(1f)
    val contentPx = (cellPx - with(density) { cardHorizontalPaddingDp.toPx() * 2f }).coerceAtLeast(1f)
    val lineGapPx = with(density) { 2.dp.toPx() }

    var scale = 1.0f
    while (scale >= 0.60f) {
        val sizes = CoreCellTypography(
            titleSize = (12f * scale).sp,
            valueSize = (24f * scale).sp,
            metaSize = (11f * scale).sp,
            maxValueSize = (14f * scale).sp,
            unitSize = (8f * scale).sp
        )

        val titleFits = measureWidthPx(textMeasurer, com.lele.llmonitor.i18n.l10n("CPU10 离线"), sizes.titleSize, bold = true) <= contentPx
        val valueLineFits =
            measureWidthPx(textMeasurer, "99999", sizes.valueSize, bold = true) +
                lineGapPx +
                measureWidthPx(textMeasurer, "MHz", sizes.unitSize) <= contentPx
        val maxLineFits =
            measureWidthPx(textMeasurer, "MAX", (sizes.unitSize.value + 1f).sp, bold = true) +
                lineGapPx +
                measureWidthPx(textMeasurer, "99999", sizes.maxValueSize, bold = true) +
                lineGapPx +
                measureWidthPx(textMeasurer, "MHz", sizes.unitSize) <= contentPx

        if (titleFits && valueLineFits && maxLineFits) {
            coreTypographyCache[cacheKey] = sizes
            return sizes
        }
        scale -= 0.03f
    }

    return CoreCellTypography(
        titleSize = 8.sp,
        valueSize = 16.sp,
        metaSize = 9.sp,
        maxValueSize = 10.sp,
        unitSize = 7.sp
    ).also { coreTypographyCache[cacheKey] = it }
}

private fun policyTag(policy: String?): String? {
    val value = policy?.trim().orEmpty()
    if (value.isBlank()) return null
    val digits = value.removePrefix("policy")
    return if (digits.isNotBlank() && digits.all { it.isDigit() }) {
        "P$digits"
    } else {
        value
    }
}

private fun measureWidthPx(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    size: TextUnit,
    bold: Boolean = false
): Float {
    val result = measurer.measure(
        text = text,
        style = TextStyle(
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    )
    return result.size.width.toFloat()
}

private fun coreGridColumns(coreCount: Int): Int {
    return when (coreCount) {
        1 -> 1
        2 -> 2
        4 -> 2
        6 -> 3
        8 -> 4
        9 -> 3
        10 -> 4
        else -> when {
            coreCount <= 3 -> coreCount
            coreCount <= 6 -> 3
            coreCount <= 8 -> 4
            coreCount <= 12 -> 4
            else -> 5
        }
    }
}

@Composable
private fun ThermalZoneCard(zone: ThermalZoneReading) {
    HomeCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${zone.zone} / ${zone.type}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatTemp(zone.tempC, zone.tempFractionDigits),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

private fun toSourceLines(probes: List<SourceProbe>): List<String> {
    return probes.map { probe ->
        val status = if (probe.success) "OK" else "FAIL"
        "[$status] ${probe.source}: ${probe.value}"
    }
}

private fun tempSourceLines(
    selectedZone: String?,
    probes: List<SourceProbe>
): List<String> {
    val lines = mutableListOf<String>()
    if (!selectedZone.isNullOrBlank()) {
        lines += com.lele.llmonitor.i18n.l10n("当前来源: $selectedZone")
    }
    lines += toSourceLines(probes)
    return lines
}

private fun formatPercent(value: Float): String {
    return String.format(Locale.getDefault(), "%.1f%%", value)
}

private fun formatTemp(value: Float, fractionDigits: Int): String {
    val digits = fractionDigits.coerceIn(0, 1)
    return String.format(Locale.getDefault(), "%.${digits}f °C", value)
}

private fun formatNullable(value: Float?): String {
    if (value == null) return "--"
    return String.format(Locale.getDefault(), "%.2f", value)
}

private fun formatMemoryUsagePercent(totalBytes: Long?, availableBytes: Long?): String {
    val total = totalBytes?.takeIf { it > 0L } ?: return "--"
    val available = (availableBytes ?: 0L).coerceIn(0L, total)
    val used = (total - available).coerceAtLeast(0L)
    val usedPercent = (used.toDouble() / total.toDouble() * 100.0).coerceIn(0.0, 100.0)
    return String.format(Locale.getDefault(), "%.0f%%", usedPercent)
}

private fun formatMemoryUsedOverTotal(totalBytes: Long?, availableBytes: Long?): String {
    val total = totalBytes?.takeIf { it > 0L } ?: return "--"
    val available = (availableBytes ?: 0L).coerceIn(0L, total)
    val used = (total - available).coerceAtLeast(0L)
    val usedGiB = used / (1024.0 * 1024.0 * 1024.0)
    val totalGiB = total / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.1f / %.1f", usedGiB, totalGiB)
}

private fun memoryValueReferenceText(totalBytes: Long?): String {
    val total = totalBytes?.takeIf { it > 0L }
    val totalGiB = if (total != null) {
        total / (1024.0 * 1024.0 * 1024.0)
    } else {
        99.9
    }
    return String.format(Locale.getDefault(), "%.1f / %.1f", totalGiB, totalGiB)
}
