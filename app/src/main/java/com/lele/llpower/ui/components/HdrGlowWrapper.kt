package com.lele.llpower.ui.components

import android.graphics.ColorSpace
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
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
    
    // HDR Colors with high gain
    val hdrColor = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val linearExtSrgb = ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB)
            // A bright blue-ish white glow: R=4.0, G=4.0, B=8.0 (HDR Gain)
            Color(android.graphics.Color.pack(4f, 4f, 8f, 1f, linearExtSrgb))
        } else {
            Color(0xFF8080FF) // Fallback for older versions
        }
    }

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
                    
                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val linearExtSrgb = ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB)
                                // Gain values remain high for HDR effect
                                val packedColor = android.graphics.Color.pack(
                                    12f, 12f, 16f, alpha * 0.9f, linearExtSrgb
                                )
                                setColor(packedColor)
                            } else {
                                color = android.graphics.Color.argb(
                                    (alpha * 0.9f * 255).toInt(), 160, 160, 255
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
                            12.dp.toPx(), 12.dp.toPx(),
                            paint
                        )
                    }
                }
            }
    ) {
        content()
    }
}
