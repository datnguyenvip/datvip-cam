package com.datvip.cam

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ContentUris
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Size
import android.util.TypedValue
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class Stamp(val head: String?, val date: String?, val rows: List<String>)
class Row(val t: String, val s: Float, val bold: Boolean, val col: Int)
class Item(val uri: Uri, val time: Long) { var lat: Double? = null; var lon: Double? = null; var sel = false }

class MainActivity : AppCompatActivity(), LocationListener, SensorEventListener {
    private lateinit var pv: PreviewView
    private lateinit var ov: TextView
    private lateinit var gpsTv: TextView
    private lateinit var stage: FrameLayout
    private lateinit var frame: FrameLayout
    private lateinit var layer: FrameLayout
    private lateinit var panelLogo: ImageView
    private lateinit var sideTv: TextView
    private lateinit var panel: LinearLayout
    private lateinit var shareBtn: TextView
    private var lastShare = ""
    private var lastName = ""
    private var hideRun: Runnable? = null
    private lateinit var logoIv: ImageView
    private var logoBmp: Bitmap? = null
    private var pxUnit = 4f
    private var curDeg = 0
    private var lastRot = Surface.ROTATION_0
    private val orient by lazy {
        object : OrientationEventListener(this@MainActivity) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                val rot = when (o) { in 45..134 -> Surface.ROTATION_270; in 135..224 -> Surface.ROTATION_180; in 225..314 -> Surface.ROTATION_90; else -> Surface.ROTATION_0 }
                lastRot = rot; imageCapture?.targetRotation = rot
                val deg = when (rot) { Surface.ROTATION_90 -> 90; Surface.ROTATION_270 -> -90; Surface.ROTATION_180 -> 180; else -> 0 }
                if (deg != curDeg) { curDeg = deg; runOnUiThread { applyRotation() } }
            }
        }
    }
    private val sp by lazy { getSharedPreferences("dc", Context.MODE_PRIVATE) }
    private val ex = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var front = false
    private var loc: Location? = null
    private var hdg: Float? = null
    private var autoAddr = ""
    private var lastGeo = 0L
    private var lastUri: Uri? = null
    private val days = arrayOf("Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy")
    private val dirs = arrayOf("B", "ĐB", "Đ", "ĐN", "N", "TN", "T", "TB")
    private val boolKeys = listOf("time" to "Ngày giờ", "gps" to "Toạ độ GPS", "alt" to "Độ cao", "hdg" to "Hướng la bàn", "wmOn" to "Watermark chìm", "logoOn" to "Hiện logo lên ảnh")

    private fun s(k: String) = sp.getString(k, "") ?: ""
    private fun b(k: String) = sp.getBoolean(k, true)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted(Manifest.permission.CAMERA)) startCamera()
        startSensors()
    }
    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) saveLogo(uri) }
    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi(); loadLogo()
        perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        val tick = object : Runnable { override fun run() { refresh(); ui.postDelayed(this, 1000) } }
        ui.post(tick)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0B141B.toInt()) }
        stage = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        frame = FrameLayout(this)
        stage.addView(frame, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        pv = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        frame.addView(pv, FrameLayout.LayoutParams(-1, -1))
        layer = FrameLayout(this)
        frame.addView(layer, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        ov = TextView(this).apply { setTextColor(Color.WHITE); setPadding(dp(10), dp(8), dp(12), dp(8)) }
        panelLogo = ImageView(this).apply {
            visibility = View.GONE; scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { setColor(0xEBFFFFFF.toInt()); cornerRadius = dp(4).toFloat() }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sideTv = TextView(this).apply { setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; visibility = View.GONE; setPadding(dp(8), dp(6), dp(12), 0) }
        val headRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        headRow.addView(panelLogo, LinearLayout.LayoutParams(dp(40), dp(20)).apply { setMargins(dp(10), dp(8), 0, 0) })
        headRow.addView(sideTv, LinearLayout.LayoutParams(-2, -2))
        box.addView(headRow, LinearLayout.LayoutParams(-2, -2))
        box.addView(ov, LinearLayout.LayoutParams(-2, -2))
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(0xCC0E1A24.toInt()); cornerRadius = dp(8).toFloat() }
            clipToOutline = true
            addView(View(context).apply { setBackgroundColor(0xFFFF6B1A.toInt()) }, LinearLayout.LayoutParams(dp(4), -1))
            addView(box, LinearLayout.LayoutParams(-2, -2))
        }
        layer.addView(panel, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(10), 0, dp(10), dp(12)) })
        stage.addOnLayoutChangeListener { _, l0, t0, r0, b0, _, _, _, _ ->
            val sw = r0 - l0; val sh = b0 - t0
            if (sw > 0 && sh > 0) {
                val fw = minOf(sw, sh * 3 / 4); val fh = fw * 4 / 3
                val lp = frame.layoutParams as FrameLayout.LayoutParams
                if (lp.width != fw || lp.height != fh) { lp.width = fw; lp.height = fh; frame.layoutParams = lp; frame.post { applyRotation() } }
            }
        }
        gpsTv = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat() }
            setOnClickListener { locateDialog() }
        }
        setGpsPill(false, "Đang định vị…")
        stage.addView(gpsTv, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(12), 0, 0) })
        shareBtn = TextView(this).apply {
            text = "📤 Chia sẻ ảnh + vị trí"; setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(10), dp(14), dp(10)); visibility = View.GONE
            background = GradientDrawable().apply { setColor(0xFFFF6B1A.toInt()); cornerRadius = dp(22).toFloat() }
            setOnClickListener { shareLast() }
        }
        stage.addView(shareBtn, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { setMargins(0, 0, dp(10), 0) })
        fun mini(t: String, f: () -> Unit) = TextView(this).apply {
            text = t; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x99000000.toInt()) }
            setOnClickListener { f() }
        }
        val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        side.addView(mini("✎") { quickNote() }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { bottomMargin = dp(10) })
        side.addView(mini("📍") { locateDialog() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        stage.addView(side, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, dp(10), dp(14)) })
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        fun chip(t: String, f: () -> Unit) = TextView(this).apply {
            text = t; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1B2B36.toInt()); setStroke(dp(1), 0xFF2F4452.toInt()) }
            setOnClickListener { f() }
        }
        fun cell(v: View, size: Int) = FrameLayout(this).apply { addView(v, FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER)) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, dp(22)) }
        val one = { LinearLayout.LayoutParams(0, dp(80), 1f) }
        val shutter = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFF6B1A.toInt()); setStroke(dp(5), Color.WHITE) }
            setOnClickListener { shoot() }
        }
        bar.addView(cell(chip("🖼") { openGallery() }, 52), one())
        bar.addView(cell(shutter, 78), one())
        bar.addView(cell(chip("⟲") { front = !front; bind() }, 52), one())
        bar.addView(cell(chip("⚙") { settings() }, 52), one())
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }
    private fun setGpsPill(ok: Boolean, t: String) {
        gpsTv.text = "● $t"
        (gpsTv.background as GradientDrawable).setColor(if (ok) 0xDD1F9D55.toInt() else 0xDDC23B3B.toInt())
    }

    /* ---------- Camera ---------- */
    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({ provider = f.get(); pv.post { bind() } }, ContextCompat.getMainExecutor(this))
    }
    private fun bind() {
        val p = provider ?: return
        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build().also { it.setSurfaceProvider(pv.surfaceProvider) }
        val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3).setTargetRotation(lastRot).build()
        imageCapture = ic
        val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try { p.unbindAll(); p.bindToLifecycle(this, sel, preview, ic) } catch (e: Exception) { toast("Không mở được camera") }
    }

    private fun shoot() {
        val ic = imageCapture ?: return
        ic.takePicture(ex, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try { process(image) } catch (e: Exception) { toast("Lỗi lưu ảnh: ${e.message}") } finally { image.close() }
            }
            override fun onError(e: ImageCaptureException) { toast("Chụp lỗi: ${e.message}") }
        })
    }

    private fun process(image: ImageProxy) {
        val buf = image.planes[0].buffer
        val bytes = ByteArray(buf.remaining()); buf.get(bytes)
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val r = image.cropRect
        if (r.width() in 1..bmp.width && r.height() in 1..bmp.height && r.left >= 0 && r.top >= 0 &&
            r.right <= bmp.width && r.bottom <= bmp.height && (r.width() != bmp.width || r.height() != bmp.height))
            bmp = Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height())
        val rot = image.imageInfo.rotationDegrees
        if (rot != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        if (!bmp.isMutable) bmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val d = Date()
        val L = lines(d)
        val rows = L.rows + ("Mã xác thực: " + code(d))
        stamp(bmp, Stamp(L.head, L.date, rows))
        val name = "DatvipCam_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(d) + ".jpg"
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DatvipCam")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: throw Exception("MediaStore")
        contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        val gl = loc
        if (gl != null) try {
            contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val ex = ExifInterface(pfd.fileDescriptor)
                ex.setLatLong(gl.latitude, gl.longitude)
                if (gl.hasAltitude()) ex.setAltitude(gl.altitude)
                ex.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "https://www.google.com/maps/dir/?api=1&destination=" + String.format(Locale.US, "%.6f,%.6f", gl.latitude, gl.longitude))
                ex.saveAttributes()
            }
        } catch (e: Exception) {}
        lastName = name
        lastUri = uri
        lastShare = shareText(d, loc)
        runOnUiThread {
            shareBtn.visibility = View.VISIBLE
            hideRun?.let { ui.removeCallbacks(it) }
            val r = Runnable { shareBtn.visibility = View.GONE }; hideRun = r; ui.postDelayed(r, 12000)
        }
        toast("Đã lưu: Pictures/DatvipCam")
    }

    /* ---------- Nội dung dấu ---------- */
    private fun lines(d: Date): Stamp {
        val cal = Calendar.getInstance().apply { time = d }
        fun p2(n: Int) = String.format(Locale.US, "%02d", n)
        var head: String? = null; var date: String? = null
        if (b("time")) {
            head = "${p2(cal.get(Calendar.HOUR_OF_DAY))}:${p2(cal.get(Calendar.MINUTE))}:${p2(cal.get(Calendar.SECOND))}"
            val off = TimeZone.getDefault().getOffset(d.time) / 3600000
            date = "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]}, ${p2(cal.get(Calendar.DAY_OF_MONTH))}/${p2(cal.get(Calendar.MONTH) + 1)}/${cal.get(Calendar.YEAR)} (GMT${if (off >= 0) "+" else ""}$off)"
        }
        val rows = ArrayList<String>()
        if (s("proj").isNotEmpty()) rows.add("Nhiệm vụ: " + s("proj"))
        val addr = if (s("addr").isNotEmpty()) s("addr") else autoAddr
        if (addr.isNotEmpty()) rows.add("\u200C" + addr)
        for (i in 1..4) { val lb = s("f${i}l"); val v = s("f${i}v"); if (v.isNotEmpty()) rows.add("\u200B" + (if (lb.isNotEmpty()) "$lb: $v" else v)) }
        val l = loc
        if (b("gps")) rows.add(if (l != null) "📍 " + String.format(Locale.US, "Vĩ độ %.6f, Kinh độ %.6f (±%dm)", l.latitude, l.longitude, l.accuracy.toInt()) else "📍 Chưa có toạ độ GPS")
        if (b("alt") && l != null && l.hasAltitude()) rows.add("Độ cao ${l.altitude.toInt()} m")
        val h = hdg
        if (b("hdg") && h != null) rows.add("Hướng ${h.toInt()}° ${dirs[Math.round(h / 45f) % 8]}")
        if (s("note").isNotEmpty()) rows.add("Ghi chú: " + s("note"))
        return Stamp(head, date, rows)
    }
    private fun code(d: Date): String {
        val l = loc
        val raw = "${d.time}|" + (if (l != null) "%.6f,%.6f".format(Locale.US, l.latitude, l.longitude) else "-")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).take(4).joinToString("") { "%02X".format(it) }
    }
    private fun refresh() {
        val L = lines(Date()); val sb = SpannableStringBuilder()
        val hasLogo = logoBmp != null && b("logoOn")
        val side = if (hasLogo) L.rows.filter { it.startsWith("Nhiệm vụ:") || it.startsWith("\u200C") } else emptyList()
        val rest = L.rows.filter { it !in side }
        ov.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxUnit * 3.3f)
        if (side.isNotEmpty()) {
            val sp = SpannableStringBuilder()
            side.forEachIndexed { i, r ->
                val s0 = sp.length; sp.append(r.removePrefix("\u200C"))
                if (r.startsWith("Nhiệm vụ:")) sp.setSpan(ForegroundColorSpan(0xFFFFA45C.toInt()), s0, sp.length, 0)
                if (i < side.size - 1) sp.append("\n")
            }
            sideTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxUnit * 3.4f); sideTv.text = sp; sideTv.visibility = View.VISIBLE
        } else sideTv.visibility = View.GONE
        if (L.head != null) {
            val n = L.head.length
            sb.append(L.head + "\n"); sb.setSpan(RelativeSizeSpan(2.3f), 0, n, 0); sb.setSpan(StyleSpan(Typeface.BOLD), 0, n, 0)
            sb.setSpan(ForegroundColorSpan(0xFFFFD23F.toInt()), 0, n, 0)
            sb.append(L.date + "\n")
        }
        rest.forEachIndexed { i, r0 ->
            val r = r0.removePrefix("\u200B").removePrefix("\u200C"); val s0 = sb.length; sb.append(r)
            if (r.startsWith("Nhiệm vụ:")) { sb.setSpan(StyleSpan(Typeface.BOLD), s0, sb.length, 0); sb.setSpan(ForegroundColorSpan(0xFFFFA45C.toInt()), s0, sb.length, 0) }
            if (i < rest.size - 1) sb.append("\n")
        }
        ov.text = sb; panel.visibility = if (sb.isBlank() && !hasLogo) View.GONE else View.VISIBLE
        updateLogoView()
    }

    private fun styleRow(t0: String, u: Float): Row {
        if (t0.startsWith("\u200B")) return Row(t0.substring(1), u * 3.3f, false, 0xFFE6EEF2.toInt())
        if (t0.startsWith("\u200C")) return Row(t0.substring(1), u * 3.4f, true, Color.WHITE)
        val t = t0
        return when {
            t.startsWith("Nhiệm vụ:") -> Row(t, u * 3.8f, true, 0xFFFFA45C.toInt())
            t.startsWith("📍") -> Row(t, u * 3.2f, false, Color.WHITE)
            t.startsWith("Mã xác thực") -> Row(t, u * 2.6f, false, 0xFF9FB3BC.toInt())
            t.startsWith("Ghi chú") || t.startsWith("Độ cao") || t.startsWith("Hướng") -> Row(t, u * 3.2f, false, 0xFFE6EEF2.toInt())
            else -> Row(t, u * 3.4f, true, Color.WHITE)
        }
    }

    private fun stamp(bmp: Bitmap, L: Stamp) {
        val c = Canvas(bmp); val w = bmp.width.toFloat(); val h = bmp.height.toFloat(); val u = minOf(w, h) / 100f
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        if (b("wmOn") && s("wm").isNotEmpty()) {
            tp.color = 0x44FFFFFF; tp.textSize = u * 4f; tp.typeface = Typeface.DEFAULT_BOLD; tp.textAlign = Paint.Align.CENTER
            val tw = tp.measureText(s("wm")) + u * 12; val sp2 = u * 16; val dd = maxOf(w, h)
            c.save(); c.translate(w / 2, h / 2); c.rotate(-30f)
            var y = -dd; var row = 0
            while (y < dd) { var x = -dd + if (row % 2 == 1) tw / 2 else 0f; while (x < dd) { c.drawText(s("wm"), x, y, tp); x += tw }; y += sp2; row++ }
            c.restore(); tp.textAlign = Paint.Align.LEFT
        }
        val lg = logoBmp?.takeIf { b("logoOn") }
        var lw = 0f; var lh = 0f; val pad = u * .8f
        if (lg != null) { lh = u * 9f; lw = lg.width * lh / lg.height; if (lw > u * 24f) { lw = u * 24f; lh = lw * lg.height / lg.width } }
        val side = if (lg != null) L.rows.filter { it.startsWith("Nhiệm vụ:") || it.startsWith("\u200C") } else emptyList()
        val rest = L.rows.filter { it !in side }
        val items = ArrayList<Row>()
        if (L.head != null) { items.add(Row(L.head, u * 7.5f, true, 0xFFFFD23F.toInt())); items.add(Row(L.date ?: "", u * 3.4f, false, Color.WHITE)) }
        rest.forEach { items.add(styleRow(it, u)) }
        if (items.isEmpty() && lg == null) return
        val p = u * 2.8f; val m = u * 3f; val lhm = 1.35f; val bar = u * 1.4f; val gap = u * 1.8f
        val avail = w - 2 * m - 2 * p - bar
        var mainW = 0f
        items.forEach { tp.textSize = it.s; tp.typeface = if (it.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; mainW = maxOf(mainW, tp.measureText(it.t)) }
        val badgeW = if (lg != null) lw + 2 * pad else 0f
        val sideTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = u * 3.4f; color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
        var sideNat = 0f
        side.forEach { sideNat = maxOf(sideNat, sideTp.measureText(it.removePrefix("\u200C"))) }
        var contentW = minOf(maxOf(mainW, badgeW + (if (side.isNotEmpty()) gap + sideNat else 0f)), avail)
        if (side.isNotEmpty()) contentW = maxOf(contentW, minOf(avail, badgeW + gap + u * 30f))
        val k = minOf(1f, contentW / maxOf(mainW, 1f))
        var headH = 0f; var sideLayout: StaticLayout? = null
        if (lg != null) {
            headH = lh + 2 * pad
            if (side.isNotEmpty()) {
                val spn = SpannableStringBuilder()
                side.forEachIndexed { i, r ->
                    val s0 = spn.length; spn.append(r.removePrefix("\u200C"))
                    if (r.startsWith("Nhiệm vụ:")) spn.setSpan(ForegroundColorSpan(0xFFFFA45C.toInt()), s0, spn.length, 0)
                    if (i < side.size - 1) spn.append("\n")
                }
                val sw = maxOf(1, (contentW - badgeW - gap).toInt())
                val sl = StaticLayout.Builder.obtain(spn, 0, spn.length, sideTp, sw).build()
                sideLayout = sl; headH = maxOf(headH, sl.height.toFloat())
            }
            headH += p * .6f
        }
        val bh = headH + items.sumOf { (it.s * k * lhm).toDouble() }.toFloat() + p * 1.6f
        val x0 = m; val y0 = h - m - bh
        val rect = RectF(x0, y0, x0 + bar + contentW + 2 * p, y0 + bh)
        val path = Path().apply { addRoundRect(rect, u * 1.6f, u * 1.6f, Path.Direction.CW) }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD90E1A24.toInt(); setShadowLayer(u * 1.2f, 0f, u * .3f, 0x80000000.toInt()) }
        c.drawPath(path, bg)
        c.save(); c.clipPath(path)
        c.drawRect(x0, y0, x0 + bar, y0 + bh, Paint().apply { color = 0xFFFF6B1A.toInt() }); c.restore()
        var yy = y0 + p * .8f
        if (lg != null) {
            val bx = x0 + bar + p
            val r = RectF(bx, yy, bx + badgeW, yy + lh + 2 * pad)
            c.drawRoundRect(r, u * .8f, u * .8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEBFFFFFF.toInt() })
            c.drawBitmap(lg, null, RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            sideLayout?.let { sl -> c.save(); c.translate(bx + badgeW + gap, yy); sl.draw(c); c.restore() }
            yy += headH
        }
        items.forEach {
            tp.textSize = it.s * k; tp.typeface = if (it.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; tp.color = it.col
            c.drawText(it.t, x0 + bar + p, yy - tp.ascent(), tp); yy += it.s * k * lhm
        }
    }

    /* ---------- GPS, la bàn ---------- */
    @SuppressLint("MissingPermission")
    private fun startSensors() {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            val lm = getSystemService(LocationManager::class.java)
            for (pr in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER))
                if (lm.isProviderEnabled(pr)) lm.requestLocationUpdates(pr, 2000L, 0f, this)
        } else gpsTv.text = "Chưa cấp quyền vị trí"
        val sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    override fun onResume() { super.onResume(); if (granted(Manifest.permission.CAMERA) && provider == null) startCamera(); if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) startSensors(); orient.enable() }
    override fun onPause() { super.onPause(); orient.disable(); getSystemService(LocationManager::class.java).removeUpdates(this); getSystemService(SensorManager::class.java).unregisterListener(this) }
    override fun onLocationChanged(l: Location) {
        val cur = loc
        if (l.provider == LocationManager.NETWORK_PROVIDER && cur != null && cur.provider == LocationManager.GPS_PROVIDER && l.time - cur.time < 10000) return
        loc = l; setGpsPill(true, "GPS ±${l.accuracy.toInt()}m"); geocode(l, false)
    }
    private fun geocode(l: Location, force: Boolean) {
        val now = System.currentTimeMillis()
        if ((!force && now - lastGeo < 60000) || !Geocoder.isPresent()) return
        lastGeo = now
        thread { try { @Suppress("DEPRECATION") val a = Geocoder(this, Locale("vi", "VN")).getFromLocation(l.latitude, l.longitude, 1); autoAddr = a?.firstOrNull()?.getAddressLine(0) ?: "" } catch (e: Exception) {} }
    }
    private fun locateDialog() {
        val l = loc
        if (l == null) { toast("Chưa có toạ độ. Hãy bật GPS và chờ vài giây"); return }
        geocode(l, true)
        val addr = if (s("addr").isNotEmpty()) s("addr") else autoAddr
        val coord = String.format(Locale.US, "%.6f, %.6f", l.latitude, l.longitude)
        val msg = "Toạ độ: $coord\nSai số: ±${l.accuracy.toInt()} m" +
            (if (l.hasAltitude()) "\nĐộ cao: ${l.altitude.toInt()} m" else "") +
            (hdg?.let { "\nHướng: ${it.toInt()}° ${dirs[Math.round(it / 45f) % 8]}" } ?: "") +
            "\nĐịa chỉ: " + (if (addr.isNotEmpty()) addr else "đang tra cứu… (cần có mạng, mở lại sau vài giây)")
        AlertDialog.Builder(this).setTitle("📍 Vị trí hiện tại").setMessage(msg)
            .setNeutralButton("Mở bản đồ") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$coord(Datvip Cam)"))) } catch (e: Exception) { toast("Không có app bản đồ") }
            }
            .setNegativeButton("Sao chép") { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("toa-do", coord)); toast("Đã sao chép toạ độ")
            }
            .setPositiveButton("Chia sẻ") { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText(Date(), l)) }, "Chia sẻ vị trí"))
            }.show()
    }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onSensorChanged(e: SensorEvent) {
        val r = FloatArray(9); val r2 = FloatArray(9); val o = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(r, e.values)
        SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, r2)
        SensorManager.getOrientation(r2, o)
        hdg = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
    }

    /* ---------- Cài đặt, thư viện ---------- */
    private fun settings() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val fields = listOf("proj" to "Tên nhiệm vụ / công việc", "addr" to "Địa chỉ (để trống = tự lấy theo GPS)", "note" to "Ghi chú", "wm" to "Watermark chìm (tên công ty…)") + (1..4).flatMap { listOf("f${it}l" to "Nhãn trường $it (VD: Tên)", "f${it}v" to "Nội dung trường $it (VD: Đạt)") }
        val ets = fields.map { (k, hint) -> EditText(this).apply { this.hint = hint; setText(s(k)); setSingleLine() }.also { col.addView(it) } }
        val cbs = boolKeys.map { (k, t) -> CheckBox(this).apply { text = t; isChecked = b(k) }.also { col.addView(it) } }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply { text = if (logoBmp == null) "Chọn logo" else "Đổi logo"; setOnClickListener { pickLogo.launch("image/*") } })
        row.addView(Button(this).apply { text = "Xoá logo"; setOnClickListener { File(filesDir, "logo.png").delete(); logoBmp = null; refresh(); toast("Đã xoá logo") } })
        col.addView(row)
        AlertDialog.Builder(this).setTitle("Cài đặt dấu ảnh").setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("Lưu") { _, _ ->
                val e = sp.edit()
                fields.forEachIndexed { i, f -> e.putString(f.first, ets[i].text.toString().trim()) }
                boolKeys.forEachIndexed { i, kv -> e.putBoolean(kv.first, cbs[i].isChecked) }
                e.apply(); refresh()
            }.setNegativeButton("Huỷ", null).show()
    }
    private fun shareText(d: Date, l: Location?): String {
        val sb = StringBuilder()
        if (s("proj").isNotEmpty()) sb.append("Nhiệm vụ: " + s("proj") + "\n")
        sb.append("🕒 " + SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.US).format(d) + "\n")
        val addr = if (s("addr").isNotEmpty()) s("addr") else autoAddr
        if (addr.isNotEmpty()) sb.append("🏠 " + addr + "\n")
        if (l != null) {
            val c = String.format(Locale.US, "%.6f,%.6f", l.latitude, l.longitude)
            sb.append("📍 " + c + "\n🧭 Chỉ đường: https://www.google.com/maps/dir/?api=1&destination=" + c + "&travelmode=driving\n")
        }
        if (s("note").isNotEmpty()) sb.append("Ghi chú: " + s("note"))
        return sb.toString().trim()
    }
    private fun sharableUri(): Uri? {
        val src = lastUri ?: return null
        return try {
            val dir = File(cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, lastName.ifEmpty { "datvipcam.jpg" })
            contentResolver.openInputStream(src)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        } catch (e: Exception) { null }
    }
    private fun shareLast() {
        val u = sharableUri() ?: run { toast("Chưa có ảnh để chia sẻ"); return }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("datvip", lastShare))
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, u); putExtra(Intent.EXTRA_TEXT, lastShare)
            clipData = ClipData.newRawUri("", u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        toast("Đã chép sẵn lời nhắn + link chỉ đường. Nếu app không hiện chữ, hãy dán vào")
        startActivity(Intent.createChooser(i, "Chia sẻ ảnh + vị trí"))
    }
    private fun loadLogo() { val f = File(filesDir, "logo.png"); logoBmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else null }
    private fun saveLogo(uri: Uri) {
        try {
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
            var ss = 1; while (maxOf(opt.outWidth, opt.outHeight) / ss > 1024) ss *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = ss }
            val bm = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: throw Exception("decode")
            File(filesDir, "logo.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
            loadLogo(); refresh(); toast("Đã đặt logo")
        } catch (e: Exception) { toast("Không đọc được ảnh logo") }
    }
    private fun updateLogoView() {
        val lg = logoBmp
        if (lg == null || !b("logoOn")) { panelLogo.visibility = View.GONE; return }
        val u = pxUnit; var h = u * 9f; var w = lg.width * h / lg.height
        if (w > u * 24f) { w = u * 24f; h = w * lg.height / lg.width }
        val pad = (u * .8f).toInt(); val nw = w.toInt() + 2 * pad; val nh = h.toInt() + 2 * pad
        val lp = panelLogo.layoutParams as LinearLayout.LayoutParams
        if (lp.width != nw || lp.height != nh) { lp.width = nw; lp.height = nh; panelLogo.layoutParams = lp; panelLogo.setPadding(pad, pad, pad, pad) }
        if (panelLogo.tag !== lg) { panelLogo.setImageBitmap(lg); panelLogo.tag = lg }
        panelLogo.visibility = View.VISIBLE
    }
    private fun applyRotation() {
        val w = frame.width; val h = frame.height
        if (w == 0 || h == 0) return
        val land = curDeg == 90 || curDeg == -90
        val lp = layer.layoutParams as FrameLayout.LayoutParams
        val nw = if (land) h else w; val nh = if (land) w else h
        if (lp.width != nw || lp.height != nh) { lp.width = nw; lp.height = nh; layer.layoutParams = lp }
        layer.animate().rotation(curDeg.toFloat()).setDuration(150).start()
        pxUnit = minOf(w, h) / 100f
        val pl = panel.layoutParams as FrameLayout.LayoutParams; val m = (pxUnit * 3).toInt()
        pl.setMargins(m, 0, m, m); panel.layoutParams = pl
        refresh()
    }
    private fun quickNote() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val keys = listOf("note" to "Ghi chú") + (1..4).flatMap { listOf("f${it}l" to "Nhãn $it (VD: Tên)", "f${it}v" to "Nội dung $it (VD: Đạt)") }
        val ets = keys.map { (k, hint) -> EditText(this).apply { this.hint = hint; setText(s(k)); setSingleLine() }.also { col.addView(it) } }
        AlertDialog.Builder(this).setTitle("Ghi chú nhanh").setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("Lưu") { _, _ ->
                val e = sp.edit(); keys.forEachIndexed { i, kv -> e.putString(kv.first, ets[i].text.toString().trim()) }; e.apply(); refresh()
            }.setNegativeButton("Huỷ", null).show()
    }

    /* ---------- Thư viện + chia sẻ hàng loạt ---------- */
    private fun nearBy(a: Item, b2: Item): Boolean {
        val la = a.lat; val oa = a.lon; val lb = b2.lat; val ob = b2.lon
        if (la == null || oa == null || lb == null || ob == null) return false
        val r = FloatArray(1); Location.distanceBetween(la, oa, lb, ob, r); return r[0] < 100f
    }
    private fun openGallery() {
        val items = ArrayList<Item>()
        val col = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(col, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED),
            MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?", arrayOf("%DatvipCam%"), MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
            while (c.moveToNext()) items.add(Item(ContentUris.withAppendedId(col, c.getLong(0)), c.getLong(1) * 1000))
        }
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0B141B.toInt()); setPadding(0, dp(28), 0, 0) }
        val title = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val checks = ArrayList<TextView>(); val imgs = ArrayList<ImageView>(); var lastTap = -1
        fun upd() { title.text = "Ảnh (${items.count { it.sel }}/${items.size})"; checks.forEachIndexed { i, t -> t.visibility = if (items[i].sel) View.VISIBLE else View.GONE } }
        fun pill(t: String, f: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(Color.WHITE); textSize = 13f; setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply { setColor(0xFF1B2B36.toInt()); cornerRadius = dp(18).toFloat() }; setOnClickListener { f() }
        }
        val gap = { LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) } }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), dp(8)) }
        bar.addView(pill("✕") { dlg.dismiss() })
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(pill("Cùng vị trí") {
            if (lastTap < 0) toast("Chạm vào một ảnh trước") else { val ref = items[lastTap]; items.forEach { if (it === ref || nearBy(ref, it)) it.sel = true }; upd() }
        }, gap())
        bar.addView(pill("Tất cả") { val all = items.all { it.sel }; items.forEach { it.sel = !all }; upd() }, gap())
        bar.addView(pill("📤 Chia sẻ") { shareBatch(items.filter { it.sel }) }, gap())
        root.addView(bar)
        val size = resources.displayMetrics.widthPixels / 3
        val grid = GridLayout(this).apply { columnCount = 3 }
        items.forEachIndexed { i, m ->
            val cell = FrameLayout(this)
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF182228.toInt()) }
            val ck = TextView(this).apply {
                text = "✓"; setTextColor(Color.WHITE); gravity = Gravity.CENTER; textSize = 16f; visibility = View.GONE
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFF6B1A.toInt()) }
            }
            cell.addView(iv, FrameLayout.LayoutParams(-1, -1).apply { setMargins(1, 1, 1, 1) })
            cell.addView(ck, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.END or Gravity.TOP).apply { setMargins(0, dp(6), dp(6), 0) })
            cell.setOnClickListener { m.sel = !m.sel; lastTap = i; upd() }
            cell.setOnLongClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); true
            }
            imgs.add(iv); checks.add(ck)
            grid.addView(cell, GridLayout.LayoutParams().apply { width = size; height = size })
        }
        root.addView(ScrollView(this).apply { addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
        thread {
            items.forEachIndexed { i, m ->
                val bm = try { contentResolver.loadThumbnail(m.uri, Size(300, 300), null) } catch (e: Exception) { null }
                try { contentResolver.openInputStream(m.uri)?.use { ins -> ExifInterface(ins).latLong?.let { m.lat = it[0]; m.lon = it[1] } } } catch (e: Exception) {}
                runOnUiThread { if (bm != null) imgs[i].setImageBitmap(bm) }
            }
        }
        dlg.setContentView(root); dlg.show(); upd()
        if (items.isEmpty()) toast("Chưa có ảnh nào. Hãy chụp trước")
    }
    private fun batchText(sel: List<Item>): String {
        val sb = StringBuilder()
        if (s("proj").isNotEmpty()) sb.append("Nhiệm vụ: " + s("proj") + "\n")
        sb.append("🖼 ${sel.size} ảnh · " + SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.US).format(Date(sel.minOf { it.time })) + "\n")
        val groups = ArrayList<MutableList<Item>>()
        for (m in sel) { val g = groups.firstOrNull { nearBy(it[0], m) }; if (g != null) g.add(m) else groups.add(mutableListOf(m)) }
        groups.forEachIndexed { i, g ->
            val la = g[0].lat; val lo = g[0].lon
            if (la != null && lo != null) {
                val c = String.format(Locale.US, "%.6f,%.6f", la, lo)
                sb.append((if (groups.size > 1) "📍 Vị trí ${i + 1} (${g.size} ảnh): " else "📍 ") + c +
                    "\n🧭 Chỉ đường: https://www.google.com/maps/dir/?api=1&destination=" + c + "&travelmode=driving\n")
            }
        }
        if (s("note").isNotEmpty()) sb.append("Ghi chú: " + s("note"))
        return sb.toString().trim()
    }
    private fun shareBatch(sel: List<Item>) {
        if (sel.isEmpty()) { toast("Hãy chọn ít nhất một ảnh"); return }
        val txt = batchText(sel)
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("datvip", txt))
        toast("Đang chuẩn bị ${sel.size} ảnh…")
        thread {
            try {
                val dir = File(cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val uris = ArrayList<Uri>()
                sel.forEachIndexed { i, m ->
                    val f = File(dir, "DatvipCam_${i + 1}_${m.time}.jpg")
                    contentResolver.openInputStream(m.uri)?.use { ins -> f.outputStream().use { o -> ins.copyTo(o) } }
                    uris.add(FileProvider.getUriForFile(this, "$packageName.fileprovider", f))
                }
                val it2 = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); putExtra(Intent.EXTRA_TEXT, txt)
                    val cd = ClipData.newRawUri("", uris[0]); uris.drop(1).forEach { u -> cd.addItem(ClipData.Item(u)) }; clipData = cd
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    toast("Đã chép sẵn lời nhắn + link chỉ đường. Nếu Zalo không hiện chữ, hãy dán vào")
                    startActivity(Intent.createChooser(it2, "Chia sẻ hàng loạt"))
                }
            } catch (e: Exception) { toast("Không chia sẻ được: ${e.message}") }
        }
    }
    private fun openLast() {
        val u = lastUri
        val i = if (u != null) Intent(Intent.ACTION_VIEW).setDataAndType(u, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        else Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try { startActivity(i) } catch (e: Exception) { toast("Không mở được thư viện") }
    }
    private fun toast(t: String) = runOnUiThread { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }
}
