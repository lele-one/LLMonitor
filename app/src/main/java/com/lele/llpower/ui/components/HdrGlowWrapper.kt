package com.lele.llpower.ui.components

import android.graphics.ColorSpace
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

@Composable
fun HdrGlowWrapper(
    visible: Boolean,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            // Breath once: 0 -> 1 -> 0
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
            )
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1200, easing = LinearOutSlowInEasing)
            )
            onAnimationEnd()
        }
    }

    Box(
        modifier = modifier
            .drawBehind {
                if (animatable.value > 0f) {
                    val alpha = animatable.value
                    val blurRadius = 8.dp.toPx()
                    val corner = 12.dp.toPx()
                    
                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val linearExtSrgb = ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB)
                                // 单层 HDR 发光：在当前方案基础上再提升 2x
                                setColor(android.graphics.Color.pack(48f, 48f, 64f, alpha * 0.9f, linearExtSrgb))
                            } else {
                                color = android.graphics.Color.argb(
                                    (alpha * 255).toInt(), 255, 255, 255
                                )
                            }
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = (8.0 / 3.0).dp.toPx()
                            maskFilter = android.graphics.BlurMaskFilter(
                                blurRadius,
                                android.graphics.BlurMaskFilter.Blur.NORMAL
                            )
                        }
                        canvas.nativeCanvas.drawRoundRect(
                            0f, 0f, size.width, size.height,
                            corner, corner,
                            paint
                        )
                    }
                }
            }
    ) {
        content()
    }
}
