package com.lele.llmonitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lele.llmonitor.BuildConfig
import com.lele.llmonitor.R

private fun resolveAboutBuildChipLabel(): String {
    return when (BuildConfig.BUILD_TYPE) {
        "debug" -> "Debug"
        "release" -> "Release"
        "releaseSlim" -> "Release Slim"
        "benchmark" -> "Benchmark"
        else -> BuildConfig.BUILD_TYPE
    }
}

@Composable
private fun AboutHeroCard() {
    SettingsHeroCard(
        title = "LLMonitor",
        subtitle = com.lele.llmonitor.i18n.l10n("风在耳边"),
        chips = listOf(
            "Version ${BuildConfig.VERSION_NAME}",
            resolveAboutBuildChipLabel()
        ),
        iconPainter = painterResource(R.drawable.ic_llmonitor_exact),
        iconSize = 24.dp
    )
}

@Composable
private fun AboutLicenseEntryCard(
    onClick: () -> Unit
) {
    SettingsEntryCard(
        title = com.lele.llmonitor.i18n.l10n("开源许可"),
        icon = Icons.Rounded.Verified,
        onClick = onClick
    )
}

@Composable
internal fun AboutInfoRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AboutInfoPanel() {
    SettingsSectionCard(
        title = com.lele.llmonitor.i18n.l10n("基本信息"),
        leadingIcon = Icons.Rounded.Info,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AboutInfoRow(label = com.lele.llmonitor.i18n.l10n("应用名称"), value = "LLMonitor")
            AboutInfoRow(label = com.lele.llmonitor.i18n.l10n("作者"), value = "LELE")
            AboutInfoRow(label = com.lele.llmonitor.i18n.l10n("版本"), value = BuildConfig.VERSION_NAME)
        }
    }
}

@Composable
fun AboutScreen(
    onOpenOpenSource: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { AboutHeroCard() }
        item { AboutInfoPanel() }
        item { AboutLicenseEntryCard(onClick = onOpenOpenSource) }
        item { com.lele.llmonitor.ui.components.NavigationBarBottomInsetSpacer() }
    }
}
