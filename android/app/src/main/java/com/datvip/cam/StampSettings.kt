package com.datvip.cam

import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object StampSettings {
    const val DEFAULT_HEX = "0E1A24"
    const val DEFAULT_OPACITY = 0.60f

    fun background(preferences: SharedPreferences): Int {
        val hex = preferences.getString("stampBackgroundHex", DEFAULT_HEX) ?: DEFAULT_HEX
        val rgb = runCatching { Color.parseColor("#$hex") }.getOrDefault(0xFF0E1A24.toInt())
        val opacity = preferences.getFloat("stampBackgroundOpacity", DEFAULT_OPACITY).coerceIn(0.4f, 1f)
        return Color.argb((opacity * 255).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }
}

data class NotePreset(val id: String, val name: String, val values: Map<String, String>)

object NotePresets {
    val fields = listOf("proj" to "Nhiệm vụ / công việc", "note" to "Ghi chú") +
        (1..4).flatMap { listOf("f${it}l" to "Nhãn $it (VD: Tên)", "f${it}v" to "Nội dung $it (VD: Đạt)") }

    fun load(preferences: SharedPreferences): List<NotePreset> = runCatching {
        val json = JSONArray(preferences.getString("noteTemplates", "[]"))
        (0 until minOf(json.length(), 10)).map { index ->
            val item = json.getJSONObject(index)
            val values = item.getJSONObject("values")
            NotePreset(item.optString("id", UUID.randomUUID().toString()), item.getString("name"),
                fields.associate { it.first to values.optString(it.first) })
        }
    }.getOrDefault(emptyList())

    fun save(preferences: SharedPreferences, presets: List<NotePreset>) {
        val data = JSONArray()
        presets.take(10).forEach { item -> data.put(JSONObject().put("id", item.id)
            .put("name", item.name).put("values", JSONObject(item.values))) }
        preferences.edit().putString("noteTemplates", data.toString()).apply()
    }
}
