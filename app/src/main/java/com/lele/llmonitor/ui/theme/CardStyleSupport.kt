package com.lele.llmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

@Composable
fun llClassCardContainerColor(
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    opacity: Float = 1f
): Color {
    val baseColor = accentColor.copy(alpha = 0.08f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    val resolved = if (enabled) {
        baseColor
    } else {
        lerp(baseColor, MaterialTheme.colorScheme.surfaceContainerLow, 0.52f)
    }
    return resolved.copy(alpha = (resolved.alpha * opacity.coerceIn(0f, 1f)).coerceIn(0f, 1f))
}

@Composable
fun llClassCardBorderColor(
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
): Color {
    return if (enabled) {
        accentColor.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
    }
}

@Composable
fun llClassSectionTitleColor(
    enabled: Boolean = true
): Color {
    val color = MaterialTheme.colorScheme.onSurface
    return if (enabled) color else color.copy(alpha = 0.65f)
}

@Composable
fun llClassSectionMetaColor(
    enabled: Boolean = true
): Color {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return if (enabled) color else color.copy(alpha = 0.65f)
}
