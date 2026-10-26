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
import android.media.MediaActionSound
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
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
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
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.media3.common.util.UnstableApi
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
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

class Stamp(val head: String?, val date: String?, val rows: List<String>)
class Row(val t: String, val s: Float, val bold: Boolean, val col: Int)
class Item(val uri: Uri, val time: Long, val video: Boolean = false) { var lat: Double? = null; var lon: Double? = null; var sel = false }

@UnstableApi
class MainActivity : AppCompatActivity(), LocationListener, SensorEventListener {
    private val shutterSound = MediaActionSound()
    private lateinit var shutter: View
    private lateinit var photoModeButton: TextView
    private lateinit var videoModeButton: TextView
    private lateinit var recordStatus: TextView
    private var videoMode = false
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var recordPending = false
    private var videoProcessing = false
    private var destroying = false
    private var lastMime = "image/jpeg"
    private val videoExporter = VideoStampExporter()
    private val videoFrames = ArrayList<VideoStampFrame>()
    private var videoStart = Date()
    private var videoLocation: Location? = null
    private var videoLandscape = false
    private var videoCode = ""
    private var lastVideoSecond = -1L
    private var recoveryChecked = false
    private fun videoBusy() = recordPending || recording != null || videoProcessing
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
                if (videoBusy()) return
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
    private val boolKeys = listOf("time" to "Ngày giờ", "gps" to "Toạ độ GPS", "alt" to "Độ cao", "hdg" to "Hướng la bàn", "wmOn" to "Watermark chìm", "logoOn" to "Hiện logo lên ảnh", "shutterSound" to "Tiếng chụp ảnh")

    private fun s(k: String) = sp.getString(k, "") ?: ""
    private fun b(k: String) = sp.getBoolean(k, true)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted(Manifest.permission.CAMERA)) startCamera()
        startSensors()
        offerVideoRecovery()
    }
    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) saveLogo(uri) }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (videoMode && !videoBusy()) {
            if (allowed) startVideo() else AlertDialog.Builder(this)
                .setMessage("Chưa cho phép micro. Bạn có thể quay video không có tiếng hoặc cấp quyền micro trong cài đặt điện thoại.")
                .setPositiveButton("Quay không tiếng") { _, _ -> startVideo() }
                .setNegativeButton("Huỷ", null).show()
        }
    }
    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shutterSound.load(MediaActionSound.SHUTTER_CLICK)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi(); loadLogo()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (videoBusy()) {
                    if (recording != null) stopVideo()
                    toast("Hãy đợi lưu video xong")
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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
        ov = TextView(this).apply { setTextColor(Color.WHITE); setShadowLayer(dp(1).toFloat(), 0f, 1f, Color.BLACK); setPadding(dp(10), dp(8), dp(12), dp(8)) }
        panelLogo = ImageView(this).apply {
            visibility = View.GONE; scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { setColor(0xEBFFFFFF.toInt()); cornerRadius = dp(4).toFloat() }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sideTv = TextView(this).apply { setTextColor(Color.WHITE); setShadowLayer(dp(1).toFloat(), 0f, 1f, Color.BLACK); typeface = Typeface.DEFAULT_BOLD; visibility = View.GONE; setPadding(dp(8), dp(6), dp(12), 0) }
        val headRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        headRow.addView(panelLogo, LinearLayout.LayoutParams(dp(40), dp(20)).apply { setMargins(dp(10), dp(8), 0, 0) })
        headRow.addView(sideTv, LinearLayout.LayoutParams(-2, -2))
        box.addView(headRow, LinearLayout.LayoutParams(-2, -2))
        box.addView(ov, LinearLayout.LayoutParams(-2, -2))
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(StampSettings.background(sp)); cornerRadius = dp(8).toFloat() }
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
            text = "📤 Chia sẻ + vị trí"; setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
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

        recordStatus = TextView(this).apply {
            gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 15f
            setPadding(dp(8), dp(6), dp(8), dp(6)); visibility = View.GONE
            setBackgroundColor(0xCC000000.toInt())
        }
        stage.addView(recordStatus, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(52) })
        val modes = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
        photoModeButton = TextView(this).apply {
            text = "Ảnh"; textSize = 17f; gravity = Gravity.CENTER; setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener { setVideoMode(false) }
        }
        videoModeButton = TextView(this).apply {
            text = "Video"; textSize = 17f; gravity = Gravity.CENTER; setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener { setVideoMode(true) }
        }
        modes.addView(photoModeButton); modes.addView(videoModeButton)
        root.addView(modes)

        fun chip(t: String, f: () -> Unit) = TextView(this).apply {
            text = t; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1B2B36.toInt()); setStroke(dp(1), 0xFF2F4452.toInt()) }
            setOnClickListener { f() }
        }
        fun cell(v: View, size: Int) = FrameLayout(this).apply { addView(v, FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER)) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, dp(22)) }
        val one = { LinearLayout.LayoutParams(0, dp(80), 1f) }
        shutter = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFF6B1A.toInt()); setStroke(dp(5), Color.WHITE) }
            setOnClickListener {
                if (videoMode) {
                    if (recording != null) stopVideo()
                    else if (!videoBusy()) {
                        if (granted(Manifest.permission.RECORD_AUDIO)) startVideo()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else shoot()
            }
        }
        bar.addView(cell(chip("🖼") { if (!videoBusy()) openGallery() else toast("Hãy đợi lưu video") }, 52), one())
        bar.addView(cell(shutter, 78), one())
        val flipCamera = ImageView(this).apply {
            setImageResource(R.drawable.ic_camera_flip); setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "Đổi camera trước/sau"
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1B2B36.toInt()); setStroke(dp(1), 0xFF2F4452.toInt()) }
            setOnClickListener { if (!videoBusy()) { front = !front; bind() } }
        }
        bar.addView(cell(flipCamera, 52), one())
        bar.addView(cell(chip("⚙") { if (!videoBusy()) settings() else toast("Hãy đợi lưu video") }, 52), one())
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        updateVideoUi()
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
        if (videoBusy()) return
        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build().also { it.setSurfaceProvider(pv.surfaceProvider) }
        val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3).setTargetRotation(lastRot).build()
        imageCapture = null
        videoCapture = null
        val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            p.unbindAll()
            if (videoMode) {
                val recorder = Recorder.Builder().setQualitySelector(
                    QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))
                ).build()
                val vc = VideoCapture.withOutput(recorder).apply { targetRotation = lastRot }
                p.bindToLifecycle(this, sel, preview, vc)
                videoCapture = vc
            } else {
                p.bindToLifecycle(this, sel, preview, ic)
                imageCapture = ic
            }
        } catch (e: Exception) { toast("Không mở được camera: ${e.message}") }
    }

    private fun setVideoMode(on: Boolean) {
        if (videoBusy() || videoMode == on) return
        videoMode = on
        updateVideoUi()
        bind()
    }

    private fun updateVideoUi() {
        photoModeButton.setTextColor(if (!videoMode) 0xFFFFD23F.toInt() else Color.GRAY)
        videoModeButton.setTextColor(if (videoMode) 0xFFFFD23F.toInt() else Color.GRAY)
        photoModeButton.isEnabled = !videoBusy(); videoModeButton.isEnabled = !videoBusy()
        shutter.isEnabled = !videoProcessing && !recordPending
        shutter.contentDescription = when {
            videoProcessing -> "Đang lưu video"
            recording != null -> "Dừng quay video"
            videoMode -> "Bắt đầu quay video"
            else -> "Chụp ảnh"
        }
        shutter.background = GradientDrawable().apply {
            shape = if (recording != null) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
            cornerRadius = dp(12).toFloat()
            setColor(if (videoMode) Color.RED else 0xFFFF6B1A.toInt())
            setStroke(dp(5), Color.WHITE)
        }
        recordStatus.visibility = if (videoBusy()) View.VISIBLE else View.GONE
        if (videoProcessing) recordStatus.text = "Đang đóng dấu và lưu video…"
        else if (recordPending) recordStatus.text = "Đang bắt đầu…"
    }

    @SuppressLint("MissingPermission")
    private fun startVideo() {
        if (videoBusy() || !videoMode) return
        val capture = videoCapture ?: run { toast("Camera chưa sẵn sàng. Hãy chọn lại Video"); return }
        val raw = File(filesDir, "video-pending").apply { mkdirs() }
            .let { File(it, "raw_${System.currentTimeMillis()}.mp4") }
        videoStart = Date(); videoLocation = loc?.let { Location(it) }
        videoLandscape = lastRot == Surface.ROTATION_90 || lastRot == Surface.ROTATION_270
        videoCode = code(videoStart)
        videoFrames.clear(); lastVideoSecond = -1
        addVideoFrame(0)
        capture.targetRotation = lastRot
        shareBtn.visibility = View.GONE
        recordPending = true; updateVideoUi()
        try {
            val pending = capture.output.prepareRecording(this, FileOutputOptions.Builder(raw).build())
            if (granted(Manifest.permission.RECORD_AUDIO)) pending.withAudioEnabled()
            recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordPending = false
                        updateVideoUi(); recordStatus.text = "● 0:00"
                    }
                    is VideoRecordEvent.Status -> {
                        val durationUs = event.recordingStats.recordedDurationNanos / 1000
                        addVideoFrame(durationUs)
                        val sec = durationUs / 1_000_000
                        recordStatus.text = String.format(Locale.US, "● %d:%02d", sec / 60, sec % 60)
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording?.close(); recording = null; recordPending = false
                        if (destroying) return@start
                        if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) {
                            videoProcessing = false
                            updateVideoUi()
                            toast("Quay video lỗi (${event.error}). Hãy thử lại; bản quay tạm được giữ trong app.")
                        } else if (raw.length() > 0) {
                            addVideoFrame(event.recordingStats.recordedDurationNanos / 1000)
                            exportVideo(raw, videoFrames.toList(), videoStart, videoLocation, videoLandscape)
                        } else {
                            raw.delete(); videoProcessing = false; updateVideoUi(); toast("Video quá ngắn hoặc không có dữ liệu")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            recording = null; recordPending = false; raw.delete()
            updateVideoUi(); toast("Không quay được: ${e.message}")
        }
    }

    private fun addVideoFrame(timeUs: Long) {
        val second = timeUs / 1_000_000
        if (second == lastVideoSecond) return
        lastVideoSecond = second
        val line = lines(Date(videoStart.time + second * 1000))
        videoFrames.add(VideoStampFrame(second * 1_000_000, Stamp(line.head, line.date, line.rows + "Mã xác thực: $videoCode")))
    }

    private fun stopVideo() {
        if (recording == null) return
        videoProcessing = true
        updateVideoUi()
        recording?.stop()
    }

    private fun exportVideo(raw: File, frames: List<VideoStampFrame>, date: Date, location: Location?, landscape: Boolean) {
        videoProcessing = true; updateVideoUi()
        val output = File(raw.parentFile, "stamped_${raw.name}")
        fun fail(message: String) {
            output.delete(); videoProcessing = false; updateVideoUi()
            if (!destroying && !isFinishing) AlertDialog.Builder(this)
                .setTitle("Chưa lưu được video")
                .setMessage("$message\nBản quay gốc còn trong app. Bạn có thể thử xử lý lại.")
                .setPositiveButton("Thử lại") { _, _ -> exportVideo(raw, frames, date, location, landscape) }
                .setNegativeButton("Để sau", null).show()
        }
        val metadata = File(raw.path + ".json")
        val logoFile = File(raw.path + ".png")
        val job = try {
            if (!metadata.exists()) {
                val data = JSONObject().put("date", date.time).put("landscape", landscape)
                    .put("watermark", if (b("wmOn")) s("wm") else "")
                    .put("background", StampSettings.background(sp))
                    .put("share", shareText(date, location))
                location?.let { data.put("lat", it.latitude).put("lon", it.longitude) }
                val timeline = JSONArray()
                frames.forEach { frame -> timeline.put(JSONObject().put("time", frame.timeUs)
                    .put("head", frame.stamp.head ?: JSONObject.NULL).put("date", frame.stamp.date ?: JSONObject.NULL)
                    .put("rows", JSONArray(frame.stamp.rows))) }
                data.put("frames", timeline)
                logoBmp?.takeIf { b("logoOn") }?.let { logo -> logoFile.outputStream().use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                metadata.writeText(data.toString())
                data
            } else JSONObject(metadata.readText())
        } catch (e: Exception) { fail(e.localizedMessage ?: "Không lưu được bản quay tạm"); return }
        val frozenLogo = BitmapFactory.decodeFile(logoFile.path)
        val frozenWatermark = job.optString("watermark")
        val frozenBackground = job.optInt("background", StampSettings.background(sp))
        videoExporter.start(this, raw, output, landscape, frames, { bitmap, line -> stamp(bitmap, line, frozenWatermark, frozenLogo, frozenBackground) }, {
            ex.execute {
                var uri: Uri? = null
                try {
                    val name = "DatvipCam_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(date) + ".mp4"
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DatvipCam")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val destination = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("Không tạo được tệp video")
                    uri = destination
                    val stream = contentResolver.openOutputStream(destination) ?: error("Không mở được tệp video")
                    stream.use { target -> output.inputStream().use { it.copyTo(target) } }
                    contentResolver.update(destination, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                    location?.let { sp.edit().putString("gps_$name", "${it.latitude},${it.longitude}").apply() }
                    raw.delete(); output.delete(); metadata.delete(); logoFile.delete()
                    runOnUiThread {
                        videoProcessing = false
                        lastName = name; lastUri = destination; lastMime = "video/mp4"; lastShare = mapLinksOnly(job.optString("share"))
                        updateVideoUi(); shareBtn.visibility = View.VISIBLE
                        toast("Đã lưu video: Movies/DatvipCam")
                    }
                } catch (e: Exception) {
                    uri?.let { contentResolver.delete(it, null, null) }
                    runOnUiThread { fail(e.localizedMessage ?: "Lỗi lưu video") }
                }
            }
        }, { message -> fail(message) })
    }

    private fun offerVideoRecovery() {
        if (recoveryChecked || videoBusy() || !granted(Manifest.permission.CAMERA)) return
        recoveryChecked = true
        val metadata = File(filesDir, "video-pending").listFiles()
            ?.filter { it.extension == "json" && File(it.path.removeSuffix(".json")).exists() }
            ?.minByOrNull { it.lastModified() } ?: return
        AlertDialog.Builder(this).setTitle("Video chưa lưu xong")
            .setMessage("Có bản quay chưa đóng dấu xong. Bạn muốn tiếp tục lưu không?")
            .setPositiveButton("Tiếp tục lưu") { _, _ ->
                try {
                    val data = JSONObject(metadata.readText())
                    val timeline = data.getJSONArray("frames")
                    val frames = (0 until timeline.length()).map { index ->
                        val frame = timeline.getJSONObject(index)
                        val rows = frame.getJSONArray("rows")
                        VideoStampFrame(frame.getLong("time"), Stamp(
                            if (frame.isNull("head")) null else frame.getString("head"),
                            if (frame.isNull("date")) null else frame.getString("date"),
                            (0 until rows.length()).map { rows.getString(it) }))
                    }
                    val location = if (data.has("lat")) Location("saved").apply {
                        latitude = data.getDouble("lat"); longitude = data.getDouble("lon")
                    } else null
                    videoMode = true
                    bind()
                    exportVideo(File(metadata.path.removeSuffix(".json")), frames, Date(data.getLong("date")), location, data.getBoolean("landscape"))
                } catch (e: Exception) { toast("Không đọc được bản quay tạm: ${e.message}") }
            }.setNegativeButton("Để sau", null).show()
    }

    private fun shoot() {
        val ic = imageCapture ?: return
        ic.takePicture(ex, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                runOnUiThread { if (!destroying && b("shutterSound")) shutterSound.play(MediaActionSound.SHUTTER_CLICK) }
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
        lastMime = "image/jpeg"
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
        (panel.background as GradientDrawable).setColor(StampSettings.background(sp))
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

    private fun stamp(bmp: Bitmap, L: Stamp, watermark: String = if (b("wmOn")) s("wm") else "", lg: Bitmap? = logoBmp?.takeIf { b("logoOn") }, background: Int = StampSettings.background(sp)) {
        val c = Canvas(bmp); val w = bmp.width.toFloat(); val h = bmp.height.toFloat(); val u = minOf(w, h) / 100f
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        if (watermark.isNotEmpty()) {
            tp.color = 0x44FFFFFF; tp.textSize = u * 4f; tp.typeface = Typeface.DEFAULT_BOLD; tp.textAlign = Paint.Align.CENTER
            val tw = tp.measureText(watermark) + u * 12; val sp2 = u * 16; val dd = maxOf(w, h)
            c.save(); c.translate(w / 2, h / 2); c.rotate(-30f)
            var y = -dd; var row = 0
            while (y < dd) { var x = -dd + if (row % 2 == 1) tw / 2 else 0f; while (x < dd) { c.drawText(watermark, x, y, tp); x += tw }; y += sp2; row++ }
            c.restore(); tp.textAlign = Paint.Align.LEFT
        }
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
        val sideTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = u * 3.4f; color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(u * .15f, 0f, u * .08f, Color.BLACK) }
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
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background; setShadowLayer(u * 1.2f, 0f, u * .3f, 0x80000000.toInt()) }
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
            tp.setShadowLayer(u * .15f, 0f, u * .08f, Color.BLACK)
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
    override fun onPause() {
        if (recording != null) stopVideo()
        super.onPause(); orient.disable()
        getSystemService(LocationManager::class.java).removeUpdates(this)
        getSystemService(SensorManager::class.java).unregisterListener(this)
    }
    override fun onDestroy() {
        destroying = true
        recording?.close(); recording = null
        videoExporter.cancel()
        shutterSound.release()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
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

    private fun backgroundSettings() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(10)) }
        val preview = TextView(this).apply {
            text = "12:34:56\nNhiệm vụ · Ghi chú mẫu"; textSize = 19f; setTextColor(Color.WHITE)
            setShadowLayer(dp(1).toFloat(), 0f, 1f, Color.BLACK); setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        col.addView(preview)
        col.addView(TextView(this).apply { text = "Chọn màu tuỳ ý bằng mã màu hoặc 3 thanh màu. Nền tối mờ mặc định, độ đậm tối thiểu 40% để dễ đọc chữ." })
        val hex = EditText(this).apply { hint = "Mã màu #RRGGBB"; setSingleLine() }
        col.addView(hex)
        val rgb = (0..2).map { SeekBar(this).apply { max = 255 } }
        val opacity = SeekBar(this).apply { max = 60 }
        val opacityLabel = TextView(this)
        fun update() {
            val alpha = opacity.progress + 40
            val value = Color.rgb(rgb[0].progress, rgb[1].progress, rgb[2].progress)
            hex.setText(String.format(Locale.US, "#%06X", value and 0xFFFFFF))
            opacityLabel.text = "Độ đậm nền: $alpha%"
            preview.background = GradientDrawable().apply { setColor(Color.argb(alpha * 255 / 100, Color.red(value), Color.green(value), Color.blue(value))); cornerRadius = dp(8).toFloat() }
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) update() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        fun choose(color: Int, alpha: Int) {
            rgb[0].progress = Color.red(color); rgb[1].progress = Color.green(color); rgb[2].progress = Color.blue(color)
            opacity.progress = (alpha - 40).coerceIn(0, 60); update()
        }
        rgb.forEachIndexed { i, bar ->
            col.addView(TextView(this).apply { text = listOf("Đỏ", "Lục", "Lam")[i] }); col.addView(bar); bar.setOnSeekBarChangeListener(listener)
        }
        col.addView(opacityLabel); col.addView(opacity); opacity.setOnSeekBarChangeListener(listener)
        col.addView(Button(this).apply { text = "Xem màu theo mã đã nhập"; setOnClickListener {
            val code = hex.text.toString().trim().removePrefix("#")
            if (!code.matches(Regex("[0-9a-fA-F]{6}"))) hex.error = "Nhập 6 ký tự màu, ví dụ #0E1A24"
            else choose(Color.parseColor("#$code"), opacity.progress + 40)
        } })
        val initial = StampSettings.background(sp)
        choose(initial, (sp.getFloat("stampBackgroundOpacity", StampSettings.DEFAULT_OPACITY).coerceIn(.4f, 1f) * 100).toInt())
        val dialog = AlertDialog.Builder(this).setTitle("Nền khung thông tin").setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("Lưu", null).setNegativeButton("Huỷ", null).setNeutralButton("Mặc định", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = hex.text.toString().trim().removePrefix("#")
                if (!code.matches(Regex("[0-9a-fA-F]{6}"))) hex.error = "Nhập 6 ký tự màu, ví dụ #0E1A24"
                else {
                    sp.edit().putString("stampBackgroundHex", code.uppercase(Locale.US))
                        .putFloat("stampBackgroundOpacity", (opacity.progress + 40) / 100f).apply()
                    refresh(); dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { choose(0xFF0E1A24.toInt(), 60) }
        }
        dialog.show()
    }

    private fun settings() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        col.addView(Button(this).apply { text = "Màu và độ đậm nền khung thông tin"; setOnClickListener { backgroundSettings() } })
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
    private fun directionsLink(lat: Double, lon: Double): String =
        "https://www.google.com/maps/dir/?api=1&destination=" + String.format(Locale.US, "%.6f,%.6f", lat, lon) + "&travelmode=driving"

    private fun mapLinksOnly(text: String): String =
        Regex("""https://www\.google\.com/maps/dir/\?api=1&destination=-?\d+(?:\.\d+)?,-?\d+(?:\.\d+)?&travelmode=driving""")
            .findAll(text).map { it.value }.distinct().joinToString("\n")

    private fun shareText(d: Date, l: Location?): String =
        l?.let { directionsLink(it.latitude, it.longitude) } ?: ""
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
        val u = sharableUri() ?: run { toast("Chưa có ảnh hoặc video để chia sẻ"); return }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("datvip", lastShare))
        val i = Intent(Intent.ACTION_SEND).apply {
            type = lastMime; putExtra(Intent.EXTRA_STREAM, u); if (lastShare.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, lastShare)
            clipData = ClipData.newRawUri("", u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        toast(if (lastShare.isEmpty()) "Chưa có GPS: chỉ chia sẻ tệp" else "Đã chép link chỉ đường. Nếu app không hiện link, hãy dán vào")
        startActivity(Intent.createChooser(i, "Chia sẻ + vị trí"))
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
        if (videoBusy()) { toast("Hãy đợi lưu video"); return }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val keys = NotePresets.fields
        val editors = keys.associate { (key, hint) -> key to EditText(this).apply {
            this.hint = hint; setText(s(key)); if (key != "note") setSingleLine()
        } }
        fun draft() = editors.mapValues { it.value.text.toString().trim() }
        fun use(values: Map<String, String>) {
            val edit = sp.edit()
            keys.forEach { (key, _) ->
                val value = values[key] ?: ""
                edit.putString(key, value); editors.getValue(key).setText(value)
            }
            edit.apply(); refresh()
        }
        val saved = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = "Lưu tối đa 10 mẫu: nhiệm vụ, ghi chú và 4 trường tuỳ biến. GPS vẫn lấy mới." })
        col.addView(saved)
        lateinit var redraw: () -> Unit
        fun askName(initial: String, apply: (String) -> Unit) {
            val input = EditText(this).apply { hint = "Tên mẫu"; setText(initial); setSingleLine() }
            val dialog = AlertDialog.Builder(this).setTitle("Tên mẫu ghi chú").setView(input)
                .setPositiveButton("Lưu", null).setNegativeButton("Huỷ", null).create()
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = input.text.toString().trim().take(60)
                if (title.isEmpty()) input.error = "Hãy nhập tên mẫu"
                else { apply(title); dialog.dismiss() }
            } }
            dialog.show()
        }
        redraw = {
            saved.removeAllViews()
            val templates = NotePresets.load(sp)
            saved.addView(TextView(this).apply { text = "Mẫu đã lưu (${templates.size}/10)"; typeface = Typeface.DEFAULT_BOLD })
            templates.forEach { template -> saved.addView(Button(this).apply {
                text = template.name
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle(template.name)
                        .setItems(arrayOf("Dùng mẫu", "Cập nhật từ nội dung đang nhập", "Đổi tên", "Xoá mẫu")) { _, action ->
                            val current = NotePresets.load(sp)
                            when (action) {
                                0 -> { use(template.values); toast("Đã dùng mẫu: ${template.name}") }
                                1 -> { NotePresets.save(sp, current.map { if (it.id == template.id) it.copy(values = draft()) else it }); redraw() }
                                2 -> askName(template.name) { title ->
                                    if (current.any { it.id != template.id && it.name.equals(title, true) }) toast("Tên mẫu đã có")
                                    else { NotePresets.save(sp, current.map { if (it.id == template.id) it.copy(name = title) else it }); redraw() }
                                }
                                3 -> AlertDialog.Builder(this@MainActivity).setMessage("Xoá mẫu ${template.name}?")
                                    .setPositiveButton("Xoá") { _, _ -> NotePresets.save(sp, current.filter { it.id != template.id }); redraw() }
                                    .setNegativeButton("Huỷ", null).show()
                            }
                        }.show()
                }
            }) }
        }
        redraw()
        keys.forEach { (key, _) -> col.addView(editors.getValue(key)) }
        col.addView(Button(this).apply {
            text = "Lưu nội dung thành mẫu mới"
            setOnClickListener {
                val values = draft()
                if (values.values.all { it.isBlank() }) toast("Hãy nhập nội dung trước khi lưu mẫu")
                else if (NotePresets.load(sp).size >= 10) toast("Đã đủ 10 mẫu. Hãy cập nhật hoặc xoá một mẫu")
                else askName(values["note"].orEmpty().ifEmpty { values["proj"].orEmpty() }.take(40)) { title ->
                    val templates = NotePresets.load(sp)
                    if (templates.any { it.name.equals(title, true) }) toast("Tên mẫu đã có")
                    else if (templates.size < 10) { NotePresets.save(sp, templates + NotePreset(UUID.randomUUID().toString(), title, values)); redraw(); toast("Đã lưu mẫu") }
                }
            }
        })
        AlertDialog.Builder(this).setTitle("Ghi chú nhanh và mẫu đã lưu").setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("Dùng nội dung") { _, _ -> use(draft()) }.setNegativeButton("Đóng", null).show()
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
        val videos = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(videos, arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.DISPLAY_NAME),
            MediaStore.Video.Media.RELATIVE_PATH + " LIKE ? AND " + MediaStore.Video.Media.IS_PENDING + " = 0",
            arrayOf("%DatvipCam%"), MediaStore.Video.Media.DATE_ADDED + " DESC")?.use { c ->
            while (c.moveToNext()) {
                val item = Item(ContentUris.withAppendedId(videos, c.getLong(0)), c.getLong(1) * 1000, true)
                val gps = s("gps_${c.getString(2)}").split(',')
                if (gps.size == 2) { item.lat = gps[0].toDoubleOrNull(); item.lon = gps[1].toDoubleOrNull() }
                items.add(item)
            }
        }
        items.sortByDescending { it.time }
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0B141B.toInt()); setPadding(0, dp(28), 0, 0) }
        val title = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val checks = ArrayList<TextView>(); val imgs = ArrayList<ImageView>(); var lastTap = -1
        fun upd() { title.text = "Ảnh & video (${items.count { it.sel }}/${items.size})"; checks.forEachIndexed { i, t -> t.visibility = if (items[i].sel) View.VISIBLE else View.GONE } }
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
            if (m.video) cell.addView(TextView(this).apply {
                text = "▶"; textSize = 24f; setTextColor(Color.WHITE); setShadowLayer(4f, 1f, 1f, Color.BLACK)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(8), 0, 0, dp(8)) })
            cell.addView(ck, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.END or Gravity.TOP).apply { setMargins(0, dp(6), dp(6), 0) })
            cell.setOnClickListener { m.sel = !m.sel; lastTap = i; upd() }
            cell.setOnLongClickListener {
                try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, if (m.video) "video/mp4" else "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                catch (e: Exception) { toast("Không có ứng dụng mở tệp này") }; true
            }
            imgs.add(iv); checks.add(ck)
            grid.addView(cell, GridLayout.LayoutParams().apply { width = size; height = size })
        }
        root.addView(ScrollView(this).apply { addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
        thread {
            items.forEachIndexed { i, m ->
                val bm = try { contentResolver.loadThumbnail(m.uri, Size(300, 300), null) } catch (e: Exception) { null }
                if (!m.video) try { contentResolver.openInputStream(m.uri)?.use { ins -> ExifInterface(ins).latLong?.let { m.lat = it[0]; m.lon = it[1] } } } catch (e: Exception) {}
                runOnUiThread { if (bm != null) imgs[i].setImageBitmap(bm) }
            }
        }
        dlg.setContentView(root); dlg.show(); upd()
        if (items.isEmpty()) toast("Chưa có ảnh hoặc video nào")
    }
    private fun batchText(sel: List<Item>): String {
        val groups = ArrayList<MutableList<Item>>()
        for (item in sel) {
            val group = groups.firstOrNull { nearBy(it[0], item) }
            if (group != null) group.add(item) else groups.add(mutableListOf(item))
        }
        return groups.mapNotNull { group ->
            val lat = group[0].lat; val lon = group[0].lon
            if (lat != null && lon != null) directionsLink(lat, lon) else null
        }.distinct().joinToString("\n")
    }
    private fun shareBatch(sel: List<Item>) {
        if (sel.isEmpty()) { toast("Hãy chọn ít nhất một ảnh hoặc video"); return }
        val txt = batchText(sel)
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("datvip", txt))
        toast("Đang chuẩn bị ${sel.size} tệp…")
        thread {
            try {
                val dir = File(cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val uris = ArrayList<Uri>()
                sel.forEachIndexed { i, m ->
                    val f = File(dir, "DatvipCam_${i + 1}_${m.time}." + if (m.video) "mp4" else "jpg")
                    contentResolver.openInputStream(m.uri)?.use { ins -> f.outputStream().use { o -> ins.copyTo(o) } }
                    uris.add(FileProvider.getUriForFile(this, "$packageName.fileprovider", f))
                }
                val it2 = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = if (sel.all { it.video }) "video/*" else if (sel.none { it.video }) "image/*" else "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); if (txt.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, txt)
                    val cd = ClipData.newRawUri("", uris[0]); uris.drop(1).forEach { u -> cd.addItem(ClipData.Item(u)) }; clipData = cd
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    toast(if (txt.isEmpty()) "Không có GPS: chỉ chia sẻ tệp" else "Đã chép link chỉ đường. Nếu Zalo không hiện link, hãy dán vào")
                    startActivity(Intent.createChooser(it2, "Chia sẻ hàng loạt"))
                }
            } catch (e: Exception) { toast("Không chia sẻ được: ${e.message}") }
        }
    }
    private fun openLast() {
        val u = lastUri
        val i = if (u != null) Intent(Intent.ACTION_VIEW).setDataAndType(u, lastMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        else Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try { startActivity(i) } catch (e: Exception) { toast("Không mở được thư viện") }
    }
    private fun toast(t: String) = runOnUiThread { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }
}
