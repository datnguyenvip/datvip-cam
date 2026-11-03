package com.datvip.cam

import android.content.Context
import android.content.res.Configuration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LanguageTest {
    private fun context(tag: String): Context {
        val base = org.robolectric.RuntimeEnvironment.getApplication()
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
    }
    @Test fun englishStampRetainsOriginalUserContentAndNumberFormatting() {
        val en = context("en")
        val raw = "Địa điểm {1}: Linh Xuân / 10.879036, 106.772466"
        assertEquals("Using template: $raw", Language.text(en, "Đang dùng mẫu: {0}", raw))
        assertEquals("Coordinates", Language.text(en, "Tọa độ"))
        assertEquals("Field Camera", Language.name(en))
        assertEquals("S", Language.direction(en, 180f))
        val stamp = Stamp("09:32:54", "Monday", listOf("Task: $raw", "\u200BLocation: $raw", "Verification code: ABCD"))
        assertEquals(StampRole.PROJECT, StampContent.entries(stamp).first().role)
        assertEquals(raw, StampContent.entries(stamp).first().text.removePrefix("Task: "))
        assertEquals(StampRole.PLACE, StampContent.entries(stamp)[1].role)
    }
    @Test fun vietnameseAndUnsupportedSystemLanguagesChooseExpectedInterface() {
        val vi = context("vi")
        assertEquals("Camera HT", Language.name(vi))
        assertEquals("Chụp ảnh", Language.text(vi, "Chụp ảnh"))
        assertEquals("N", Language.direction(vi, 180f))
        assertEquals("Take Photo", Language.text(context("fr"), "Chụp ảnh"))
    }
}
