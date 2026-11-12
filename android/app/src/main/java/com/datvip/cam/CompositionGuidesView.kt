package com.datvip.cam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs

/** Preview only: deliberately separate from the photo/video stamp renderer. */
class CompositionGuidesView(context: Context) : View(context) {
    var tilt: Float? = null
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val d = resources.displayMetrics.density
        paint.color = 0x73FFFFFF; paint.strokeWidth = .7f * d
        for (i in 1..2) {
            canvas.drawLine(w * i / 3, 0f, w * i / 3, h, paint)
            canvas.drawLine(0f, h * i / 3, w, h * i / 3, paint)
        }
        val value = tilt ?: return
        canvas.save(); canvas.translate(w / 2, h / 2)
        paint.color = 0x99FFFFFF.toInt(); paint.strokeWidth = d
        canvas.drawLine(-45*d, 0f, 45*d, 0f, paint)
        canvas.rotate(-value)
        paint.color = if (abs(value) <= 2) Color.GREEN else Color.YELLOW
        paint.strokeWidth = 3*d
        canvas.drawLine(-42*d, 0f, -7*d, 0f, paint)
        canvas.drawLine(7*d, 0f, 42*d, 0f, paint)
        canvas.drawCircle(0f, 0f, 3.5f*d, paint)
        canvas.restore()
    }
}
