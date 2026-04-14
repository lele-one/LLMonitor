package com.lele.llmonitor.ui.settings

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal const val OPEN_SOURCE_LICENSE_DETAIL_ARG = "groupId"
internal const val OPEN_SOURCE_LICENSE_DETAIL_ROUTE = "open_source_license_detail/{$OPEN_SOURCE_LICENSE_DETAIL_ARG}"

internal fun openSourceLicenseDetailRoute(groupId: String): String {
    return "open_source_license_detail/${Uri.encode(groupId)}"
}

@Composable
private fun OpenSourceSummaryHeroCard(summary: OpenSourceNoticesSummary) {
    SettingsHeroCard(
        title = com.lele.llmonitor.i18n.l10n("开源许可"),
        subtitle = buildOpenSourceSummarySubtitle(),
        chips = buildOpenSourceHeroChips(summary),
        icon = Icons.Rounded.Description
    )
}

@Composable
private fun OpenSourceGroupCard(
    group: OpenSourceNoticeGroup,
    onClick: () -> Unit
) {
    SettingsPreferenceCard(
        title = group.displayName,
        subtitle = buildOpenSourceGroupCardSubtitle(group),
        icon = Icons.Rounded.Verified,
        outerPadding = settingsCardOuterPadding(vertical = 6.dp),
        onClick = onClick
    )
}

@Composable
private fun OpenSourceErrorCard(message: String) {
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("开源许可暂不可用"),
        leadingIcon = Icons.Rounded.Info,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OpenSourceLoadingCard() {
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("开源许可"),
        leadingIcon = Icons.Rounded.Description,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = com.lele.llmonitor.i18n.l10n("正在读取当前版本附带的许可材料…"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OpenSourceArtifactsCard(group: OpenSourceNoticeGroup) {
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("适用组件"),
        leadingIcon = Icons.Rounded.Verified,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.artifactCoordinates.forEach { coordinate ->
                Text(
                    text = coordinate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun OpenSourceMetadataCard(group: OpenSourceNoticeGroup) {
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("许可标识与链接"),
        leadingIcon = Icons.Rounded.Link,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OpenSourceMetadataRow(
                label = com.lele.llmonitor.i18n.l10n("许可证"),
                value = group.licenseNames.joinToString("\n").ifBlank { com.lele.llmonitor.i18n.l10n("未声明") }
            )
            if (group.licenseSpdxIds.isNotEmpty()) {
                OpenSourceMetadataRow(
                    label = "SPDX",
                    value = group.licenseSpdxIds.joinToString("\n")
                )
            }
            if (group.licenseUrls.isNotEmpty()) {
                OpenSourceLinkRow(
                    label = com.lele.llmonitor.i18n.l10n("链接"),
                    urls = group.licenseUrls
                )
            }
        }
    }
}

@Composable
private fun OpenSourceMetadataRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun OpenSourceLinkRow(
    label: String,
    urls: List<String>
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            urls.distinct().forEach { url ->
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri(url) }
                )
            }
        }
    }
}

@Composable
private fun OpenSourceLongTextCard(
    title: String,
    text: String
) {
    SettingsSectionCard(
        title = title,
        leadingIcon = Icons.Rounded.Description,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun OpenSourceLicensesScreen(
    onOpenLicenseDetail: (String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(context) {
        OpenSourceLicensesRepository.initialize(context)
    }
    val uiState by OpenSourceLicensesRepository.state.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
    ) {
        when (val state = uiState) {
            OpenSourceLicensesUiState.Loading -> {
                item { OpenSourceLoadingCard() }
            }

            is OpenSourceLicensesUiState.Error -> {
                item { OpenSourceErrorCard(message = state.message) }
            }

            is OpenSourceLicensesUiState.Ready -> {
                item { OpenSourceSummaryHeroCard(summary = state.bundle.notices.summary) }
                items(
                    items = state.bundle.notices.groups,
                    key = { it.id }
                ) { group ->
                    OpenSourceGroupCard(
                        group = group,
                        onClick = { onOpenLicenseDetail(group.id) }
                    )
                }
            }
        }
        item {
            com.lele.llmonitor.ui.components.NavigationBarBottomInsetSpacer()
        }
    }
}

@Composable
fun OpenSourceLicenseDetailScreen(
    groupId: String
) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(context) {
        OpenSourceLicensesRepository.initialize(context)
    }
    val uiState by OpenSourceLicensesRepository.state.collectAsState()
    val decodedGroupId = Uri.decode(groupId)

    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
    ) {
        when (val state = uiState) {
            OpenSourceLicensesUiState.Loading -> {
                item { OpenSourceLoadingCard() }
            }

            is OpenSourceLicensesUiState.Error -> {
                item { OpenSourceErrorCard(message = state.message) }
            }

            is OpenSourceLicensesUiState.Ready -> {
                val group = state.bundle.notices.groups.firstOrNull { it.id == decodedGroupId }
                if (group == null) {
                    item {
                        OpenSourceErrorCard(message = com.lele.llmonitor.i18n.l10n("未找到对应的许可信息。"))
                    }
                } else {
                    item {
                        SettingsHeroCard(
                            title = group.displayName,
                            subtitle = "",
                            chips = buildOpenSourceDetailChips(group),
                            icon = Icons.Rounded.Description
                        )
                    }
                    item { OpenSourceArtifactsCard(group = group) }
                    item { OpenSourceMetadataCard(group = group) }
                    if (group.licenseText.isNotBlank()) {
                        item {
                            OpenSourceLongTextCard(
                                title = com.lele.llmonitor.i18n.l10n("许可证全文"),
                                text = group.licenseText
                            )
                        }
                    }
                    if (group.noticeText.isNotBlank()) {
                        item {
                            OpenSourceLongTextCard(
                                title = com.lele.llmonitor.i18n.l10n("NOTICE 声明"),
                                text = group.noticeText
                            )
                        }
                    }
                    if (group.licenseText.isBlank() && group.noticeText.isBlank()) {
                        item {
                            OpenSourceLongTextCard(
                                title = com.lele.llmonitor.i18n.l10n("许可材料"),
                                text = group.detailText.ifBlank {
                                    state.bundle.thirdPartyNoticesText.ifBlank { com.lele.llmonitor.i18n.l10n("暂无可展示的许可文本。") }
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            com.lele.llmonitor.ui.components.NavigationBarBottomInsetSpacer()
        }
    }
}
