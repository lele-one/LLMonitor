package com.lele.llpower.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.lele.llpower.data.BatteryEngine
import com.lele.llpower.data.local.BatteryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PowerCurveCard(
    history: List<BatteryEntity>,
    recordIntervalMs: Long,
    invert: Boolean = false,
    isDualCell: Boolean = false,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val density = LocalDensity.current

    // 1. 定义时间窗口 (8小时)
    val now = System.currentTimeMillis()
    val totalDurationMs = 8 * 60 * 60 * 1000L
    val endTime = now 
    val startTime = endTime - totalDurationMs

    // 2. 筛选和转换数据点
    val points = remember(history.toList(), now, invert, isDualCell) {
        history.filter { it.timestamp >= startTime }
            .sortedBy { it.timestamp }
            .map {
                val correctedCurrent = BatteryEngine.applyCurrentAdjustments(
                    rawCurrentMa = it.current,
                    invert = invert,
                    isDoubleCell = isDualCell
                )
                PointData(
                    timestamp = it.timestamp,
                    power = it.voltage * (correctedCurrent / 1000f)
                )
            }
    }

    // 3. 预计算 Y 轴范围 (提取出来供 Y 轴和 Chart 共享)
    val minData = remember(points) { points.minOfOrNull { it.power } ?: 0f }
    val maxData = remember(points) { points.maxOfOrNull { it.power } ?: 0f }
    
    val yMin = remember(minData) { (kotlin.math.floor(minData / 10.0) * 10).toFloat() }
    val yMax = remember(maxData, yMin) { 
        var max = (kotlin.math.ceil(maxData / 10.0) * 10).toFloat()
        if (max <= yMin) max = yMin + 10f
        max
    }
    
    val validRange = (yMax - yMin).coerceAtLeast(1f) // 避免除以 0
    val gridCount = (validRange / 10).toInt().coerceAtLeast(1)


    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = onSurfaceVariantColor.toArgb()

    // 计算画布宽度
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    // 扣除 Y 轴宽度 (约 35dp) 和 padding
    val chartAreaWidth = screenWidth - 32.dp - 35.dp 
    val visibleDurationMs = 2 * 60 * 60 * 1000L
    val totalWidthRatio = totalDurationMs.toFloat() / visibleDurationMs
    val canvasWidth = chartAreaWidth * totalWidthRatio

    val scrollState = rememberScrollState()
    var hasInitialAligned by remember { mutableStateOf(false) }
    
    // 判断是否处于"跟随最新"状态 (允许少量误差)
    val isAtBottom by remember { 
        derivedStateOf { 
            scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 50 
        } 
    }

    // 初次布局完成后，先对齐到最新点
    LaunchedEffect(scrollState.maxValue) {
        if (!hasInitialAligned && scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
            hasInitialAligned = true
        }
    }

    // 自动滚动逻辑 1: 数据更新时，在“跟随最新”状态下保持贴边
    LaunchedEffect(points.size, scrollState.maxValue) {
        if (hasInitialAligned && scrollState.maxValue > 0 && !scrollState.isScrollInProgress && isAtBottom) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // 自动滚动逻辑 2: 横向滚动停止 3 秒后自动回到底部（仅触发一次完整动画）
    @OptIn(FlowPreview::class)
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .distinctUntilChanged()
            .debounce(3000L)
            .collect { (value, max) ->
                if (max > 0 && value < max - 2) {
                    scrollState.animateScrollTo(max)
                }
            }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "实时功率曲线", 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))

            // 使用 Row 分离 Y 轴标签和 滚动图表
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                // 左侧：Y 轴标签区域 (固定不动)
                Canvas(
                    modifier = Modifier
                        .width(35.dp)
                        .fillMaxHeight()
                ) {
                    val height = size.height
                    val bottomLabelHeight = 20.dp.toPx()
                    val graphHeight = height - bottomLabelHeight
                    
                    val textPaint = Paint().apply {
                        color = labelColor
                        textSize = density.run { 10.sp.toPx() }
                        textAlign = Paint.Align.LEFT // 左对齐
                        typeface = Typeface.DEFAULT
                    }

                    for (i in 0..gridCount) {
                        val value = yMin + i * 10f
                        val normalizedY = (value - yMin) / validRange
                        val y = graphHeight * (1 - normalizedY)
                        
                        // 绘制标签 (偏移一点点避免贴边)
                        drawIntoCanvas {
                            it.nativeCanvas.drawText(
                                String.format("%.0f", value),
                                0f, 
                                y + 4.dp.toPx(), // 稍微垂直居中
                                textPaint
                            )
                        }
                    }
                }

                // 右侧：可滚动的图表区域
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(scrollState)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(canvasWidth)
                            .fillMaxHeight()
                    ) {
                        val width = size.width
                        val height = size.height
                        val bottomLabelHeight = 20.dp.toPx()
                        val graphHeight = height - bottomLabelHeight

                        fun getX(timeFn: Long): Float {
                            val progress = (timeFn - startTime).toFloat() / totalDurationMs
                            return width * progress
                        }

                        // 绘制 Y 轴网格线 (仅线条，标签在左侧 Canvas)
                        for (i in 0..gridCount) {
                            val value = yMin + i * 10f
                            val normalizedY = (value - yMin) / validRange
                            val y = graphHeight * (1 - normalizedY)
                            
                            // 零轴实线，其他虚线
                            if (kotlin.math.abs(value) < 0.1f) {
                                drawLine(
                                    color = outlineVariantColor.copy(alpha = 0.5f),
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            } else {
                                if (i > 0 && i < gridCount) { // 上下边界可视情况通过
                                    drawLine(
                                        color = outlineVariantColor.copy(alpha = 0.5f),
                                        start = Offset(0f, y),
                                        end = Offset(width, y),
                                        strokeWidth = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                }
                            }
                        }

                        // 绘制 X 轴时间刻度
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
                            
                            // 垂直网格
                            drawLine(
                                color = outlineVariantColor.copy(alpha = 0.3f),
                                start = Offset(x, 0f),
                                end = Offset(x, graphHeight),
                                strokeWidth = 1.dp.toPx()
                            )
                            
                            // 时间标签
                            val timeStr = timeFormat.format(Date(t))
                            drawIntoCanvas {
                                it.nativeCanvas.drawText(
                                    timeStr,
                                    x,
                                    height - 6f,
                                    xLabelPaint
                                )
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
                                    // 绘制前面的实线段
                                    drawSegment(solidPath, points, currentSegmentStart, i, yMin, validRange, graphHeight, ::getX)
                                    
                                    // 绘制断流处的平滑虚线段
                                    drawDashedBezier(dashedPath, p1, p2, yMin, validRange, graphHeight, ::getX)
                                    
                                    currentSegmentStart = i + 1
                                }
                            }
                            // 绘制最后一段实线
                            drawSegment(solidPath, points, currentSegmentStart, points.size - 1, yMin, validRange, graphHeight, ::getX)
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

// Ensure helper classes/functions exist
// data class PointData(val timestamp: Long, val power: Float) // Already defined? Need to re-declare if write_to_file overwrites.
// fun drawSegment(...) // Same.

// I must include the helper code again because write_to_file overwrites everything.
// But wait, the previous file had them at the bottom.
// I should include them in this write.

data class PointData(val timestamp: Long, val power: Float)

fun drawDashedBezier(
    path: Path,
    p1: PointData,
    p2: PointData,
    yMin: Float,
    validRange: Float,
    graphHeight: Float,
    getX: (Long) -> Float
) {
    val x1 = getX(p1.timestamp)
    val y1 = graphHeight * (1 - (p1.power - yMin) / validRange)
    val x2 = getX(p2.timestamp)
    val y2 = graphHeight * (1 - (p2.power - yMin) / validRange)

    path.moveTo(x1, y1)
    val dx = x2 - x1
    // 虚线连接使用简单的水平切线，保持平滑进入和退出
    path.cubicTo(x1 + dx / 4f, y1, x2 - dx / 4f, y2, x2, y2)
}

fun drawSegment(
    path: Path,
    points: List<PointData>,
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
        ys[i] = graphHeight * (1 - (p.power - yMin) / validRange)
    }

    // 1. 计算割线斜率
    val ms = FloatArray(size - 1)
    for (i in 0 until size - 1) {
        val dx = xs[i + 1] - xs[i]
        ms[i] = if (dx <= 0.001f) 0f else (ys[i + 1] - ys[i]) / dx
    }

    // 2. 计算点切线 (使用 Monotone Cubic Spline 算法)
    val tangents = FloatArray(size)
    // 边界点使用简单斜率
    tangents[0] = ms[0]
    tangents[size - 1] = ms[size - 2]
    
    for (i in 1 until size - 1) {
        val mLabel = ms[i - 1]
        val mNext = ms[i]
        if (mLabel * mNext <= 0f) {
            tangents[i] = 0f
        } else {
            // Fritsch-Butland 平均值，增加对极小值的保护
            tangents[i] = (mLabel + mNext) / 2f
        }
    }

    // 3. 绘制三阶贝塞尔段
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
