package com.datvip.cam

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import java.util.UUID

/** The same task/preset editor is available from Settings and the camera pencil. */
class StampSettingsScreen(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val changed: () -> Unit = {},
    private val reloadLogo: () -> Unit = {},
    private val logo: () -> Bitmap? = { null },
    private val pickLogo: () -> Unit = {},
    private val resetLogo: () -> Unit = {},
    private val about: () -> Unit = {},
    private val purchases: () -> Unit = {},
    private val privacyOptions: () -> Unit = {},
    private val quick: Boolean = false
) {
    private val orange = Color.rgb(255, 164, 92)
    private val muted = Color.rgb(175, 191, 202)
    private val editors = linkedMapOf<String, EditText>()
    private val previews = mutableListOf<StampOverlayView>()
    private val logoPreviews = mutableListOf<ImageView>()
    private var presetList: LinearLayout? = null
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 14f, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(if (bold) Color.WHITE else muted)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun button(value: String, action: () -> Unit) = Button(context).apply {
        text = value; setTextColor(orange); isAllCaps = false; setOnClickListener { action() }
    }
    private fun notifyChange() { changed(); refreshPreview() }
    fun refreshPreview() {
        logoPreviews.forEach { it.setImageBitmap(logo()) }
        val rows = arrayListOf(Language.text(context, "Nhiệm vụ: ") + preferences.getString("proj", "").orEmpty().ifBlank { Language.text(context, "Khảo sát hiện trường") },
            "\u200C" + preferences.getString("addr", "").orEmpty().ifBlank { "Địa điểm khảo sát" })
        for (i in 1..4) {
            val value = preferences.getString("f${i}v", "").orEmpty()
            val label = preferences.getString("f${i}l", "").orEmpty()
            if (value.isNotBlank()) rows.add("\u200B" + if (label.isBlank()) value else "$label: $value")
        }
        rows.add(Language.text(context, "Mã xác thực: 12345678"))
        previews.forEach {
            it.stamp = Stamp("10:51:00", Language.text(context, "Thứ Hai, 02/11/2026 (GMT+7)"), rows)
            it.appearance = StampSettings.appearance(preferences)
            it.logo = logo().takeIf { preferences.getBoolean("logoOn", false) }
            it.invalidate()
        }
    }
    private fun field(parent: LinearLayout, key: String, label: String, multiline: Boolean = false): EditText {
        parent.addView(text(label))
        return EditText(context).apply {
            tag = key; hint = label; setText(preferences.getString(key, "")); setTextColor(Color.WHITE); setHintTextColor(muted)
            setSingleLine(!multiline); if (multiline) { minLines = 2; maxLines = 5; gravity = Gravity.TOP }
            editors[key] = this
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    preferences.edit().putString(key, s.toString()).apply(); notifyChange()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }.also { parent.addView(it, LinearLayout.LayoutParams(-1, -2)) }
    }
    private fun toggle(parent: LinearLayout, key: String, label: String, fallback: Boolean = true, logoChange: Boolean = false) {
        parent.addView(Switch(context).apply {
            tag = key; text = label; setTextColor(Color.WHITE); setPadding(0, dp(10), 0, dp(10))
            isChecked = preferences.getBoolean(key, fallback)
            setOnCheckedChangeListener { _, value ->
                preferences.edit().putBoolean(key, value).apply()
                if (logoChange) reloadLogo()
                notifyChange()
            }
        })
    }
    private fun slider(parent: LinearLayout, key: String, label: String, minimum: Int, maximum: Int, initial: Float): SeekBar {
        val labelView = text(label)
        parent.addView(labelView)
        val bar = SeekBar(context).apply { tag = key; max = maximum - minimum; progress = (preferences.getFloat(key, initial) * 100).toInt().coerceIn(minimum, maximum) - minimum }
        labelView.text = "$label: ${bar.progress + minimum}%"
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                labelView.text = "$label: ${progress + minimum}%"
                preferences.edit().putFloat(key, (progress + minimum) / 100f).apply(); notifyChange()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        parent.addView(bar); return bar
    }
    private fun currentValues() = NotePresets.fields.associate { it.first to preferences.getString(it.first, "").orEmpty() }
    private fun usePreset(preset: NotePreset) {
        val edit = preferences.edit()
        NotePresets.fields.forEach { edit.putString(it.first, preset.values[it.first].orEmpty()) }; edit.apply()
        editors.forEach { (key, view) -> view.setText(preferences.getString(key, "")) }
        notifyChange(); Toast.makeText(context, Language.text(context, "Đang dùng mẫu: {0}", (preset.name)), Toast.LENGTH_SHORT).show()
    }
    private fun askName(initial: String, apply: (String) -> Unit) {
        val input = EditText(context).apply { setText(initial); hint = Language.text(context, "Tên mẫu"); setSingleLine() }
        val dialog = AlertDialog.Builder(context).setTitle(Language.text(context, "Tên mẫu nhiệm vụ")).setView(input).setPositiveButton(Language.text(context, "Lưu"), null).setNegativeButton(Language.text(context, "Huỷ"), null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = input.text.toString().trim().take(60)
            if (title.isBlank()) input.error = Language.text(context, "Nhập tên mẫu") else { apply(title); dialog.dismiss() }
        } }; dialog.show()
    }
    private fun redrawPresets() {
        val list = presetList ?: return; list.removeAllViews()
        val presets = NotePresets.load(preferences)
        list.addView(text(Language.text(context, "Mẫu nhiệm vụ ({0}/20)", (presets.size)), bold = true))
        presets.forEach { preset ->
            val row = LinearLayout(context)
            row.addView(button(preset.name) { usePreset(preset) }.apply { tag = "preset:${preset.id}" }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("⋯") {
                AlertDialog.Builder(context).setTitle(preset.name).setItems(arrayOf(Language.text(context, "Cập nhật từ nội dung hiện tại"), Language.text(context, "Đổi tên"), Language.text(context, "Xoá mẫu"))) { _, choice ->
                    val current = NotePresets.load(preferences)
                    when (choice) {
                        0 -> { NotePresets.save(preferences, current.map { if (it.id == preset.id) it.copy(values = currentValues()) else it }); redrawPresets() }
                        1 -> askName(preset.name) { name ->
                            if (current.any { it.id != preset.id && it.name.equals(name, true) }) Toast.makeText(context, Language.text(context, "Tên mẫu đã có"), Toast.LENGTH_SHORT).show()
                            else { NotePresets.save(preferences, current.map { if (it.id == preset.id) it.copy(name = name) else it }); redrawPresets() }
                        }
                        2 -> AlertDialog.Builder(context).setMessage(Language.text(context, "Xoá mẫu {0}?", (preset.name))).setPositiveButton(Language.text(context, "Xoá")) { _, _ ->
                            NotePresets.save(preferences, current.filterNot { it.id == preset.id }); redrawPresets()
                        }.setNegativeButton(Language.text(context, "Huỷ"), null).show()
                    }
                }.show()
            }, LinearLayout.LayoutParams(dp(56), -2)); list.addView(row)
        }
    }
    fun createContent(close: () -> Unit = {}): View {
        NotePresets.installDefaults(preferences)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0B141B.toInt()); isFocusableInTouchMode = true }
        val toolbar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(12), dp(8)) }
        toolbar.addView(text(if (quick) Language.text(context, "Ghi chú nhanh") else Language.text(context, "Cài đặt dấu ảnh"), 20f, true), LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button(Language.text(context, "Xong"), close).apply { contentDescription = Language.text(context, "Đóng cài đặt") }); root.addView(toolbar)
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(24)) }
        val scroll = ScrollView(context).apply { addView(content) }
        val anchors = linkedMapOf<String, View>()
        if (!quick) {
            val shortcuts = LinearLayout(context)
            val stableTags = listOf("Khung", "Nền", "Logo", "Mẫu")
            for ((index, item) in listOf(Language.text(context, "Khung") to Language.text(context, "Tùy chỉnh khung và nền trong suốt"), Language.text(context, "Nền") to Language.text(context, "Tùy chỉnh khung và nền trong suốt"), "Logo" to "Logo", Language.text(context, "Mẫu") to Language.text(context, "Mẫu nhiệm vụ")).withIndex()) {
                val (label, target) = item
                shortcuts.addView(button(label) { anchors[target]?.let { scroll.smoothScrollTo(0, it.top) } }.apply {
                    tag = "shortcut-${stableTags[index]}"; textSize = 12f; setPadding(dp(4), dp(8), dp(4), dp(8)); maxLines = 1
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            root.addView(shortcuts)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (!quick) {
            content.addView(button(Language.text(context, "Ngôn ngữ")) {
                val tags = listOf("", "vi", "en")
                val current = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
                AlertDialog.Builder(context).setTitle(Language.text(context, "Ngôn ngữ"))
                    .setSingleChoiceItems(arrayOf(Language.text(context, "Theo điện thoại"), "Tiếng Việt", "English"), tags.indexOf(current).coerceAtLeast(0)) { picker, index ->
                        picker.dismiss(); close(); Language.choose(tags[index])
                    }.setNegativeButton(Language.text(context, "Huỷ"), null).show()
            }.apply { tag = "language-picker" })
        }
        fun section(title: String): LinearLayout {
            val heading = text(title, 15f, true); content.addView(heading); anchors[title] = heading
            val group = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply { setColor(0xFF1B2B36.toInt()); cornerRadius = dp(12).toFloat() }
            }
            content.addView(group, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }); return group
        }
        val tasks = section(Language.text(context, "Mẫu nhiệm vụ"))
        tasks.addView(text(Language.text(context, "Chạm tên để dùng mẫu. Nút ⋯ để cập nhật, đổi tên hoặc xoá. GPS luôn lấy mới.")))
        presetList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }; tasks.addView(presetList); redrawPresets()
        tasks.addView(button(Language.text(context, "Lưu nội dung hiện tại thành mẫu")) {
            val current = NotePresets.load(preferences)
            when {
                current.size >= 20 -> Toast.makeText(context, Language.text(context, "Đã đủ 20 mẫu"), Toast.LENGTH_SHORT).show()
                currentValues().values.all { it.isBlank() } -> Toast.makeText(context, Language.text(context, "Nhập nội dung trước khi lưu"), Toast.LENGTH_SHORT).show()
                else -> askName("") { name ->
                    if (current.any { it.name.equals(name, true) }) Toast.makeText(context, Language.text(context, "Tên mẫu đã có"), Toast.LENGTH_SHORT).show()
                    else {
    val newPreset = NotePreset(
        UUID.randomUUID().toString(),
        name,
        currentValues()
    )

    val saved = NotePresets.save(preferences, current + newPreset)

    if (saved) {
        redrawPresets()
        Toast.makeText(
            context,
            "Template saved",
            Toast.LENGTH_SHORT
        ).show()
    } else {
        Toast.makeText(
            context,
            "Cannot save template",
            Toast.LENGTH_LONG
        ).show()
    }
}
                }
            }
        })
        val notes = section(Language.text(context, "Nội dung dấu"))
        field(notes, "proj", Language.text(context, "Nhiệm vụ / công việc"))
        if (!quick) field(notes, "addr", Language.text(context, "Địa chỉ (để trống = tự lấy theo GPS)"), true)
        field(notes, "note", Language.text(context, "Ghi chú"), true)
        if (!quick) field(notes, "wm", Language.text(context, "Watermark chìm"))
        val custom = section(Language.text(context, "Trường tuỳ biến — Nhãn và nội dung"))
        for (i in 1..4) { field(custom, "f${i}l", Language.text(context, "Nhãn {0}", (i))); field(custom, "f${i}v", Language.text(context, "Nội dung {0}", (i)), true) }
        if (!quick) {
            val appearance = section(Language.text(context, "Tùy chỉnh khung và nền trong suốt"))
            val size = slider(appearance, "stampDisplaySize", Language.text(context, "Cỡ dấu"), 50, 120, .75f)
            val row = LinearLayout(context)
            for ((title, progress) in listOf(Language.text(context, "Rất nhỏ") to 0, Language.text(context, "Gọn") to 25, Language.text(context, "Lớn") to 50)) row.addView(button(title) { size.progress = progress }, LinearLayout.LayoutParams(0, -2, 1f))
            appearance.addView(row)
            val opacity = slider(appearance, "stampBackgroundOpacity", Language.text(context, "Độ đậm nền"), 0, 100, .75f)
            appearance.addView(button(Language.text(context, "Nền trong suốt hoàn toàn")) { opacity.progress = 0 }.apply { tag = "transparent-background" })
            appearance.addView(text(Language.text(context, "Màu nền — chọn màu hoặc chỉnh Đỏ / Lục / Lam")))
            val palette = LinearLayout(context)
            val rgb = listOf(SeekBar(context), SeekBar(context), SeekBar(context))
            fun updateRgb(color: Int) { rgb[0].progress = Color.red(color); rgb[1].progress = Color.green(color); rgb[2].progress = Color.blue(color) }
            for (color in listOf(0xFF576145.toInt(), Color.BLACK, Color.WHITE, 0xFF1565C0.toInt(), 0xFFB35D12.toInt())) {
                palette.addView(button("●") { updateRgb(color) }.apply { setTextColor(color); contentDescription = String.format(Locale.US, Language.text(context, "Màu #%06X"), color and 0xFFFFFF) }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            appearance.addView(palette)
            val hex = text(""); appearance.addView(hex)
            rgb.forEachIndexed { index, bar -> appearance.addView(text(listOf(Language.text(context, "Đỏ"), Language.text(context, "Lục"), "Lam")[index])); bar.max = 255; appearance.addView(bar) }
            val initial = StampSettings.background(preferences); updateRgb(initial)
            fun saveColor() {
                val value = String.format(Locale.US, "%02X%02X%02X", rgb[0].progress, rgb[1].progress, rgb[2].progress)
                preferences.edit().putString("stampBackgroundHex", value).apply(); hex.text = Language.text(context, "Màu nền: #{0}", (value)); notifyChange()
            }
            rgb.forEach { it.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { saveColor() }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            }) }; saveColor()
            appearance.addView(button(Language.text(context, "Khôi phục nền mặc định")) { updateRgb(0xFF576145.toInt()); opacity.progress = 75 })
            val previewBox = FrameLayout(context)
            appearance.addView(text(Language.text(context, "Xem thử bố cục — dữ liệu minh hoạ")))
            val preview = StampOverlayView(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.GRAY, 0xFF263C4A.toInt(), Color.BLACK)) }
            previews.add(preview)
            previewBox.addView(preview, FrameLayout.LayoutParams(dp(210), dp(280), Gravity.CENTER))
            appearance.addView(previewBox, LinearLayout.LayoutParams(-1, dp(280)))
            val logos = section("Logo")
            logos.addView(text(Language.text(context, "Logo BẢO VỆ ANTT có sẵn. Logo riêng được ưu tiên khi bạn chọn từ thư viện.")))
            logos.addView(ImageView(context).apply { tag = "logo-image"; scaleType = ImageView.ScaleType.FIT_CENTER; logoPreviews.add(this) }, LinearLayout.LayoutParams(-1, dp(72)))
            toggle(logos, "logoOn", Language.text(context, "Hiện logo lên ảnh"), fallback = false)
            slider(logos, "stampLogoOpacity", Language.text(context, "Độ đậm logo"), 0, 100, 1f)
            toggle(logos, "stampLogoRemoveWhiteBackground", Language.text(context, "Ẩn nền trắng của logo"), false, true)
            logos.addView(button(Language.text(context, "Chọn logo từ thư viện"), pickLogo))
            logos.addView(button(Language.text(context, "Dùng logo mặc định")) { resetLogo(); notifyChange() })
            val show = section(Language.text(context, "Hiển thị trên ảnh và video"))
            for ((key, label) in listOf("time" to Language.text(context, "Ngày giờ"), "gps" to Language.text(context, "Toạ độ GPS"), "alt" to Language.text(context, "Độ cao"), "hdg" to Language.text(context, "Hướng la bàn"), "wmOn" to Language.text(context, "Watermark chìm"), "shutterSound" to Language.text(context, "Tiếng chụp ảnh"))) toggle(show, key, label)
            val app = section(Language.text(context, "Ứng dụng"))
            app.addView(button(Language.text(context, "Xóa quảng cáo"), purchases))
            app.addView(button(Language.text(context, "Tùy chọn quyền riêng tư"), privacyOptions))
            app.addView(button(Language.text(context, "Chính sách quyền riêng tư")) {
                val lang = if (Language.locale(context).language == "vi") "vi" else "en"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://datnguyenvip.github.io/datvip-cam/privacy-1.1-$lang.html")))
            })
            app.addView(button(Language.text(context, "Thông tin và phiên bản"), about))
            app.addView(text(Language.text(context, "Vị trí được dùng để đóng dấu và tra cứu địa chỉ qua dịch vụ Android. Ẩn dòng GPS không tắt định vị. Bạn có thể thu hồi quyền vị trí trong cài đặt điện thoại.")))
            app.addView(button(Language.text(context, "Chính sách quyền riêng tư")) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://datnguyenvip.github.io/datvip-cam/")))
                } catch (_: android.content.ActivityNotFoundException) {
                    AlertDialog.Builder(context).setTitle(Language.text(context, "Chính sách quyền riêng tư"))
                        .setMessage(Language.text(context, "https://datnguyenvip.github.io/datvip-cam/\nLiên hệ: chobanchotoi@gmail.com"))
                        .setPositiveButton(Language.text(context, "Đóng"), null).show()
                }
            })
        }
        root.requestFocus(); refreshPreview(); return root
    }
    fun show() {
        val dialog = Dialog(context, android.R.style.Theme_Material_NoActionBar)
        val root = createContent { dialog.dismiss() }
        dialog.setContentView(root)
        dialog.window?.let { window ->
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
            }
        }
        dialog.show(); dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }
}
