package com.datvip.cam

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.view.View
import kotlin.math.*

data class LevelStyle(val color: Int = Color.YELLOW, val length: Float = 1f) {
    val fraction get() = if (length.isFinite()) length.coerceIn(.15f, 1f) else 1f
    companion object {
        fun read(p: SharedPreferences) = LevelStyle(p.getInt("levelColor", Color.YELLOW), p.getFloat("levelLength", 1f))
    }
}

object CompassMath {
    val mountains = listOf("Tý", "Quý", "Sửu", "Cấn", "Dần", "Giáp", "Mão", "Ất", "Thìn", "Tốn", "Tỵ", "Bính", "Ngọ", "Đinh", "Mùi", "Khôn", "Thân", "Canh", "Dậu", "Tân", "Tuất", "Càn", "Hợi", "Nhâm")
    fun normalize(value: Float): Float = ((value % 360) + 360) % 360
    fun mountain(value: Float) = mountains[((normalize(value) + 7.5f) / 15).toInt() % 24]
    fun smooth(old: Float, next: Float, alpha: Float = .18f): Float {
        val delta = ((next - old + 540) % 360) - 180
        return normalize(old + delta * alpha)
    }
    fun recordedTilt(tilt: Float, currentAngle: Int, initialAngle: Int) = normalize(tilt+currentAngle-initialAngle+180)-180
}

class BubbleLevelView(context: Context) : View(context) {
    var gravityX = 0f
    var gravityY = 0f
    var available = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun sample(rawX: Float, rawY: Float, angle: Int) {
        val a = Math.toRadians(angle.toDouble())
        gravityX = (rawX * cos(a) - rawY * sin(a)).toFloat().coerceIn(-1f, 1f)
        gravityY = (rawX * sin(a) + rawY * cos(a)).toFloat().coerceIn(-1f, 1f)
        available = gravityX.isFinite() && gravityY.isFinite()
        invalidate()
    }
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val r = 45 * d; val cx = width / 2f; val cy = height * .24f
        p.style = Paint.Style.FILL; p.color = 0xB0102019.toInt(); c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = d
        c.drawCircle(cx, cy, r, p); c.drawCircle(cx, cy, 12*d, p)
        c.drawLine(cx-r, cy, cx+r, cy, p); c.drawLine(cx, cy-r, cx, cy+r, p)
        if (available) {
            val scale = (r-10*d) / max(1f, hypot(gravityX*3, gravityY*3))
            p.style = Paint.Style.FILL; p.color = if (hypot(gravityX,gravityY) < .035f) Color.GREEN else Color.YELLOW
            c.drawCircle(cx+gravityX*3*scale, cy-gravityY*3*scale, 9*d, p)
        }
        p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textSize = 11*d; p.textAlign = Paint.Align.CENTER
        val label = if (available) String.format(java.util.Locale.US,"X %.1f°  Y %.1f°",Math.toDegrees(asin(gravityX.toDouble())),Math.toDegrees(asin(gravityY.toDouble()))) else Language.text(context,"Chưa có dữ liệu cảm biến")
        c.drawText(label,cx,cy+r+16*d,p)
    }
}

class CompassDialView(context: Context) : View(context) {
    var overMap = false
        set(value) { field = value; invalidate() }
    var heading: Float? = null
        set(value) { field = value; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val r = min(width,height)*.46f; val cx = width/2f; val cy = height/2f
        c.save(); c.translate(cx,cy)
        p.clearShadowLayer()
        p.style=Paint.Style.FILL; p.color=if(overMap) 0x18111C24 else 0xCD111C24.toInt(); c.drawCircle(0f,0f,r,p)
        if(overMap) p.setShadowLayer(3f,0f,0f,Color.BLACK)
        p.style=Paint.Style.STROKE; p.color=Color.YELLOW; p.strokeWidth=2f
        c.drawCircle(0f,0f,r,p); c.drawCircle(0f,0f,r*.70f,p)
        p.textAlign=Paint.Align.CENTER; p.textSize=r*.085f; p.typeface=Typeface.DEFAULT_BOLD
        for(i in 0 until 120) {
            val a=Math.toRadians(i*3.0-90); val inner=r*(if(i%5==0).90f else .95f)
            c.drawLine((cos(a)*inner).toFloat(),(sin(a)*inner).toFloat(),(cos(a)*r).toFloat(),(sin(a)*r).toFloat(),p)
        }
        p.style=Paint.Style.FILL
        CompassMath.mountains.forEachIndexed { i,label ->
            val a=Math.toRadians(i*15.0-90)
            p.color=if(i%6==0) Color.RED else Color.YELLOW
            c.drawText(label,(cos(a)*r*.80).toFloat(),(sin(a)*r*.80).toFloat()+p.textSize*.35f,p)
        }
        p.color=Color.WHITE; p.textSize=r*.12f
        listOf("B","Đ","N","T").forEachIndexed{i,label ->
            val a=Math.toRadians(i*90.0-90); c.drawText(label,(cos(a)*r*.58).toFloat(),(sin(a)*r*.58).toFloat()+p.textSize*.35f,p)
        }
        heading?.let { h ->
            c.save(); c.rotate(h)
            p.color=Color.RED; val path=Path().apply { moveTo(0f,-r*.60f); lineTo(-r*.07f,0f); lineTo(r*.07f,0f); close() }; c.drawPath(path,p)
            p.color=Color.WHITE; c.rotate(180f); c.drawPath(path,p); c.restore()
        }
        p.color=Color.YELLOW; c.drawCircle(0f,0f,r*.045f,p)
        c.restore()
    }
}
