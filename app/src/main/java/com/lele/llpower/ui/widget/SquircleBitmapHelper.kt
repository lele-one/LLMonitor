package com.lele.llpower.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Helper to generate a G2 Squircle bitmap for Glance widgets.
 * Matches the 'SmoothCornerShape' logic used in the main app.
 */
object SquircleBitmapHelper {
    
    fun createSquircleBitmap(
        context: Context,
        widthDp: Dp,
        heightDp: Dp,
        radiusDp: Dp,
        color: Int
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (widthDp.value * density).toInt().coerceAtLeast(1)
        val h = (heightDp.value * density).toInt().coerceAtLeast(1)
        val r = radiusDp.value * density
        
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        
        val path = Path()
        val s = r * 0.5f // smoothing stretch factor (Figma 60% approx)
        
        path.moveTo(r + s, 0f)
        path.lineTo(w - (r + s), 0f)
        
        // Top Right
        path.cubicTo(w - r * 0.5f, 0f, w.toFloat(), r * 0.5f, w.toFloat(), r + s)
        path.lineTo(w.toFloat(), h - (r + s))
        
        // Bottom Right
        path.cubicTo(w.toFloat(), h - r * 0.5f, w - r * 0.5f, h.toFloat(), w - (r + s), h.toFloat())
        path.lineTo(r + s, h.toFloat())
        
        // Bottom Left
        path.cubicTo(r * 0.5f, h.toFloat(), 0f, h - r * 0.5f, 0f, h - (r + s))
        path.lineTo(0f, r + s)
        
        // Top Left
        path.cubicTo(0f, r * 0.5f, r * 0.5f, 0f, r + s, 0f)
        
        path.close()
        canvas.drawPath(path, paint)
        
        return bitmap
    }
}
