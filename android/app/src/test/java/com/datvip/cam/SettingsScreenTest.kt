package com.datvip.cam

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @Test fun allFourRequestedFeaturesAreReachableAndSaveChanges() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val context = controller.get()
        val prefs = context.getSharedPreferences("test-settings-ui", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val selectedLogo = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val screen = StampSettingsScreen(context, prefs, logo = { selectedLogo })
        val root = screen.createContent()
        context.setContentView(root)
        for (shortcut in listOf("Khung", "Nền", "Logo", "Mẫu")) assertNotNull(root.findViewWithTag<Button>("shortcut-$shortcut"))
        val size = root.findViewWithTag<SeekBar>("stampDisplaySize")
        assertNotNull(size); size.progress = 0
        assertEquals(.5f, prefs.getFloat("stampDisplaySize", 0f), .001f)
        root.findViewWithTag<Button>("transparent-background").performClick()
        assertEquals(0f, prefs.getFloat("stampBackgroundOpacity", 1f), .001f)
        val logo = root.findViewWithTag<ImageView>("logo-image")
        assertNotNull(logo.drawable)
        root.findViewWithTag<Switch>("stampLogoRemoveWhiteBackground").isChecked = true
        assertTrue(prefs.getBoolean("stampLogoRemoveWhiteBackground", false))
        assertEquals(8, NotePresets.load(prefs).size)
        val traffic = NotePresets.load(prefs).first { it.name == "Giao thông" }
        root.findViewWithTag<Button>("preset:${traffic.id}").performClick()
        assertEquals("Trực chốt giao thông", root.findViewWithTag<EditText>("proj").text.toString())
        assertEquals("Địa điểm", prefs.getString("f1l", ""))
        assertEquals("Ngã ba đường Hoàng Cầm - đường số 5", root.findViewWithTag<EditText>("f1v").text.toString())
        root.findViewWithTag<EditText>("f1v").setText("Địa điểm chỉnh sửa")
        assertEquals("Địa điểm chỉnh sửa", prefs.getString("f1v", ""))
        val another = StampSettingsScreen(context, prefs, logo = { selectedLogo }).createContent()
        assertEquals("Địa điểm chỉnh sửa", another.findViewWithTag<EditText>("f1v").text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun settingsGroupsProduceVisualEvidence() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val context = controller.get()
        val prefs = context.getSharedPreferences("test-settings-images", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val logo = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.default_antt_logo,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })
        val root = StampSettingsScreen(context, prefs, logo = { logo }).createContent()
        context.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 1920)
        val directory = File("build/reports/settings-images").apply { mkdirs() }
        val image = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        File(directory, "settings-top.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        for ((label, name) in listOf("Khung" to "frame", "Logo" to "logo")) {
            root.findViewWithTag<Button>("shortcut-$label").performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            root.draw(Canvas(image))
            File(directory, "settings-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        controller.pause().stop().destroy()
    }
}
