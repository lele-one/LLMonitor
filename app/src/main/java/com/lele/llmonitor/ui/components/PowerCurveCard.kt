package com.lele.llmonitor.ui.components

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
import com.lele.llmonitor.data.BatteryEngine
import com.lele.llmonitor.data.local.BatteryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private const val POWER_MIN_DISPLAY_W = -400f
private const val POWER_MAX_DISPLAY_W = 400f
private const val MAX_POWER_GRID_LINES = 40

private data class PowerCurveRenderSnapshot(
    val points: List<PointData>,
    val latestHistoryTimestamp: Long?,
    val startTime: Long,
    val endTime: Long
)

@Composable
fun PowerCurveCard(
    history: List<BatteryEntity>,
    recordIntervalMs: Long,
    invert: Boolean = false,
    isDualCell: Boolean = false,
    animationEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val density = LocalDensity.current

    // 1. 定义时间窗口 (8小时)
    val initialNow = remember { System.currentTimeMillis() }
    val totalDurationMs = 8 * 60 * 60 * 1000L

    val initialRenderSnapshot = remember(initialNow, totalDurationMs) {
        PowerCurveRenderSnapshot(
            points = emptyList(),
            latestHistoryTimestamp = null,
            startTime = initialNow - totalDurationMs,
            endTime = initialNow
        )
    }

    // 2. 筛选和转换数据点（常驻协程，避免每个新点都重启 produceState）
    val renderSnapshot by produceState(
        initialValue = initialRenderSnapshot,
        invert,
        isDualCell
    ) {
        snapshotFlow {
            Triple(
                history.size,
                history.firstOrNull()?.timestamp ?: 0L,
                history.lastOrNull()?.timestamp ?: 0L
            )
        }
            .distinctUntilChanged()
            .conflate()
            .collect {
                val historySnapshot = history.toList()
                val latestHistoryTimestamp = historySnapshot.lastOrNull()?.timestamp
                // 锁定初始时间锚点，避免历史补齐期间窗口终点反复跳动。
                val endTime = maxOf(initialNow, latestHistoryTimestamp ?: Long.MIN_VALUE)
                val startTime = endTime - totalDurationMs
                val points = withContext(Dispatchers.Default) {
                    historySnapshot.asSequence()
                        .filter { it.timestamp >= startTime }
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
                        .filter { it.power.isFinite() }
                        .toList()
                }
                value = PowerCurveRenderSnapshot(
                    points = points,
                    latestHistoryTimestamp = latestHistoryTimestamp,
                    startTime = startTime,
                    endTime = endTime
                )
            }
    }
    val points = renderSnapshot.points
    val latestHistoryTimestamp = renderSnapshot.latestHistoryTimestamp
    val startTime = renderSnapshot.startTime
    val endTime = renderSnapshot.endTime

    // 3. 预计算 Y 轴范围 (提取出来供 Y 轴和 Chart 共享)
    val minData = remember(points) {
        points.minOfOrNull { it.power }
            ?.coerceIn(POWER_MIN_DISPLAY_W, POWER_MAX_DISPLAY_W) ?: 0f
    }
    val maxData = remember(points) {
        points.maxOfOrNull { it.power }
            ?.coerceIn(POWER_MIN_DISPLAY_W, POWER_MAX_DISPLAY_W) ?: 0f
    }
    
    val yMin = remember(minData) { (kotlin.math.floor(minData / 10.0) * 10).toFloat() }
    val yMax = remember(maxData, yMin) { 
        var max = (kotlin.math.ceil(maxData / 10.0) * 10).toFloat()
        if (max <= yMin) max = yMin + 10f
        max
    }
    
    val validRange = (yMax - yMin).coerceAtLeast(1f) // 避免除以 0
    val gridCount = (validRange / 10).toInt().coerceIn(1, MAX_POWER_GRID_LINES)


    val primaryColor = MaterialTheme.colorScheme.primary
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
    var autoReboundEnabled by remember { mutableStateOf(false) }
    
    // 判断是否处于com.lele.llmonitor.i18n.l10n("跟随最新")状态 (允许少量误差)
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

    // 冷启动历史未就绪前禁用动画，待历史加载完成后再恢复自动回弹动画。
    LaunchedEffect(hasInitialAligned, animationEnabled) {
        autoReboundEnabled = hasInitialAligned && animationEnabled
    }

    // 自动滚动：仅在“当前已贴边”时跟随最新，不做额外回弹动画。
    LaunchedEffect(latestHistoryTimestamp, scrollState.maxValue) {
        if (hasInitialAligned && scrollState.maxValue > 0 && !scrollState.isScrollInProgress && isAtBottom) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // 保留“3秒自动回弹到底部”，但只在延迟开启后生效。
    @OptIn(FlowPreview::class)
    LaunchedEffect(scrollState, autoReboundEnabled) {
        if (!autoReboundEnabled) return@LaunchedEffect
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .distinctUntilChanged()
            .debounce(3000L)
            .collect { (value, max) ->
                if (max > 0 && value < max - 2 && !scrollState.isScrollInProgress) {
                    scrollState.animateScrollTo(max)
                }
            }
    }

    HomeCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = com.lele.llmonitor.i18n.l10n("实时功率曲线"), 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val p1Power = p1.power.coerceIn(POWER_MIN_DISPLAY_W, POWER_MAX_DISPLAY_W)
    val y1 = graphHeight * (1 - (p1Power - yMin) / validRange)
    val x2 = getX(p2.timestamp)
    val p2Power = p2.power.coerceIn(POWER_MIN_DISPLAY_W, POWER_MAX_DISPLAY_W)
    val y2 = graphHeight * (1 - (p2Power - yMin) / validRange)

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
        val power = p.power.coerceIn(POWER_MIN_DISPLAY_W, POWER_MAX_DISPLAY_W)
        ys[i] = graphHeight * (1 - (power - yMin) / validRange)
    }

    // 1. 计算区间宽度与割线斜率
    val hs = FloatArray(size - 1)
    val ds = FloatArray(size - 1)
    for (i in 0 until size - 1) {
        val dx = xs[i + 1] - xs[i]
        hs[i] = dx.coerceAtLeast(0.001f)
        ds[i] = (ys[i + 1] - ys[i]) / hs[i]
    }

    // 2. 计算点切线 (Fritsch-Carlson 单调三次插值，抑制尖峰过冲)
    val tangents = FloatArray(size)
    tangents[0] = ds[0]
    tangents[size - 1] = ds[size - 2]

    for (i in 1 until size - 1) {
        val dPrev = ds[i - 1]
        val dNext = ds[i]
        if (dPrev * dNext <= 0f) {
            tangents[i] = 0f
        } else {
            // 加权谐均值，较大斜率差时更稳定
            val w1 = 2f * hs[i] + hs[i - 1]
            val w2 = hs[i] + 2f * hs[i - 1]
            tangents[i] = (w1 + w2) / (w1 / dPrev + w2 / dNext)
        }
    }

    // 3. 斜率限制器：确保每段单调，避免峰值“忽长忽短”的过冲
    for (i in 0 until size - 1) {
        val d = ds[i]
        if (kotlin.math.abs(d) < 1e-6f) {
            tangents[i] = 0f
            tangents[i + 1] = 0f
        } else {
            val a = tangents[i] / d
            val b = tangents[i + 1] / d
            val norm = kotlin.math.sqrt(a * a + b * b)
            if (norm > 3f) {
                val scale = 3f / norm
                tangents[i] = scale * a * d
                tangents[i + 1] = scale * b * d
            }
        }
    }

    // 4. 绘制三阶贝塞尔段
    path.moveTo(xs[0], ys[0])
    for (i in 0 until size - 1) {
        val dx = hs[i]
        val cx1 = xs[i] + dx / 3f
        val cy1 = ys[i] + tangents[i] * dx / 3f
        val cx2 = xs[i + 1] - dx / 3f
        val cy2 = ys[i + 1] - tangents[i + 1] * dx / 3f
        
        path.cubicTo(cx1, cy1, cx2, cy2, xs[i + 1], ys[i + 1])
    }
}
