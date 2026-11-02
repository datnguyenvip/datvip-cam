package com.datvip.cam

import android.content.Context
import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil

data class StampAppearance(val background: Int, val size: Float = .75f, val logoOpacity: Float = 1f)
enum class StampRole { PROJECT, PLACE, TIME, DATE, ADDRESS, CUSTOM, DETAIL }
data class StampEntry(val text: String, val role: StampRole)

object StampContent {
    fun normalizedLabel(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('đ', 'd')
        .trim().replace(Regex("\\s+"), " ")
    fun isPlace(value: String) = ':' in value && normalizedLabel(value.substringBefore(':')) == "dia diem"
    fun entries(stamp: Stamp): List<StampEntry> {
        val project = stamp.rows.filter { it.startsWith("Nhiệm vụ:") }
        val custom = stamp.rows.filter { it.startsWith('\u200B') }.map { it.substring(1) }
        val addresses = stamp.rows.filter { it.startsWith('\u200C') }.map { it.substring(1) }
        return project.map { StampEntry(it, StampRole.PROJECT) } +
            custom.filter(::isPlace).map { StampEntry(it, StampRole.PLACE) } +
            listOfNotNull(stamp.head?.let { StampEntry(it, StampRole.TIME) }, stamp.date?.let { StampEntry(it, StampRole.DATE) }) +
            addresses.map { StampEntry(it, StampRole.ADDRESS) } +
            custom.filterNot(::isPlace).map { StampEntry(it, StampRole.CUSTOM) } +
            stamp.rows.filterNot { it.startsWith("Nhiệm vụ:") || it.startsWith('\u200B') || it.startsWith('\u200C') }
                .map { StampEntry(it, StampRole.DETAIL) }
    }
}

object AndroidStampRenderer {
    data class Block(val entry: StampEntry, val layout: StaticLayout, val x: Float, val y: Float)
    data class Plan(val rect: RectF, val logo: RectF?, val blocks: List<Block>, val corner: Boolean, val unit: Float)
    private val orange = Color.rgb(255, 164, 92)
    private val yellow = Color.rgb(255, 210, 63)

    /** Design coordinates: shorter image edge is always 1200; output size cannot change wrapping. */
    fun plan(width: Float, height: Float, stamp: Stamp, logo: Bitmap?, style: StampAppearance): Plan {
        val unit = 12f * style.size
        val margin = 36f
        val available = width - 2 * margin
        fun measure(panelWidth: Float, corner: Boolean, scale: Float): Plan {
            val pad = (if (corner) 1.2f else 2.8f) * unit
            val bar = 1.4f * unit
            val gap = (if (corner) .7f else 1.2f) * unit * scale
            val left = bar + pad
            val textWidth = (panelWidth - left - pad).coerceAtLeast(1f)
            val blocks = mutableListOf<Block>()
            fun textPaint(entry: StampEntry) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                val points = when (entry.role) {
                    StampRole.PROJECT -> if (corner) 3f else 3.8f
                    StampRole.TIME -> if (corner) 3.8f else 7.5f
                    StampRole.DATE -> if (corner) 2.6f else 3.4f
                    StampRole.PLACE, StampRole.ADDRESS -> if (corner) 3f else 3.4f
                    StampRole.CUSTOM -> if (corner) 2.9f else 3.3f
                    StampRole.DETAIL -> if (entry.text.startsWith("Mã xác thực")) 2.6f else if (corner) 2.8f else 3.2f
                }
                textSize = (points * unit * scale).coerceAtLeast(1f)
                typeface = if (entry.role in listOf(StampRole.PROJECT, StampRole.PLACE, StampRole.TIME, StampRole.ADDRESS)) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                color = when (entry.role) { StampRole.PROJECT -> orange; StampRole.TIME -> yellow; else -> if (entry.text.startsWith("Mã xác thực")) Color.LTGRAY else Color.WHITE }
            }
            fun layout(entry: StampEntry, width: Float) = StaticLayout.Builder.obtain(entry.text, 0, entry.text.length,
                textPaint(entry), width.toInt().coerceAtLeast(1)).setIncludePad(false).setBreakStrategy(0).setHyphenationFrequency(0).build()
            fun append(entry: StampEntry, x: Float, y: Float, width: Float): Float {
                val text = layout(entry, width)
                blocks.add(Block(entry, text, x, y))
                return text.height.toFloat()
            }
            val logoRect = logo?.takeIf { style.logoOpacity > 0 }?.let {
                val fit = minOf((if (corner) 6 else 9) * unit * scale / it.height,
                    (if (corner) 9 else 24) * unit * scale / it.width)
                RectF(left, pad, left + it.width * fit, pad + it.height * fit)
            }
            val headerX = left + (logoRect?.let { it.width() + gap } ?: 0f)
            val headerWidth = (textWidth - headerX + left).coerceAtLeast(1f)
            val entries = StampContent.entries(stamp)
            var y = pad
            entries.filter { it.role == StampRole.PROJECT || it.role == StampRole.PLACE }.forEach {
                y += append(it, headerX, y, headerWidth) + gap
            }
            y = maxOf(y, logoRect?.bottom ?: pad)
            if (y > pad) y += gap
            val body = entries.filterNot { it.role == StampRole.PROJECT || it.role == StampRole.PLACE }
            var index = 0
            while (index < body.size) {
                val entry = body[index]
                if (entry.role == StampRole.TIME && body.getOrNull(index + 1)?.role == StampRole.DATE) {
                    val date = body[index + 1]
                    val timeWidth = ceil(textPaint(entry).measureText(entry.text)) + unit
                    val dateWidth = textWidth - timeWidth - gap
                    if (dateWidth >= textWidth * .35f) {
                        val th = layout(entry, timeWidth).height.toFloat()
                        val dh = layout(date, dateWidth).height.toFloat()
                        val stacked = layout(entry, textWidth).height + gap + layout(date, textWidth).height
                        if (maxOf(th, dh) < stacked) {
                            append(entry, left, y, timeWidth)
                            append(date, left + timeWidth + gap, y + maxOf(0f, (th - dh) / 2), dateWidth)
                            y += maxOf(th, dh) + gap; index += 2; continue
                        }
                    }
                }
                y += append(entry, left, y, textWidth) + gap
                index++
            }
            return Plan(RectF(0f, 0f, panelWidth, y + pad), logoRect, blocks, corner, unit)
        }
        var result = measure(available / 2, true, 1f)
        if (result.rect.height() > (height - 2 * margin) * .4f) {
            var scale = 1f
            result = measure(available, false, scale)
            while (result.rect.height() > height - 2 * margin && scale > .15f) {
                scale *= .9f; result = measure(available, false, scale)
            }
        }
        val x = margin; val y = height - margin - result.rect.height()
        return result.copy(rect = RectF(result.rect).apply { offset(x, y) },
            logo = result.logo?.let { RectF(it).apply { offset(x, y) } }, blocks = result.blocks.map { it.copy(x = it.x + x, y = it.y + y) })
    }

    fun draw(canvas: Canvas, width: Int, height: Int, stamp: Stamp, logo: Bitmap?, style: StampAppearance, watermark: String = "") {
        if (width <= 0 || height <= 0) return
        val factor = minOf(width, height) / 1200f
        val w = width / factor; val h = height / factor
        canvas.save(); canvas.scale(factor, factor)
        if (watermark.isNotEmpty()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFFFFF; textSize = 48f; typeface = Typeface.DEFAULT_BOLD }
            val tw = paint.measureText(watermark) + 144f; val dd = maxOf(w, h)
            canvas.save(); canvas.translate(w / 2, h / 2); canvas.rotate(-30f)
            var y = -dd; var row = 0
            while (y < dd) { var x = -dd + if (row++ % 2 == 1) tw / 2 else 0f; while (x < dd) { canvas.drawText(watermark, x, y, paint); x += tw }; y += 192f }
            canvas.restore()
        }
        val p = plan(w, h, stamp, logo, style)
        if (p.blocks.isEmpty() && p.logo == null) { canvas.restore(); return }
        val path = Path().apply { addRoundRect(p.rect, p.unit * 1.6f, p.unit * 1.6f, Path.Direction.CW) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.background }
        canvas.drawPath(path, paint)
        canvas.save(); canvas.clipPath(path)
        paint.color = Color.argb(Color.alpha(style.background), 255, 107, 26)
        canvas.drawRect(p.rect.left, p.rect.top, p.rect.left + p.unit * 1.4f, p.rect.bottom, paint)
        if (logo != null && p.logo != null) {
            paint.color = Color.WHITE; paint.alpha = (style.logoOpacity * 255).toInt(); paint.isFilterBitmap = true
            canvas.drawBitmap(logo, null, p.logo, paint)
        }
        p.blocks.forEach { block -> canvas.save(); canvas.translate(block.x, block.y); block.layout.draw(canvas); canvas.restore() }
        canvas.restore(); canvas.restore()
    }
}

class StampOverlayView(context: Context) : View(context) {
    var stamp = Stamp(null, null, emptyList())
    var logo: Bitmap? = null
    var appearance = StampAppearance(Color.TRANSPARENT)
    var watermark = ""
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); AndroidStampRenderer.draw(canvas, width, height, stamp, logo, appearance, watermark) }
}

object LogoProcessing {
    fun trim(bitmap: Bitmap): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val bounds = LogoAlphaBounds.find(bitmap.width, bitmap.height, pixels) ?: return bitmap
        return Bitmap.createBitmap(bitmap, bounds[0], bounds[1], bounds[2], bounds[3])
    }
    fun removeWhite(bitmap: Bitmap): Bitmap {
        val scale = minOf(1f, 1024f / maxOf(bitmap.width, bitmap.height))
        val source = if (scale < 1) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val w = source.width; val h = source.height
        val pixels = IntArray(w * h); source.getPixels(pixels, 0, w, 0, 0, w, h)
        val visited = BooleanArray(pixels.size); val queue = IntArray(pixels.size)
        var end = 0; var cursor = 0
        fun enqueue(i: Int) {
            if (visited[i]) return
            visited[i] = true; val c = pixels[i]
            if (Color.alpha(c) == 0 || minOf(Color.red(c), Color.green(c), Color.blue(c)) >= 230) queue[end++] = i
        }
        for (x in 0 until w) { enqueue(x); enqueue((h - 1) * w + x) }
        for (y in 0 until h) { enqueue(y * w); enqueue(y * w + w - 1) }
        while (cursor < end) {
            val i = queue[cursor++]; pixels[i] = Color.TRANSPARENT
            if (i % w > 0) enqueue(i - 1)
            if (i % w + 1 < w) enqueue(i + 1)
            if (i / w > 0) enqueue(i - w)
            if (i / w + 1 < h) enqueue(i + w)
        }
        return trim(Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888))
    }
}
