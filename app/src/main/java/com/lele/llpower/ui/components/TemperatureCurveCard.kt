package com.lele.llpower.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lele.llpower.data.local.BatteryEntity
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class TempPointData(val timestamp: Long, val temperature: Float)

@Composable
fun TemperatureCurveCard(
    history: List<BatteryEntity>,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val density = LocalDensity.current

    // 时间窗口 (8小时)
    val now = System.currentTimeMillis()
    val totalDurationMs = 8 * 60 * 60 * 1000L
    val endTime = now 
    val startTime = endTime - totalDurationMs

    // 筛选和转换数据点
    val points = remember(history.toList(), now) {
        history.filter { it.timestamp >= startTime }
            .sortedBy { it.timestamp }
            .map { TempPointData(timestamp = it.timestamp, temperature = it.temperature) }
    }

    // Y 轴范围 (温度，1°C 为刻度)
    val minData = remember(points) { points.minOfOrNull { it.temperature } ?: 25f }
    val maxData = remember(points) { points.maxOfOrNull { it.temperature } ?: 45f }
    
    val yMin = remember(minData) { kotlin.math.floor(minData).toFloat() }
    val yMax = remember(maxData, yMin) { 
        var max = kotlin.math.ceil(maxData).toFloat()
        if (max <= yMin) max = yMin + 5f
        max
    }
    
    val validRange = (yMax - yMin).coerceAtLeast(1f)
    val gridCount = validRange.toInt().coerceAtLeast(1)

    val primaryColor = MaterialTheme.colorScheme.secondary // 使用 Secondary 颜色以并与百分比卡片呼应
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = onSurfaceVariantColor.toArgb()

    // 计算画布宽度
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val chartAreaWidth = screenWidth - 32.dp - 35.dp 
    val visibleDurationMs = 2 * 60 * 60 * 1000L
    val totalWidthRatio = totalDurationMs.toFloat() / visibleDurationMs
    val canvasWidth = chartAreaWidth * totalWidthRatio

    val scrollState = rememberScrollState()
    val isDragged by scrollState.interactionSource.collectIsDraggedAsState()
    
    val isAtBottom by remember { 
        derivedStateOf { 
            scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 50 
        } 
    }

    LaunchedEffect(points.size, now) {
        if (!isDragged && (isAtBottom || points.isEmpty())) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    LaunchedEffect(isDragged) {
        if (!isDragged) {
            delay(5000L)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "实时温度曲线", 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp)
            ) {
                // Y 轴标签
                Canvas(
                    modifier = Modifier.width(35.dp).fillMaxHeight()
                ) {
                    val height = size.height
                    val bottomLabelHeight = 20.dp.toPx()
                    val graphHeight = height - bottomLabelHeight
                    
                    val textPaint = Paint().apply {
                        color = labelColor
                        textSize = density.run { 10.sp.toPx() }
                        textAlign = Paint.Align.LEFT
                        typeface = Typeface.DEFAULT
                    }

                    for (i in 0..gridCount) {
                        val value = yMin + i * 1f
                        val normalizedY = (value - yMin) / validRange
                        val y = graphHeight * (1 - normalizedY)
                        
                        drawIntoCanvas {
                            it.nativeCanvas.drawText(
                                String.format("%.0f°", value),
                                0f, 
                                y + 4.dp.toPx(),
                                textPaint
                            )
                        }
                    }
                }

                // 图表区域
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(scrollState)
                ) {
                    Canvas(
                        modifier = Modifier.width(canvasWidth).fillMaxHeight()
                    ) {
                        val width = size.width
                        val height = size.height
                        val bottomLabelHeight = 20.dp.toPx()
                        val graphHeight = height - bottomLabelHeight

                        fun getX(timeFn: Long): Float {
                            val progress = (timeFn - startTime).toFloat() / totalDurationMs
                            return width * progress
                        }

                        // Y 网格
                        for (i in 0..gridCount) {
                            val value = yMin + i * 1f
                            val normalizedY = (value - yMin) / validRange
                            val y = graphHeight * (1 - normalizedY)
                            
                            drawLine(
                                color = outlineVariantColor.copy(alpha = 0.5f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = if (i > 0 && i < gridCount) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                            )
                        }

                        // X 轴时间刻度
                        val xLabelPaint = Paint().apply {
                            color = labelColor
                            textSize = density.run { 10.sp.toPx() }
                            textAlign = Paint.Align.CENTER
                            typeface = Typeface.DEFAULT
                        }

                        val cal = Calendar.getInstance()
                        cal.timeInMillis = startTime
                        val currentMinute = cal.get(Calendar.MINUTE)
                        val minutesToNextQuarter = 15 - (currentMinute % 15)
                        cal.add(Calendar.MINUTE, minutesToNextQuarter)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)

                        while (cal.timeInMillis <= endTime) {
                            val t = cal.timeInMillis
                            val x = getX(t)
                            
                            drawLine(
                                color = outlineVariantColor.copy(alpha = 0.3f),
                                start = Offset(x, 0f),
                                end = Offset(x, graphHeight),
                                strokeWidth = 1.dp.toPx()
                            )
                            
                            val timeStr = timeFormat.format(Date(t))
                            drawIntoCanvas {
                                it.nativeCanvas.drawText(timeStr, x, height - 6f, xLabelPaint)
                            }
                            cal.add(Calendar.MINUTE, 15)
                        }

                        // 绘制曲线
                        val gapThreshold = 5 * 60 * 1000L
                        val solidPath = Path()
                        val dashedPath = Path()
                        
                        if (points.isNotEmpty()) {
                            var currentSegmentStart = 0
                            for (i in 0 until points.size - 1) {
                                val p1 = points[i]
                                val p2 = points[i+1]
                                val delta = p2.timestamp - p1.timestamp
                                
                                if (delta > gapThreshold) {
                                    drawTempSegment(solidPath, points, currentSegmentStart, i, yMin, validRange, graphHeight, ::getX)
                                    drawTempDashedBezier(dashedPath, p1, p2, yMin, validRange, graphHeight, ::getX)
                                    currentSegmentStart = i + 1
                                }
                            }
                            drawTempSegment(solidPath, points, currentSegmentStart, points.size - 1, yMin, validRange, graphHeight, ::getX)
                        }

                        drawPath(
                            path = solidPath,
                            color = primaryColor,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        
                        drawPath(
                            path = dashedPath,
                            color = primaryColor.copy(alpha = 0.6f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }
                }
            }
        }
    }
}

fun drawTempDashedBezier(
    path: Path,
    p1: TempPointData,
    p2: TempPointData,
    yMin: Float,
    validRange: Float,
    graphHeight: Float,
    getX: (Long) -> Float
) {
    val x1 = getX(p1.timestamp)
    val y1 = graphHeight * (1 - (p1.temperature - yMin) / validRange)
    val x2 = getX(p2.timestamp)
    val y2 = graphHeight * (1 - (p2.temperature - yMin) / validRange)

    path.moveTo(x1, y1)
    val dx = x2 - x1
    // 虚线连接使用水平切线
    path.cubicTo(x1 + dx / 4f, y1, x2 - dx / 4f, y2, x2, y2)
}

fun drawTempSegment(
    path: Path,
    points: List<TempPointData>,
    startIndex: Int,
    endIndex: Int,
    yMin: Float,
    validRange: Float,
    graphHeight: Float,
    getX: (Long) -> Float
) {
    if (startIndex >= endIndex) return

    val size = endIndex - startIndex + 1
    val xs = FloatArray(size)
    val ys = FloatArray(size)
    for (i in 0 until size) {
        val p = points[startIndex + i]
        xs[i] = getX(p.timestamp)
        ys[i] = graphHeight * (1 - (p.temperature - yMin) / validRange)
    }

    // 1. 计算割线斜率
    val ms = FloatArray(size - 1)
    for (i in 0 until size - 1) {
        val dx = xs[i + 1] - xs[i]
        ms[i] = if (dx <= 0.001f) 0f else (ys[i + 1] - ys[i]) / dx
    }

    // 2. 计算点切线 (Monotone Cubic Spline)
    val tangents = FloatArray(size)
    tangents[0] = ms[0]
    tangents[size - 1] = ms[size - 2]
    
    for (i in 1 until size - 1) {
        val mLabel = ms[i - 1]
        val mNext = ms[i]
        if (mLabel * mNext <= 0f) {
            tangents[i] = 0f
        } else {
            tangents[i] = (mLabel + mNext) / 2f
        }
    }

    // 3. 绘制
    path.moveTo(xs[0], ys[0])
    for (i in 0 until size - 1) {
        val dx = xs[i + 1] - xs[i]
        val cx1 = xs[i] + dx / 3f
        val cy1 = ys[i] + tangents[i] * dx / 3f
        val cx2 = xs[i + 1] - dx / 3f
        val cy2 = ys[i + 1] - tangents[i + 1] * dx / 3f
        
        path.cubicTo(cx1, cy1, cx2, cy2, xs[i + 1], ys[i + 1])
    }
}
