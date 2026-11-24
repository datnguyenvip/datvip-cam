package com.datvip.cam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraToolsLayoutTest {
    @Test fun cameraToolsFitAndCanBeToggledWithoutCameraPermission() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).create().start().visible()
        val activity=controller.get()
        val root=activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY))
        root.layout(0,0,720,1280)
        fun descendants(view:View):List<View> = listOf(view) + if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        val labels=listOf("Bọt thủy","La bàn phong thủy","Tùy chỉnh thước","Thước cân bằng","Zoom ảnh và video")
        for(label in labels) {
            val match=descendants(root).firstOrNull { it.contentDescription==Language.text(activity,label) }
            assertNotNull("Missing control: $label",match)
            assertTrue(match!!.width>0 && match.height>0)
            val position=IntArray(2);match.getLocationOnScreen(position)
            assertTrue("Clipped control: $label",position[0]>=0 && position[1]>=0 && position[0]+match.width<=720 && position[1]+match.height<=1280)
        }
        val toggle=descendants(root).first { it.contentDescription==Language.text(activity,"Bọt thủy") }
        toggle.performClick()
        val bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
        val directory=File("build/reports/camera-tools").apply { mkdirs() }
        File(directory,"camera-controls.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        controller.stop().destroy()
    }
}
