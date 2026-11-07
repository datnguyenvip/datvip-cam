package com.datvip.cam

import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object StampSettings {
    const val DEFAULT_HEX = "576145"
    const val DEFAULT_OPACITY = 0.75f

    fun appearance(preferences: SharedPreferences) = StampAppearance(
        background(preferences), preferences.getFloat("stampDisplaySize", .75f).coerceIn(.5f, 1.2f),
        preferences.getFloat("stampLogoOpacity", 1f).coerceIn(0f, 1f))

    fun background(preferences: SharedPreferences): Int {
        val hex = preferences.getString("stampBackgroundHex", DEFAULT_HEX) ?: DEFAULT_HEX
        val rgb = runCatching { Color.parseColor("#$hex") }.getOrDefault(0xFF576145.toInt())
        val opacity = preferences.getFloat("stampBackgroundOpacity", DEFAULT_OPACITY).coerceIn(0f, 1f)
        return Color.argb((opacity * 255).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }
}

data class NotePreset(val id: String, val name: String, val values: Map<String, String>)

object NotePresets {
    val fields = listOf("proj" to "Nhiệm vụ / công việc", "note" to "Ghi chú") +
        (1..4).flatMap { listOf("f${it}l" to "Nhãn $it (VD: Tên)", "f${it}v" to "Nội dung $it (VD: Đạt)") }

    private fun preset(id: String, name: String, project: String, location: String) = NotePreset(
        "builtin-$id", name,
        fields.associate { it.first to "" } + mapOf(
            "proj" to project,
            "f1l" to "Địa điểm",
            "f1v" to location
        )
    )

    val builtIn = listOf(
        preset("survey", "Khảo sát", "Khảo sát hiện trường", "Công trình A"),
        preset("delivery", "Giao nhận hàng", "Kiểm tra giao nhận hàng hóa", "Điểm giao nhận"),
        preset("construction", "Công trình", "Kiểm tra tiến độ thi công", "Công trình A"),
        preset("travel", "Du lịch", "Ghi lại hành trình", "Điểm tham quan")
    )
    fun installDefaults(preferences: SharedPreferences) {
        if (preferences.getInt("noteTemplatesSeedVersion", 0) >= 1) return
        val existing = load(preferences)
        val missing = builtIn.filter { candidate -> existing.none { it.name.equals(candidate.name, true) } }
        save(preferences, existing + missing)
        preferences.edit().putInt("noteTemplatesSeedVersion", 1).apply()
    }

    fun load(preferences: SharedPreferences): List<NotePreset> = runCatching {
        val json = JSONArray(preferences.getString("noteTemplates", "[]"))
        (0 until minOf(json.length(), 20)).map { index ->
            val item = json.getJSONObject(index)
            val values = item.getJSONObject("values")
            NotePreset(item.optString("id", UUID.randomUUID().toString()), item.getString("name"),
                fields.associate { it.first to values.optString(it.first) })
        }
    }.getOrDefault(emptyList())

    fun save(preferences: SharedPreferences, presets: List<NotePreset>): Boolean {
        if (presets.size > 20) return false

        val data = JSONArray()
        presets.forEach { item ->
            data.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("values", JSONObject(item.values))
            )
        }

        val committed = preferences.edit()
            .putString("noteTemplates", data.toString())
            .commit()

        if (!committed) return false

        val restored = load(preferences)
        return restored.size == presets.size &&
            restored.map { it.id }.toSet() == presets.map { it.id }.toSet()
    }
}
