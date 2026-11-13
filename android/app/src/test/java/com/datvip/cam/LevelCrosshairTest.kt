package com.datvip.cam

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LevelCrosshairTest {
    @Test fun gravityProjectionForBothLandscapeDirectionsAndPortrait() {
        for (orientation in listOf(0, 90, -90, 180)) for (residual in listOf(-20.0, 0.0, 20.0)) {
            val filter = CameraOrientation()
            val radians = Math.toRadians(orientation + residual)
            repeat(60) { filter.update(sin(radians), cos(radians), it * 20L) }
            assertEquals(orientation, filter.angle)
            assertEquals(residual.toFloat(), filter.tilt!!, .01f)
        }
    }

    @Test fun singularProjectionInvalidatesImmediatelyAndRecovers() {
        val filter = CameraOrientation()
        filter.update(0.0, 1.0, 0)
        filter.update(.01, .01, 20)
        assertNull(filter.tilt)
        filter.update(.1, .99, 40)
        assertNotNull(filter.tilt)
        filter.update(Double.NaN, 1.0, 60)
        assertNull(filter.tilt)
    }

    @Test fun smoothingHasConsistentResponseAtDifferentSensorRates() {
        fun response(dt: Long): Float {
            val filter = CameraOrientation()
            filter.update(0.0, 1.0, 0)
            var time = dt
            while (time <= 200) {
                filter.update(sin(.2), cos(.2), time); time += dt
            }
            return filter.tilt!!
        }
        assertEquals(response(10), response(50), .05f)
        assertTrue(response(20) > 10f)
    }

    @Test fun renderedAxesArePerpendicularCenteredAndScaleWithPreview() {
        for ((width, height) in listOf(360 to 480, 480 to 360, 1080 to 1440, 1440 to 1080)) {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            LevelCrosshair.draw(Canvas(bmp), width, height, 20f)
            val unit = min(width, height) / 360f
            for (degrees in listOf(20.0, 110.0, 200.0, 290.0)) {
                val rad = Math.toRadians(degrees)
                val x = (width / 2 + cos(rad) * 80 * unit).roundToInt()
                val y = (height / 2 + sin(rad) * 80 * unit).roundToInt()
                assertTrue("Axis absent at $degrees in ${width}x$height", Color.alpha(bmp.getPixel(x, y)) > 0)
            }
            assertEquals(0, bmp.getPixel((width*.2).toInt(), (height*.2).toInt()))
        }
    }

    @Test fun exportRetainsCaptureMetadataAndNormalizesGeometry() {
        val source = File.createTempFile("level-source", ".jpg")
        val target = File.createTempFile("level-target", ".jpg")
        try {
            for (file in listOf(source, target)) file.outputStream().use {
                Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:13 13:00:00")
                setAttribute(ExifInterface.TAG_MAKE, "CameraHT-test")
                setAttribute(ExifInterface.TAG_ORIENTATION, "6")
                setLatLong(10.75, 106.67); saveAttributes()
            }
            ExifInterface(target).apply { PhotoMetadata.copy(source.readBytes(), this, 60, 80); saveAttributes() }
            val result = ExifInterface(target)
            assertEquals("CameraHT-test", result.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals("2026:11:13 13:00:00", result.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertEquals(1, result.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            assertEquals(60, result.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0))
            assertEquals(10.75, result.latLong!![0], .00001)
            assertEquals(106.67, result.latLong!![1], .00001)
        } finally { source.delete(); target.delete() }
    }
}
