package com.lele.llmonitor.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLineAutoShrink: Boolean = false,
    singleLineAutoShrinkReferenceText: String? = null,
    sourceLines: List<String> = emptyList()
) {
    HomeCard(
        modifier = modifier.height(FIXED_SMALL_CARD_HEIGHT_DP),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (singleLineAutoShrink) {
                AutoShrinkSingleLineText(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    referenceText = singleLineAutoShrinkReferenceText
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium, // headlineMedium is bold in our Type.kt
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (sourceLines.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                sourceLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val FIXED_SMALL_CARD_HEIGHT_DP = 77.7.dp

@Composable
private fun AutoShrinkSingleLineText(
    text: String,
    style: TextStyle,
    color: Color,
    referenceText: String? = null,
    minFontSize: TextUnit = 12.sp,
    shrinkFactor: Float = 0.9f
) {
    val initialSize = if (style.fontSize != TextUnit.Unspecified) style.fontSize else 24.sp
    val sizingText = referenceText ?: text
    var currentSize by remember(sizingText, initialSize) { mutableStateOf(initialSize) }
    val reservedLineHeight = if (style.lineHeight != TextUnit.Unspecified) {
        style.lineHeight
    } else {
        initialSize * 1.2f
    }
    val reservedHeight = with(LocalDensity.current) { reservedLineHeight.toDp() }

    if (referenceText != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(reservedHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            // 使用“参考最长文本”做测量，得到固定字号，再用同字号渲染实际文本。
            Text(
                text = sizingText,
                style = style.copy(fontSize = currentSize),
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer(alpha = 0f),
                onTextLayout = { layoutResult ->
                    if (layoutResult.hasVisualOverflow && currentSize > minFontSize) {
                        val nextSize = (currentSize.value * shrinkFactor).sp
                        currentSize = if (nextSize > minFontSize) nextSize else minFontSize
                    }
                }
            )
            Text(
                text = text,
                style = style.copy(fontSize = currentSize),
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(reservedHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                style = style.copy(fontSize = currentSize),
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { layoutResult ->
                    if (layoutResult.hasVisualOverflow && currentSize > minFontSize) {
                        val nextSize = (currentSize.value * shrinkFactor).sp
                        currentSize = if (nextSize > minFontSize) nextSize else minFontSize
                    }
                }
            )
        }
    }
}
