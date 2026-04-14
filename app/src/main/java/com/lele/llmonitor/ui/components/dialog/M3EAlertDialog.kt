package com.lele.llmonitor.ui.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lele.llmonitor.ui.theme.AppCorners
import com.lele.llmonitor.ui.theme.AppShapes

object M3EDialogTokens {
    val MinWidth: Dp = 280.dp
    val MaxWidth: Dp = 560.dp
    val Corner: Dp = AppCorners.xxl
    val TonalElevation: Dp = 6.dp
    val ContentPadding: Dp = 24.dp
    val SectionGap: Dp = 16.dp
    val ButtonGap: Dp = 8.dp
}

@Composable
private fun M3EDialogSurface(
    modifier: Modifier = Modifier,
    minWidth: Dp = M3EDialogTokens.MinWidth,
    maxWidth: Dp = M3EDialogTokens.MaxWidth,
    contentPadding: PaddingValues = PaddingValues(M3EDialogTokens.ContentPadding),
    sectionGap: Dp = M3EDialogTokens.SectionGap,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.widthIn(
            min = minWidth,
            max = maxWidth
        ),
        shape = AppShapes.g2(M3EDialogTokens.Corner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = M3EDialogTokens.TonalElevation
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(sectionGap),
            content = content
        )
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun M3EAlertDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    minWidth: Dp = M3EDialogTokens.MinWidth,
    maxWidth: Dp = M3EDialogTokens.MaxWidth,
    contentPadding: PaddingValues = PaddingValues(M3EDialogTokens.ContentPadding),
    sectionGap: Dp = M3EDialogTokens.SectionGap,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = true),
    dismissAfterExitAnimation: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        M3EDialogSurface(
            minWidth = minWidth,
            maxWidth = maxWidth,
            contentPadding = contentPadding,
            sectionGap = sectionGap
        ) {
            if (icon != null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    icon()
                }
            }
            if (title != null) {
                title()
            }
            if (text != null) {
                text()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (dismissButton != null) {
                    dismissButton()
                    Spacer(modifier = Modifier.width(M3EDialogTokens.ButtonGap))
                }
                confirmButton()
            }
        }
    }
}
