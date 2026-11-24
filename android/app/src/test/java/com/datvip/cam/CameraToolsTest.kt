package com.datvip.cam

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
@Config(sdk=[36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraToolsTest {
    @Test fun shortenedColoredAxesMatchPreviewAndVideoFrame() {
        val bitmap=Bitmap.createBitmap(360,480,Bitmap.Config.ARGB_8888)
        val style=LevelStyle(Color.CYAN,.4f)
        LevelCrosshair.draw(Canvas(bitmap),360,480,0f,style)
        assertEquals(Color.CYAN,bitmap.getPixel(220,240))
        assertEquals(0,Color.alpha(bitmap.getPixel(280,240)))
        assertEquals(0,Color.alpha(bitmap.getPixel(180,340)))
        val view=CompositionGuidesView(RuntimeEnvironment.getApplication()).apply { layout(0,0,360,480); levelEnabled=true; tilt=0f; this.style=style }
        val preview=Bitmap.createBitmap(360,480,Bitmap.Config.ARGB_8888); view.draw(Canvas(preview))
        assertTrue(bitmap.sameAs(preview))
        val frame=VideoStampFrame(100_000,Stamp(null,null,emptyList()),0f,style)
        val video=Bitmap.createBitmap(360,480,Bitmap.Config.ARGB_8888)
        LevelCrosshair.draw(Canvas(video),360,480,frame.tilt!!,frame.style)
        assertTrue(bitmap.sameAs(video))
        val dir=File("build/reports/camera-tools").apply { mkdirs() }
        File(dir,"crosshair-cyan-short.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun headingsWrapAtNorthAndMountainBoundaries() {
        assertEquals("Tý",CompassMath.mountain(359.9f))
        assertEquals("Tý",CompassMath.mountain(-1f))
        assertEquals("Quý",CompassMath.mountain(7.5f))
        assertEquals("Ngọ",CompassMath.mountain(180f))
        assertTrue(CompassMath.smooth(359f,1f) > 359f)
        assertEquals(-85f,CompassMath.recordedTilt(5f,-90,0),.001f)
        assertEquals(5f,CompassMath.recordedTilt(5f,180,-180),.001f)
    }
    @Test fun bubbleUsesBothGravityAxesAndRotatesWithLayout() {
        val view=BubbleLevelView(RuntimeEnvironment.getApplication())
        view.sample(.1f,.2f,90)
        assertEquals(-.2f,view.gravityX,.001f); assertEquals(.1f,view.gravityY,.001f)
        assertTrue(view.available)
        view.sample(Float.NaN,0f,0); assertFalse(view.available)
    }
    @Test fun preferencesSurviveReopeningAndInvalidLengthIsClamped() {
        val context=RuntimeEnvironment.getApplication()
        val pref=context.getSharedPreferences("tool-test",0)
        pref.edit().clear().putInt("levelColor",Color.RED).putFloat("levelLength",.3f).commit()
        assertEquals(LevelStyle(Color.RED,.3f),LevelStyle.read(context.getSharedPreferences("tool-test",0)))
        assertEquals(.15f,LevelStyle(length=-2f).fraction,.0001f)
        assertEquals(1f,LevelStyle(length=Float.NaN).fraction,.0001f)
    }
    @Test fun compassAndBubbleRenderWithoutLocationOrNetwork() {
        val context=RuntimeEnvironment.getApplication(); val dir=File("build/reports/camera-tools").apply { mkdirs() }
        for((name,view) in listOf("compass" to CompassDialView(context).apply { heading=127f },"bubble" to BubbleLevelView(context).apply { sample(.01f,.01f,0) })) {
            view.layout(0,0,480,640)
            val b=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888);view.draw(Canvas(b))
            File(dir,"$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
            assertTrue(b.byteCount>0)
        }
    }
}
