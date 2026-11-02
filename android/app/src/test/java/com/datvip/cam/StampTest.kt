package com.datvip.cam

import android.content.Context
import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StampTest {
    private fun sample() = Stamp("10:51:00", "Thứ Hai, 02/11/2026 (GMT+7)", listOf(
        "Nhiệm vụ: Trực chốt giao thông", "\u200C175 Đường Số 6, P. Linh Xuân, Thành phố Hồ Chí Minh",
        "\u200BANCS: DatVip – tổ 3", "\u200BĐịa điểm: Ngã ba Hoàng Cầm – đường số 5", "Mã xác thực: 12345678"))
    private fun logo() = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private val style = StampAppearance(0xBF576145.toInt())

    @Test fun normalizeAndOrder() {
        for (label in listOf("Địa điểm", "ĐỊA ĐIỂM", "Dia diem", "  Địa   điểm ", "Địa điểm")) {
            assertTrue(label, StampContent.isPlace("$label: original: value"))
            for (slot in 0..3) {
                val fields = (0..3).map { "\u200B" + if (it == slot) "$label: original: value" else "ANCS: $it" }
                val entries = StampContent.entries(Stamp("12:34:56", "date", listOf("Nhiệm vụ: task", "\u200CGPS address") + fields))
                assertEquals(listOf(StampRole.PROJECT, StampRole.PLACE, StampRole.TIME, StampRole.DATE, StampRole.ADDRESS), entries.take(5).map { it.role })
                assertEquals(1, entries.count { it.role == StampRole.PLACE })
                assertEquals("GPS address", entries.first { it.role == StampRole.ADDRESS }.text)
            }
        }
        assertFalse(StampContent.isPlace("Ghi chú: Địa điểm: elsewhere"))
    }

    @Test fun compactAndDenseLayoutsKeepAllFields() {
        for ((w, h) in listOf(1200f to 1600f, 1600f to 1200f)) {
            val p = AndroidStampRenderer.plan(w, h, sample(), logo(), style)
            assertTrue(p.corner); assertTrue(p.rect.width() <= w / 2); assertTrue(p.rect.height() <= h * .4)
            assertTrue(p.blocks.all { p.rect.contains(it.x, it.y) && p.rect.contains(it.x + it.layout.width, it.y + it.layout.height) })
            for (i in p.blocks.indices) for (j in i + 1 until p.blocks.size) {
                val a = p.blocks[i]; val b = p.blocks[j]
                assertFalse(RectF.intersects(RectF(a.x, a.y, a.x + a.layout.width, a.y + a.layout.height), RectF(b.x, b.y, b.x + b.layout.width, b.y + b.layout.height)))
            }
            val dense = Stamp(sample().head, sample().date, sample().rows + (1..8).map { "\u200BThông tin $it: " + "chi tiết dài ".repeat(30) })
            val expanded = AndroidStampRenderer.plan(w, h, dense, logo(), style)
            assertFalse(expanded.corner); assertTrue(expanded.rect.width() > w * .9f); assertTrue(expanded.rect.top >= 0)
            assertEquals(StampContent.entries(dense).size, expanded.blocks.size)
        }
    }

    @Test fun noPlaceAndNoLogo() {
        val content = Stamp("12:34:56", "date", listOf("Nhiệm vụ: task", "\u200CGPS", "\u200BANCS: team"))
        val p = AndroidStampRenderer.plan(1200f, 1600f, content, null, style)
        assertNull(p.logo); assertEquals(StampRole.PROJECT, p.blocks.first().entry.role)
        assertFalse(p.blocks.any { it.entry.role == StampRole.PLACE })
    }

    @Test fun transparentBackgroundAndIndependentLogo() {
        val image = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val logo = logo(); val appearance = style.copy(background = Color.TRANSPARENT, logoOpacity = .5f)
        AndroidStampRenderer.draw(Canvas(image), 1200, 1600, sample(), logo, appearance)
        val p = AndroidStampRenderer.plan(1200f, 1600f, sample(), logo, appearance)
        assertEquals(0, Color.alpha(image.getPixel(p.rect.centerX().toInt(), (p.rect.bottom - 2).toInt())))
        assertTrue(kotlin.math.abs(Color.alpha(image.getPixel(p.logo!!.centerX().toInt(), p.logo.centerY().toInt())) - 127) <= 1)
        assertNull(AndroidStampRenderer.plan(1200f, 1600f, sample(), logo, appearance.copy(logoOpacity = 0f)).logo)
    }

    @Test fun logoCutoutPreservesEnclosedWhiteAndSource() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            drawColor(Color.rgb(240, 240, 240))
            drawRect(4f, 4f, 16f, 16f, Paint().apply { color = Color.RED })
            drawRect(8f, 8f, 12f, 12f, Paint().apply { color = Color.WHITE })
        }
        val cutout = LogoProcessing.removeWhite(source)
        assertEquals(12, cutout.width); assertEquals(12, cutout.height)
        assertEquals(Color.WHITE, cutout.getPixel(5, 5)); assertEquals(Color.RED, cutout.getPixel(1, 1))
        assertEquals(Color.rgb(240, 240, 240), source.getPixel(0, 0))
    }

    @Test fun defaultTemplatesPreserveExistingEdits() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("test-presets", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val original = NotePreset("mine", "Giao thông", NotePresets.fields.associate { it.first to "edited" })
        NotePresets.save(prefs, listOf(original)); NotePresets.installDefaults(prefs)
        val once = NotePresets.load(prefs)
        assertEquals(8, once.size); assertEquals(original, once.first())
        NotePresets.installDefaults(prefs); assertEquals(once, NotePresets.load(prefs))
        assertEquals(8, NotePresets.builtIn.size)
        assertNotNull(BitmapFactory.decodeResource(RuntimeEnvironment.getApplication().resources, R.drawable.default_antt_logo))
    }

    @Test fun sizeAndOrientationEvidence() {
        val out = File("build/reports/stamp-images").apply { mkdirs() }
        val sourceLogo = BitmapFactory.decodeResource(RuntimeEnvironment.getApplication().resources, R.drawable.default_antt_logo, BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })
        for ((w, h) in listOf(1200 to 1600, 1600 to 1200)) for (size in listOf(.5f, .75f, 1f, 1.2f)) for (transparent in listOf(false, true)) {
            val logo = if (transparent) LogoProcessing.removeWhite(sourceLogo) else sourceLogo
            val appearance = style.copy(size = size, background = if (transparent) Color.TRANSPARENT else style.background, logoOpacity = if (transparent) .5f else 1f)
            val image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            AndroidStampRenderer.draw(Canvas(image), w, h, sample(), logo, appearance)
            File(out, "stamp-${w}-${(size * 100).toInt()}-transparent-$transparent.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val small = AndroidStampRenderer.plan(1200f, 1600f, sample(), logo(), style.copy(size = .5f))
        val large = AndroidStampRenderer.plan(1200f, 1600f, sample(), logo(), style.copy(size = 1f))
        assertTrue(small.rect.height() < large.rect.height())
    }
}
