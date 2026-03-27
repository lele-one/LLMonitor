package com.lele.llmonitor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lele.llmonitor.data.SettingsManager
import com.lele.llmonitor.ui.theme.AppCorners
import com.lele.llmonitor.ui.theme.AppShapes
import com.lele.llmonitor.ui.theme.llClassCardBorderColor
import com.lele.llmonitor.ui.theme.llClassCardContainerColor

@Composable
fun HomeCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val homeCardOpacity by SettingsManager.homeCardOpacity
    val resolvedOpacity = homeCardOpacity.coerceIn(0f, 1f)
    val cardShape = AppShapes.g2(AppCorners.lg)
    val containerColor = llClassCardContainerColor(
        accentColor = accentColor,
        enabled = enabled,
        opacity = resolvedOpacity
    )

    Card(
        modifier = modifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.dp,
            color = llClassCardBorderColor(accentColor = accentColor, enabled = enabled)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (containerColor.alpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            color = containerColor
                        )
                )
            }
            content()
        }
    }
}
