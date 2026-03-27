package com.lele.llmonitor.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun pageSurfaceColor(): Color = MaterialTheme.colorScheme.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pageSurfaceTopAppBarColors(
    containerColor: Color = pageSurfaceColor()
): TopAppBarColors {
    val contentColor = MaterialTheme.colorScheme.onSurface
    return TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = containerColor,
        titleContentColor = contentColor,
        actionIconContentColor = contentColor,
        navigationIconContentColor = contentColor
    )
}
