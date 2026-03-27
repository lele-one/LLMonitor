package com.lele.llmonitor.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lele.llmonitor.ui.theme.AppCorners
import com.lele.llmonitor.ui.theme.AppShapes

private data class HaloLayerSpec(
    val strokeWidthScale: Float,
    val blurRadiusScale: Float,
    val alphaScale: Float
)

private val ActiveRingDrawOutset: Dp = 14.dp
private val ActiveRingOuterStrokeWidth: Dp = 2.4.dp
private val ActiveRingInnerStrokeWidth: Dp = 1.2.dp
private const val ActiveRingOuterAlpha: Float = 0.42f
private const val ActiveRingRotationCycleMs: Long = 2000L
private val DefaultActiveBorderColors = listOf(
    Color(0xFF4285F4),
    Color(0xFFEA4335),
    Color(0xFFFBBC04),
    Color(0xFF34A853)
)
private val ActiveRingHaloSpecs = listOf(
    HaloLayerSpec(
        strokeWidthScale = 1.58f,
        blurRadiusScale = 3.8f,
        alphaScale = 0.12f
    ),
    HaloLayerSpec(
        strokeWidthScale = 1.34f,
        blurRadiusScale = 2.6f,
        alphaScale = 0.22f
    ),
    HaloLayerSpec(
        strokeWidthScale = 1.16f,
        blurRadiusScale = 1.5f,
        alphaScale = 0.36f
    ),
    HaloLayerSpec(
        strokeWidthScale = 1.04f,
        blurRadiusScale = 0.7f,
        alphaScale = 0.54f
    )
)

@Composable
fun rememberActiveRingRotationState(): State<Float> {
    return produceState(
        initialValue = (SystemClock.elapsedRealtime() % ActiveRingRotationCycleMs)
            .toFloat() * 360f / ActiveRingRotationCycleMs
    ) {
        while (true) {
            withFrameNanos {
                val elapsedInCycle = SystemClock.elapsedRealtime() % ActiveRingRotationCycleMs
                value = elapsedInCycle.toFloat() * 360f / ActiveRingRotationCycleMs
            }
        }
    }
}

@Composable
fun HdrGlowWrapper(
    visible: Boolean,
    modifier: Modifier = Modifier,
    cardShape: Shape = AppShapes.g2(AppCorners.lg),
    activeBorderColors: List<Color> = DefaultActiveBorderColors,
    rotationDegreesState: State<Float>,
    content: @Composable () -> Unit
) {
    val resolvedRotationDegrees by rotationDegreesState
    val density = LocalDensity.current
    val drawOutsetPx = with(density) { ActiveRingDrawOutset.roundToPx() }
    val ringAlpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 320 else 260, easing = LinearEasing),
        label = "active_ring_alpha"
    )

    Layout(
        modifier = modifier,
        content = {
            Box { content() }
            Box(
                modifier = Modifier.drawWithCache {
                    val safeBorderColors = if (activeBorderColors.isEmpty()) {
                        listOf(Color.White, Color.White)
                    } else {
                        activeBorderColors
                    }
                    val gradientColors = if (safeBorderColors.first() == safeBorderColors.last()) {
                        safeBorderColors
                    } else {
                        safeBorderColors + safeBorderColors.first()
                    }

                    val ringInsetPx = drawOutsetPx.toFloat()
                    val ringWidth = (size.width - ringInsetPx * 2f).coerceAtLeast(1f)
                    val ringHeight = (size.height - ringInsetPx * 2f).coerceAtLeast(1f)
                    val centerX = ringInsetPx + ringWidth / 2f
                    val centerY = ringInsetPx + ringHeight / 2f

                    val shader = android.graphics.SweepGradient(
                        centerX,
                        centerY,
                        gradientColors.map { it.toArgb() }.toIntArray(),
                        null
                    )
                    val outline = cardShape.createOutline(
                        size = androidx.compose.ui.geometry.Size(ringWidth, ringHeight),
                        layoutDirection = layoutDirection,
                        density = this
                    )
                    val ringPath = Path().apply {
                        when (outline) {
                            is Outline.Generic -> addPath(outline.path)
                            is Outline.Rounded -> addRoundRect(outline.roundRect)
                            is Outline.Rectangle -> addRect(outline.rect)
                        }
                    }
                    val shiftedRingPath = Path().apply {
                        addPath(ringPath, Offset(ringInsetPx, ringInsetPx))
                    }

                    val outerStrokeWidthPx = ActiveRingOuterStrokeWidth.toPx()
                    val innerStrokeWidthPx = ActiveRingInnerStrokeWidth.toPx()
                    val shaderBrush = ShaderBrush(shader)
                    val androidOutlinePath = shiftedRingPath.asAndroidPath()
                    val baseHaloAlphas = ActiveRingHaloSpecs.map { spec ->
                        (255f * ActiveRingOuterAlpha * spec.alphaScale).toInt().coerceIn(0, 255)
                    }
                    val shaderMatrix = android.graphics.Matrix()
                    val haloPaints = ActiveRingHaloSpecs.map { spec ->
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeWidth = outerStrokeWidthPx * spec.strokeWidthScale
                            setShader(shader)
                            maskFilter = android.graphics.BlurMaskFilter(
                                outerStrokeWidthPx * spec.blurRadiusScale,
                                android.graphics.BlurMaskFilter.Blur.NORMAL
                            )
                        }
                    }

                    onDrawBehind {
                        val fadeAlpha = ringAlpha.value.coerceIn(0f, 1f)
                        if (fadeAlpha <= 0.001f) return@onDrawBehind

                        shaderMatrix.reset()
                        shaderMatrix.setRotate(resolvedRotationDegrees, centerX, centerY)
                        shader.setLocalMatrix(shaderMatrix)
                        haloPaints.forEachIndexed { index, paint ->
                            paint.alpha = (baseHaloAlphas[index] * fadeAlpha).toInt().coerceIn(0, 255)
                        }

                        drawIntoCanvas { canvas ->
                            haloPaints.forEach { paint ->
                                canvas.nativeCanvas.drawPath(androidOutlinePath, paint)
                            }
                        }
                        drawPath(
                            path = shiftedRingPath,
                            style = Stroke(width = innerStrokeWidthPx),
                            brush = shaderBrush,
                            alpha = fadeAlpha
                        )
                    }
                }
            )
        }
    ) { measurables, constraints ->
        val contentPlaceable = measurables[0].measure(constraints)
        val overlayWidth = (contentPlaceable.width + drawOutsetPx * 2).coerceAtLeast(1)
        val overlayHeight = (contentPlaceable.height + drawOutsetPx * 2).coerceAtLeast(1)
        val overlayPlaceable = measurables[1].measure(
            Constraints.fixed(overlayWidth, overlayHeight)
        )

        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
            overlayPlaceable.place(-drawOutsetPx, -drawOutsetPx)
        }
    }
}
