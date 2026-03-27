package com.lele.llmonitor.ui.settings

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val OPEN_SOURCE_INVENTORY_ASSET = "open_source_inventory.json"
private const val OPEN_SOURCE_NOTICES_ASSET = "open_source_notices.json"
private const val THIRD_PARTY_NOTICES_ASSET = "THIRD_PARTY_NOTICES.txt"

internal data class OpenSourceInventoryDocument(
    val schemaVersion: Int = 1,
    val generatedAt: String = "",
    val configurationName: String = "",
    val artifacts: List<OpenSourceInventoryItem> = emptyList()
)

internal data class OpenSourceInventoryItem(
    val coordinates: String = "",
    val resolvedVersion: String = "",
    val isDirectDependency: Boolean = false,
    val licenseSpdxId: String = "",
    val licenseName: String = "",
    val licenseUrl: String = "",
    val licenseText: String = "",
    val noticeText: String = "",
    val sourceKind: String = "",
    val distributionScope: String = "",
    val status: String = ""
)

internal data class OpenSourceNoticesDocument(
    val schemaVersion: Int = 1,
    val generatedAt: String = "",
    val summary: OpenSourceNoticesSummary = OpenSourceNoticesSummary(),
    val groups: List<OpenSourceNoticeGroup> = emptyList()
)

internal data class OpenSourceNoticesSummary(
    val totalGroups: Int = 0,
    val totalArtifacts: Int = 0,
    val licenseCounts: Map<String, Int> = emptyMap(),
    val statusCounts: Map<String, Int> = emptyMap()
)

internal data class OpenSourceNoticeGroup(
    val id: String = "",
    val displayName: String = "",
    val artifactCount: Int = 0,
    val artifactCoordinates: List<String> = emptyList(),
    val licenseNames: List<String> = emptyList(),
    val licenseSpdxIds: List<String> = emptyList(),
    val licenseUrls: List<String> = emptyList(),
    val licenseText: String = "",
    val noticeText: String = "",
    val detailText: String = "",
    val status: String = ""
)

internal data class OpenSourceLicenseBundle(
    val inventory: OpenSourceInventoryDocument = OpenSourceInventoryDocument(),
    val notices: OpenSourceNoticesDocument = OpenSourceNoticesDocument(),
    val thirdPartyNoticesText: String = ""
)

internal sealed interface OpenSourceLicensesUiState {
    data object Loading : OpenSourceLicensesUiState
    data class Ready(val bundle: OpenSourceLicenseBundle) : OpenSourceLicensesUiState
    data class Error(val message: String) : OpenSourceLicensesUiState
}

internal object OpenSourceLicensesRepository {
    private val gson = Gson()
    private val lock = Any()
    private var initialized = false
    private val uiState = MutableStateFlow<OpenSourceLicensesUiState>(OpenSourceLicensesUiState.Loading)

    val state: StateFlow<OpenSourceLicensesUiState> = uiState.asStateFlow()

    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            initialized = true
            val appContext = context.applicationContext
            uiState.value = runCatching {
                OpenSourceLicensesUiState.Ready(loadOpenSourceLicenseBundle(appContext, gson))
            }.getOrElse { throwable ->
                OpenSourceLicensesUiState.Error(
                    throwable.message ?: com.lele.llmonitor.i18n.l10n("开源许可信息加载失败")
                )
            }
        }
    }
}

internal fun loadOpenSourceLicenseBundle(
    context: Context,
    gson: Gson = Gson()
): OpenSourceLicenseBundle {
    val inventoryJson = context.assets.open(OPEN_SOURCE_INVENTORY_ASSET)
        .bufferedReader()
        .use { it.readText() }
    val noticesJson = context.assets.open(OPEN_SOURCE_NOTICES_ASSET)
        .bufferedReader()
        .use { it.readText() }
    val thirdPartyNoticesText = context.assets.open(THIRD_PARTY_NOTICES_ASSET)
        .bufferedReader()
        .use { it.readText() }
    return parseOpenSourceLicenseBundle(
        inventoryJson = inventoryJson,
        noticesJson = noticesJson,
        thirdPartyNoticesText = thirdPartyNoticesText,
        gson = gson
    )
}

internal fun parseOpenSourceLicenseBundle(
    inventoryJson: String,
    noticesJson: String,
    thirdPartyNoticesText: String,
    gson: Gson = Gson()
): OpenSourceLicenseBundle {
    val inventory = gson.fromJson(inventoryJson, OpenSourceInventoryDocument::class.java)
    val notices = gson.fromJson(noticesJson, OpenSourceNoticesDocument::class.java)
    return OpenSourceLicenseBundle(
        inventory = inventory ?: OpenSourceInventoryDocument(),
        notices = notices ?: OpenSourceNoticesDocument(),
        thirdPartyNoticesText = thirdPartyNoticesText
    )
}

internal fun buildOpenSourceHeroChips(summary: OpenSourceNoticesSummary): List<String> {
    val licenseTypeCount = summary.licenseCounts.keys.count(::isUserFacingLicenseName)
    return buildList {
        add(com.lele.llmonitor.i18n.l10n("${summary.totalArtifacts} 个组件"))
        add(com.lele.llmonitor.i18n.l10n("${summary.totalGroups} 组许可"))
        if (licenseTypeCount > 0) {
            add(com.lele.llmonitor.i18n.l10n("$licenseTypeCount 类许可"))
        }
    }
}

internal fun buildOpenSourceSummarySubtitle(): String {
    return com.lele.llmonitor.i18n.l10n("LLMonitor 当前版本使用的第三方组件及其许可信息")
}

internal fun buildOpenSourceGroupCardSubtitle(group: OpenSourceNoticeGroup): String {
    val licenseNames = group.licenseNames.filter(::isUserFacingLicenseName).distinct()
    val componentSummary = com.lele.llmonitor.i18n.l10n("适用于 ${group.artifactCount} 个组件")
    return when {
        licenseNames.isEmpty() -> componentSummary
        licenseNames.size == 1 -> "$componentSummary · ${licenseNames.first()}"
        else -> com.lele.llmonitor.i18n.l10n("$componentSummary · ${licenseNames.first()} 等 ${licenseNames.size} 类许可")
    }
}

internal fun buildOpenSourceDetailChips(group: OpenSourceNoticeGroup): List<String> {
    val licenseNames = group.licenseNames.filter(::isUserFacingLicenseName).distinct()
    return buildList {
        add(com.lele.llmonitor.i18n.l10n("${group.artifactCount} 个组件"))
        when {
            licenseNames.size == 1 -> add(licenseNames.first())
            licenseNames.size > 1 -> add(com.lele.llmonitor.i18n.l10n("${licenseNames.size} 类许可"))
        }
    }
}

private fun isUserFacingLicenseName(licenseName: String): Boolean {
    return licenseName.isNotBlank() && !licenseName.equals("Unknown", ignoreCase = true)
}
