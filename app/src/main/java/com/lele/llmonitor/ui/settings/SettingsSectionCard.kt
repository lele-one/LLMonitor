package com.lele.llmonitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lele.llmonitor.ui.theme.AppCorners
import com.lele.llmonitor.ui.theme.AppShapes

@Composable
internal fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accentColor = settingsAccentColor()
    val titleAlpha = if (enabled) 1f else 0.65f
    val accentAlpha = if (enabled) 1f else 0.6f
    val contentAlpha = if (enabled) 1f else 0.72f
    SettingsContentCard(
        modifier = modifier,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                Surface(
                    color = accentColor.copy(alpha = 0.10f),
                    shape = AppShapes.g2(AppCorners.sm)
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = accentAlpha),
                        modifier = Modifier
                            .padding(10.dp)
                            .size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = titleAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = titleAlpha),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (titleTrailing != null) {
                titleTrailing()
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.55f else 0.4f),
            modifier = Modifier.padding(top = 12.dp, bottom = 14.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha),
            content = content
        )
    }
}
