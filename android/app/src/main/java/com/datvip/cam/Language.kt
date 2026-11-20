package com.datvip.cam

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.json.JSONObject
import java.util.Locale

/** Translates system labels only. User text and saved templates never pass through this catalog. */
object Language {
    private var english: JSONObject? = null
    fun isVietnamese(context: Context) = context.resources.configuration.locales[0].language == "vi"
    fun locale(context: Context) = if (isVietnamese(context)) Locale("vi", "VN") else Locale.ENGLISH
    fun text(context: Context, source: String, vararg values: Any?): String {
        val template = if (isVietnamese(context)) source else {
            val catalog = english ?: JSONObject(context.assets.open("ui-en.json").bufferedReader().use { it.readText() }).also { english = it }
            catalog.optString(source, source)
        }
        return Regex("\\{(\\d+)\\}").replace(template) { match -> values.getOrNull(match.groupValues[1].toInt())?.toString() ?: match.value }
    }
    fun choose(tag: String) {
        require(tag in listOf("", "vi", "en"))
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
    fun direction(context: Context, degrees: Float): String {
        val names = if (isVietnamese(context)) listOf("B", "ĐB", "Đ", "ĐN", "N", "TN", "T", "TB") else listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[Math.round(degrees / 45f) % 8]
    }
    fun name(context: Context) = if (isVietnamese(context)) "Camera HT – Hiện Trường" else "Field Camera – GPS Stamp"
}
