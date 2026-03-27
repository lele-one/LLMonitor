package com.lele.llmonitor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    scheduleName: String,
    weekStatusText: String,
    wallpaperModeEnabled: Boolean,
    onAboutClick: () -> Unit = {},
    onClassroomQueryClick: () -> Unit = {},
    onScheduleSelectClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    showClassroomQueryAction: Boolean = false,
    showScheduleSelectAction: Boolean = false,
    modifier: Modifier = Modifier
) {
    val topBarColors = if (wallpaperModeEnabled) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    } else {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LLMonitor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { onAboutClick() }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Spacer(modifier = Modifier.height(5.5.dp))
                        Text(
                            text = "beta",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.End
                        )
                    }
                }
                Text(
                    text = if (scheduleName.isBlank()) weekStatusText else "$scheduleName $weekStatusText",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        },
        colors = topBarColors,
        actions = {
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = Color.Transparent
                )
            }
            if (showClassroomQueryAction) {
                IconButton(onClick = onClassroomQueryClick) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = com.lele.llmonitor.i18n.l10n("教室课表查询")
                    )
                }
            } else {
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color.Transparent
                    )
                }
            }
            if (showScheduleSelectAction) {
                IconButton(onClick = onScheduleSelectClick) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = com.lele.llmonitor.i18n.l10n("课表选择")
                    )
                }
            } else {
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = Color.Transparent
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = com.lele.llmonitor.i18n.l10n("设置")
                )
            }
        }
    )
}
