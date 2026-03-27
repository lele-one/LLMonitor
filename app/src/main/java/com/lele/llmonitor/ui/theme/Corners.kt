package com.lele.llmonitor.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.sin

object AppCorners {
    val micro = 4.dp
    val tiny = 6.dp
    val xxs = 10.dp
    val xs = 12.dp
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
    val xl = 28.dp
    val xxl = 32.dp
}

@Immutable
private class G2CornerShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val exponent: Float = 2.8f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        with(density) {
            val radiusScale = 1.22f
            val rawTopLeft = (if (layoutDirection == LayoutDirection.Ltr) topStart.toPx() else topEnd.toPx()) * radiusScale
            val rawTopRight = (if (layoutDirection == LayoutDirection.Ltr) topEnd.toPx() else topStart.toPx()) * radiusScale
            val rawBottomRight = (if (layoutDirection == LayoutDirection.Ltr) bottomEnd.toPx() else bottomStart.toPx()) * radiusScale
            val rawBottomLeft = (if (layoutDirection == LayoutDirection.Ltr) bottomStart.toPx() else bottomEnd.toPx()) * radiusScale

            val halfMin = min(size.width, size.height) / 2f
            val tl = rawTopLeft.coerceIn(0f, halfMin)
            val tr = rawTopRight.coerceIn(0f, halfMin)
            val br = rawBottomRight.coerceIn(0f, halfMin)
            val bl = rawBottomLeft.coerceIn(0f, halfMin)

            val path = Path()
            val w = size.width
            val h = size.height
            val steps = 16

            fun p(c: Double): Float = max(0.0, c).pow(2.0 / exponent.toDouble()).toFloat()

            path.moveTo(tl, 0f)
            path.lineTo(w - tr, 0f)

            if (tr > 0f) {
                for (i in 0..steps) {
                    val t = (PI / 2.0) - (i.toDouble() / steps) * (PI / 2.0)
                    val u = p(cos(t))
                    val v = p(sin(t))
                    val x = (w - tr) + tr * u
                    val y = tr - tr * v
                    path.lineTo(x, y)
                }
            } else {
                path.lineTo(w, 0f)
            }

            path.lineTo(w, h - br)
            if (br > 0f) {
                for (i in 0..steps) {
                    val t = (i.toDouble() / steps) * (PI / 2.0)
                    val u = p(cos(t))
                    val v = p(sin(t))
                    val x = (w - br) + br * u
                    val y = (h - br) + br * v
                    path.lineTo(x, y)
                }
            } else {
                path.lineTo(w, h)
            }

            path.lineTo(bl, h)
            if (bl > 0f) {
                for (i in 0..steps) {
                    val t = (PI / 2.0) - (i.toDouble() / steps) * (PI / 2.0)
                    val u = p(cos(t))
                    val v = p(sin(t))
                    val x = bl - bl * u
                    val y = (h - bl) + bl * v
                    path.lineTo(x, y)
                }
            } else {
                path.lineTo(0f, h)
            }

            path.lineTo(0f, tl)
            if (tl > 0f) {
                for (i in 0..steps) {
                    val t = (i.toDouble() / steps) * (PI / 2.0)
                    val u = p(cos(t))
                    val v = p(sin(t))
                    val x = tl - tl * u
                    val y = tl - tl * v
                    path.lineTo(x, y)
                }
            } else {
                path.lineTo(0f, 0f)
            }

            path.close()
            return Outline.Generic(path)
        }
    }
}

object AppShapes {
    fun g2(all: Dp): Shape = G2CornerShape(all, all, all, all)
    fun g2(topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp): Shape =
        G2CornerShape(topStart, topEnd, bottomEnd, bottomStart)

    val micro: Shape get() = g2(AppCorners.micro)
    val tiny: Shape get() = g2(AppCorners.tiny)
    val xxs: Shape get() = g2(AppCorners.xxs)
    val xs: Shape get() = g2(AppCorners.xs)
    val sm: Shape get() = g2(AppCorners.sm)
    val md: Shape get() = g2(AppCorners.md)
    val lg: Shape get() = g2(AppCorners.lg)
    val xl: Shape get() = g2(AppCorners.xl)
    val xxl: Shape get() = g2(AppCorners.xxl)
}
