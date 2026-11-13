package com.datvip.cam

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

/** Same normalized geometry for the logical preview and the cropped, upright JPEG. */
object LevelCrosshair {
    fun draw(canvas: Canvas, width: Int, height: Int, tilt: Float) {
        if (!tilt.isFinite() || width <= 0 || height <= 0) return
        val unit = min(width, height) / 360f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.translate(width / 2f, height / 2f)
        canvas.save()
        canvas.rotate(tilt)
        val span = hypot(width.toDouble(), height.toDouble()).toFloat()
        for (outline in listOf(true, false)) {
            paint.color = if (outline) Color.BLACK else Color.YELLOW
            paint.strokeWidth = (if (outline) 3f else 1.2f) * unit
            canvas.drawLine(-span, 0f, span, 0f, paint)
            canvas.drawLine(0f, -span, 0f, span, paint)
        }
        canvas.restore()
        paint.textSize = 13f * unit
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val text = String.format(Locale.US, "%+.1f°", tilt)
        val left = 12f * unit; val baseline = -12f * unit
        paint.color = 0xCC000000.toInt()
        canvas.drawRoundRect(left - 4*unit, baseline - 15*unit,
            left + paint.measureText(text) + 4*unit, baseline + 4*unit, 3*unit, 3*unit, paint)
        paint.color = Color.YELLOW
        canvas.drawText(text, left, baseline, paint)
        canvas.restore()
    }
}
