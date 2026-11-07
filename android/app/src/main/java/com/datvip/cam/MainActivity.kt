package com.datvip.cam

import android.Manifest
import android.annotation.SuppressLint
import android.app.RecoverableSecurityException
import androidx.activity.result.IntentSenderRequest
import android.app.Activity
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
class Item(val uri: Uri, val time: Long, val video: Boolean = false) { var lat: Double? = null; var lon: Double? = null; var sel = false }

@UnstableApi
class MainActivity : AppCompatActivity(), LocationListener, SensorEventListener {
    private lateinit var adFree: AdFreePurchase
    private var banner: CameraBanner? = null
    private var lastPurchaseMessage = ""
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
    private var settingsScreen: StampSettingsScreen? = null
    private val videoFrames = ArrayList<VideoStampFrame>()
    private var videoStart = Date()
    private var videoLocation: Location? = null
    private var videoLandscape = false
    private var videoCode = ""
    private var lastVideoSecond = -1L
    private var recoveryChecked = false
    private fun videoBusy() = recordPending || recording != null || videoProcessing
    private lateinit var pv: PreviewView
    private lateinit var stampOverlay: StampOverlayView
    private lateinit var gpsTv: TextView
    private lateinit var stage: FrameLayout
    private lateinit var frame: FrameLayout
    private lateinit var layer: FrameLayout
    private var lastShare = ""
    private var lastName = ""
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
    private val days get() = arrayOf(Language.text(this@MainActivity, "Chủ nhật"), Language.text(this@MainActivity, "Thứ hai"), Language.text(this@MainActivity, "Thứ ba"), Language.text(this@MainActivity, "Thứ tư"), Language.text(this@MainActivity, "Thứ năm"), Language.text(this@MainActivity, "Thứ sáu"), Language.text(this@MainActivity, "Thứ bảy"))
    private val boolKeys get() = listOf("time" to Language.text(this@MainActivity, "Ngày giờ"), "gps" to Language.text(this@MainActivity, "Toạ độ GPS"), "alt" to Language.text(this@MainActivity, "Độ cao"), "hdg" to Language.text(this@MainActivity, "Hướng la bàn"), "wmOn" to Language.text(this@MainActivity, "Watermark chìm"), "logoOn" to Language.text(this@MainActivity, "Hiện logo lên ảnh"), "shutterSound" to Language.text(this@MainActivity, "Tiếng chụp ảnh"))

    private fun s(k: String) = sp.getString(k, "") ?: ""
    private fun b(k: String) = sp.getBoolean(k, true)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted(Manifest.permission.CAMERA)) startCamera()
        startSensors()
        offerVideoRecovery()
    }
    private var pendingMediaDelete: List<Uri> = emptyList()
    private var onMediaDeleteFinished: (() -> Unit)? = null
    private var mediaDeleteIndex = 0
    private var awaitingMediaConsent = false
    private val consentAttempted = mutableSetOf<Uri>()

    private fun mediaExists(uri: Uri): Boolean? =
        try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null, null, null
            )?.use { it.moveToFirst() }
        } catch (_: Exception) {
            null
        }

    private fun finishMediaDeletion() {
        val remaining = pendingMediaDelete.count { mediaExists(it) != false }
        val callback = onMediaDeleteFinished

        pendingMediaDelete = emptyList()
        onMediaDeleteFinished = null
        mediaDeleteIndex = 0
        awaitingMediaConsent = false
        consentAttempted.clear()

        if (remaining == 0) {
            toast("Media deletion verified")
        } else {
            toast("Media not deleted or not verifiable: $remaining")
        }

        callback?.invoke()
    }

    private val mediaDeleteLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            awaitingMediaConsent = false

            if (result.resultCode != Activity.RESULT_OK) {
                toast("Media deletion cancelled")
                val callback = onMediaDeleteFinished
                pendingMediaDelete = emptyList()
                onMediaDeleteFinished = null
                mediaDeleteIndex = 0
                consentAttempted.clear()
                callback?.invoke()
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                deleteNextAndroid10()
            } else {
                finishMediaDeletion()
            }
        }

    private fun deleteNextAndroid10() {
        while (mediaDeleteIndex < pendingMediaDelete.size) {
            val uri = pendingMediaDelete[mediaDeleteIndex]

            if (mediaExists(uri) == false) {
                mediaDeleteIndex++
                continue
            }

            try {
                contentResolver.delete(uri, null, null)
                mediaDeleteIndex++
            } catch (error: RecoverableSecurityException) {
                if (awaitingMediaConsent) return

                if (!consentAttempted.add(uri)) {
                    mediaDeleteIndex++
                    continue
                }

                awaitingMediaConsent = true
                try {
                    mediaDeleteLauncher.launch(
                        IntentSenderRequest.Builder(
                            error.userAction.actionIntent.intentSender
                        ).build()
                    )
                } catch (_: Exception) {
                    awaitingMediaConsent = false
                    mediaDeleteIndex++
                }
                return
            } catch (_: Exception) {
                mediaDeleteIndex++
            }
        }

        finishMediaDeletion()
    }
    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) saveLogo(uri) }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (videoMode && !videoBusy()) {
            if (allowed) startVideo() else AlertDialog.Builder(this)
                .setMessage(Language.text(this@MainActivity, "Chưa cho phép micro. Bạn có thể quay video không có tiếng hoặc cấp quyền micro trong cài đặt điện thoại."))
                .setPositiveButton(Language.text(this@MainActivity, "Quay không tiếng")) { _, _ -> startVideo() }
                .setNegativeButton(Language.text(this@MainActivity, "Huỷ"), null).show()
        }
    }
    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shutterSound.load(MediaActionSound.SHUTTER_CLICK)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        NotePresets.installDefaults(sp)
        buildUi(); loadLogo()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (videoBusy()) {
                    if (recording != null) stopVideo()
                    toast(Language.text(this@MainActivity, "Hãy đợi lưu video xong"))
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
        stampOverlay = StampOverlayView(this)
        layer.addView(stampOverlay, FrameLayout.LayoutParams(-1, -1))
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
        setGpsPill(false, Language.text(this@MainActivity, "Đang định vị…"))
        stage.addView(gpsTv, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(12), 0, 0) })
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
            text = Language.text(this@MainActivity, "Ảnh"); textSize = 17f; gravity = Gravity.CENTER; setPadding(dp(24), dp(10), dp(24), dp(10))
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
        bar.addView(cell(chip("🖼") { if (!videoBusy()) openGallery() else toast(Language.text(this@MainActivity, "Hãy đợi lưu video")) }, 52), one())
        bar.addView(cell(shutter, 78), one())
        val flipCamera = ImageView(this).apply {
            setImageResource(R.drawable.ic_camera_flip); setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = Language.text(this@MainActivity, "Đổi camera trước/sau")
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1B2B36.toInt()); setStroke(dp(1), 0xFF2F4452.toInt()) }
            setOnClickListener { if (!videoBusy()) { front = !front; bind() } }
        }
        bar.addView(cell(flipCamera, 52), one())
        bar.addView(cell(chip("⚙") { if (!videoBusy()) settings() else toast(Language.text(this@MainActivity, "Hãy đợi lưu video")) }, 52), one())
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        val adSlot = FrameLayout(this).apply { visibility = View.GONE }
        root.addView(adSlot, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        adFree = AdFreePurchase(this) {
            banner?.refresh()
            if (adFree.message.isNotBlank() && adFree.message != lastPurchaseMessage) { lastPurchaseMessage = adFree.message; toast(adFree.message) }
        }
        banner = CameraBanner(this, adSlot, adFree)
        adFree.start()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
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
        } catch (e: Exception) { toast(Language.text(this@MainActivity, "Không mở được camera: {0}", (e.message))) }
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
            videoProcessing -> Language.text(this@MainActivity, "Đang lưu video")
            recording != null -> Language.text(this@MainActivity, "Dừng quay video")
            videoMode -> Language.text(this@MainActivity, "Bắt đầu quay video")
            else -> Language.text(this@MainActivity, "Chụp ảnh")
        }
        shutter.background = GradientDrawable().apply {
            shape = if (recording != null) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
            cornerRadius = dp(12).toFloat()
            setColor(if (videoMode) Color.RED else 0xFFFF6B1A.toInt())
            setStroke(dp(5), Color.WHITE)
        }
        recordStatus.visibility = if (videoBusy()) View.VISIBLE else View.GONE
        if (videoProcessing) recordStatus.text = Language.text(this@MainActivity, "Đang đóng dấu và lưu video…")
        else if (recordPending) recordStatus.text = Language.text(this@MainActivity, "Đang bắt đầu…")
    }

    @SuppressLint("MissingPermission")
    private fun startVideo() {
        if (videoBusy() || !videoMode) return
        val capture = videoCapture ?: run { toast(Language.text(this@MainActivity, "Camera chưa sẵn sàng. Hãy chọn lại Video")); return }
        val raw = File(filesDir, "video-pending").apply { mkdirs() }
            .let { File(it, "raw_${System.currentTimeMillis()}.mp4") }
        videoStart = Date(); videoLocation = loc?.let { Location(it) }
        videoLandscape = lastRot == Surface.ROTATION_90 || lastRot == Surface.ROTATION_270
        videoCode = code(videoStart)
        videoFrames.clear(); lastVideoSecond = -1
        addVideoFrame(0)
        capture.targetRotation = lastRot
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
                            toast(Language.text(this@MainActivity, "Quay video lỗi ({0}). Hãy thử lại; bản quay tạm được giữ trong app.", (event.error)))
                        } else if (raw.length() > 0) {
                            addVideoFrame(event.recordingStats.recordedDurationNanos / 1000)
                            exportVideo(raw, videoFrames.toList(), videoStart, videoLocation, videoLandscape)
                        } else {
                            raw.delete(); videoProcessing = false; updateVideoUi(); toast(Language.text(this@MainActivity, "Video quá ngắn hoặc không có dữ liệu"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            recording = null; recordPending = false; raw.delete()
            updateVideoUi(); toast(Language.text(this@MainActivity, "Không quay được: {0}", (e.message)))
        }
    }

    private fun addVideoFrame(timeUs: Long) {
        val second = timeUs / 1_000_000
        if (second == lastVideoSecond) return
        lastVideoSecond = second
        val line = lines(Date(videoStart.time + second * 1000))
        videoFrames.add(VideoStampFrame(second * 1_000_000, Stamp(line.head, line.date, line.rows + Language.text(this@MainActivity, "Mã xác thực: {0}", (videoCode)))))
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
                .setTitle(Language.text(this@MainActivity, "Chưa lưu được video"))
                .setMessage(Language.text(this@MainActivity, "{0}\nBản quay gốc còn trong app. Bạn có thể thử xử lý lại.", (message)))
                .setPositiveButton(Language.text(this@MainActivity, "Thử lại")) { _, _ -> exportVideo(raw, frames, date, location, landscape) }
                .setNegativeButton(Language.text(this@MainActivity, "Để sau"), null).show()
        }
        val metadata = File(raw.path + ".json")
        val logoFile = File(raw.path + ".png")
        val job = try {
            if (!metadata.exists()) {
                val data = JSONObject().put("date", date.time).put("landscape", landscape)
                    .put("watermark", if (b("wmOn")) s("wm") else "")
                    .put("background", StampSettings.background(sp))
                    .put("stampSize", StampSettings.appearance(sp).size.toDouble())
                    .put("logoOpacity", StampSettings.appearance(sp).logoOpacity.toDouble())
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
        } catch (e: Exception) { fail(e.localizedMessage ?: Language.text(this@MainActivity, "Không lưu được bản quay tạm")); return }
        val frozenLogo = BitmapFactory.decodeFile(logoFile.path)?.let { trimLogo(it) }
        val frozenWatermark = job.optString("watermark")
        val frozenBackground = job.optInt("background", StampSettings.background(sp))
        val frozenAppearance = StampAppearance(frozenBackground,
            job.optDouble("stampSize", .75).toFloat().coerceIn(.5f, 1.2f), job.optDouble("logoOpacity", 1.0).toFloat().coerceIn(0f, 1f))
        videoExporter.start(this, raw, output, landscape, frames, { bitmap, line -> stamp(bitmap, line, frozenWatermark, frozenLogo, frozenBackground, frozenAppearance) }, {
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
                        ?: error(Language.text(this@MainActivity, "Không tạo được tệp video"))
                    uri = destination
                    val stream = contentResolver.openOutputStream(destination) ?: error(Language.text(this@MainActivity, "Không mở được tệp video"))
                    stream.use { target -> output.inputStream().use { it.copyTo(target) } }
                    contentResolver.update(destination, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                    location?.let { sp.edit().putString("gps_$name", "${it.latitude},${it.longitude}").apply() }
                    raw.delete(); output.delete(); metadata.delete(); logoFile.delete()
                    runOnUiThread {
                        videoProcessing = false
                        lastName = name; lastUri = destination; lastMime = "video/mp4"; lastShare = mapLinksOnly(job.optString("share"))
                        updateVideoUi()
                        toast(Language.text(this@MainActivity, "Đã lưu video: Movies/DatvipCam"))
                    }
                } catch (e: Exception) {
                    uri?.let { contentResolver.delete(it, null, null) }
                    runOnUiThread { fail(e.localizedMessage ?: Language.text(this@MainActivity, "Lỗi lưu video")) }
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
        AlertDialog.Builder(this).setTitle(Language.text(this@MainActivity, "Video chưa lưu xong"))
            .setMessage(Language.text(this@MainActivity, "Có bản quay chưa đóng dấu xong. Bạn muốn tiếp tục lưu không?"))
            .setPositiveButton(Language.text(this@MainActivity, "Tiếp tục lưu")) { _, _ ->
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
                } catch (e: Exception) { toast(Language.text(this@MainActivity, "Không đọc được bản quay tạm: {0}", (e.message))) }
            }.setNegativeButton(Language.text(this@MainActivity, "Để sau"), null).show()
    }

    private fun shoot() {
        val ic = imageCapture ?: return
        ic.takePicture(ex, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                runOnUiThread { if (!destroying && b("shutterSound")) shutterSound.play(MediaActionSound.SHUTTER_CLICK) }
                try { process(image) } catch (e: Exception) { toast(Language.text(this@MainActivity, "Lỗi lưu ảnh: {0}", (e.message))) } finally { image.close() }
            }
            override fun onError(e: ImageCaptureException) { toast(Language.text(this@MainActivity, "Chụp lỗi: {0}", (e.message))) }
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
        val rows = L.rows + (Language.text(this@MainActivity, "Mã xác thực: ") + code(d))
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
        gl?.let { sp.edit().putString("gps_$name", "${it.latitude},${it.longitude}").apply() }
        lastShare = shareText(d, loc)
        toast(Language.text(this@MainActivity, "Đã lưu: Pictures/DatvipCam"))
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
        if (s("proj").isNotEmpty()) rows.add(Language.text(this@MainActivity, "Nhiệm vụ: ") + s("proj"))
        val addr = if (s("addr").isNotEmpty()) s("addr") else autoAddr
        if (addr.isNotEmpty()) rows.add("\u200C" + addr)
        for (i in 1..4) { val lb = s("f${i}l"); val v = s("f${i}v"); if (v.isNotEmpty()) rows.add("\u200B" + (if (lb.isNotEmpty()) "$lb: $v" else v)) }
        val l = loc
        if (b("gps")) rows.add(if (l != null) "📍 " + String.format(Locale.US, Language.text(this@MainActivity, "Vĩ độ %.6f, Kinh độ %.6f (±%dm)"), l.latitude, l.longitude, l.accuracy.toInt()) else Language.text(this@MainActivity, "📍 Chưa có toạ độ GPS"))
        if (b("alt") && l != null && l.hasAltitude()) rows.add(Language.text(this@MainActivity, "Độ cao {0} m", (l.altitude.toInt())))
        val h = hdg
        if (b("hdg") && h != null) rows.add(Language.text(this@MainActivity, "Hướng {0}° {1}", (h.toInt()), (Language.direction(this, h))))
        if (s("note").isNotEmpty()) rows.add(Language.text(this@MainActivity, "Ghi chú: ") + s("note"))
        return Stamp(head, date, rows)
    }
    private fun code(d: Date): String {
        val l = loc
        val raw = "${d.time}|" + (if (l != null) "%.6f,%.6f".format(Locale.US, l.latitude, l.longitude) else "-")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).take(4).joinToString("") { "%02X".format(it) }
    }
    private fun refresh() {
        val now = Date(); val content = lines(now)
        val verification = if (recording != null || recordPending) videoCode else code(now)
        stampOverlay.stamp = Stamp(content.head, content.date, content.rows + Language.text(this@MainActivity, "Mã xác thực: {0}", (verification)))
        stampOverlay.logo = logoBmp?.takeIf { b("logoOn") }
        stampOverlay.appearance = StampSettings.appearance(sp)
        stampOverlay.watermark = if (b("wmOn")) s("wm") else ""
        stampOverlay.invalidate()
    }

    private fun stamp(bmp: Bitmap, content: Stamp, watermark: String = if (b("wmOn")) s("wm") else "",
                      lg: Bitmap? = logoBmp?.takeIf { b("logoOn") }, background: Int = StampSettings.background(sp),
                      appearance: StampAppearance = StampSettings.appearance(sp)) {
        AndroidStampRenderer.draw(Canvas(bmp), bmp.width, bmp.height, content, lg, appearance.copy(background = background), watermark)
    }

    /* ---------- GPS, la bàn ---------- */
    @SuppressLint("MissingPermission")
    private fun startSensors() {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            val lm = getSystemService(LocationManager::class.java)
            for (pr in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER))
                if (lm.isProviderEnabled(pr)) lm.requestLocationUpdates(pr, 2000L, 0f, this)
        } else gpsTv.text = Language.text(this@MainActivity, "Chưa cấp quyền vị trí")
        val sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    override fun onResume() { super.onResume(); if (granted(Manifest.permission.CAMERA) && provider == null) startCamera(); if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) startSensors(); orient.enable(); banner?.resume(); if (::adFree.isInitialized) adFree.restore(false) }
    override fun onPause() {
        if (recording != null) stopVideo()
        banner?.pause()
        super.onPause(); orient.disable()
        getSystemService(LocationManager::class.java).removeUpdates(this)
        getSystemService(SensorManager::class.java).unregisterListener(this)
    }
    override fun onDestroy() {
        destroying = true
        banner?.close()
        if (::adFree.isInitialized) adFree.close()
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
        thread { try { @Suppress("DEPRECATION") val a = Geocoder(this, Language.locale(this)).getFromLocation(l.latitude, l.longitude, 1); autoAddr = a?.firstOrNull()?.getAddressLine(0) ?: "" } catch (e: Exception) {} }
    }
    private fun locateDialog() {
        val l = loc
        if (l == null) { toast(Language.text(this@MainActivity, "Chưa có toạ độ. Hãy bật GPS và chờ vài giây")); return }
        geocode(l, true)
        val addr = if (s("addr").isNotEmpty()) s("addr") else autoAddr
        val coord = String.format(Locale.US, "%.6f, %.6f", l.latitude, l.longitude)
        val msg = Language.text(this@MainActivity, "Toạ độ: {0}\nSai số: ±{1} m", (coord), (l.accuracy.toInt())) +
            (if (l.hasAltitude()) Language.text(this@MainActivity, "\nĐộ cao: {0} m", (l.altitude.toInt())) else "") +
            (hdg?.let { Language.text(this@MainActivity, "\nHướng: {0}° {1}", (it.toInt()), (Language.direction(this, it))) } ?: "") +
            Language.text(this@MainActivity, "\nĐịa chỉ: ") + (if (addr.isNotEmpty()) addr else Language.text(this@MainActivity, "đang tra cứu… (cần có mạng, mở lại sau vài giây)"))
        AlertDialog.Builder(this).setTitle(Language.text(this@MainActivity, "📍 Vị trí hiện tại")).setMessage(msg)
            .setNeutralButton(Language.text(this@MainActivity, "Mở bản đồ")) { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$coord(Datvip Cam)"))) } catch (e: Exception) { toast(Language.text(this@MainActivity, "Không có app bản đồ")) }
            }
            .setNegativeButton(Language.text(this@MainActivity, "Sao chép")) { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("toa-do", coord)); toast(Language.text(this@MainActivity, "Đã sao chép toạ độ"))
            }
            .setPositiveButton(Language.text(this@MainActivity, "Chia sẻ")) { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText(Date(), l)) }, Language.text(this@MainActivity, "Chia sẻ vị trí")))
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

    private fun settings() = openStampSettings(false)
    private fun openStampSettings(quick: Boolean) {
        settingsScreen = StampSettingsScreen(this, sp, changed = { refresh() },
            reloadLogo = { loadLogo() }, logo = { logoBmp },
            pickLogo = { pickLogo.launch("image/*") },
            resetLogo = { File(filesDir, "logo.png").delete(); loadLogo(); refresh() },
            about = { showAppInfo() }, purchases = { showPurchaseOptions() }, privacyOptions = { banner?.privacyOptions() }, quick = quick)
        settingsScreen?.show()
    }

    private fun showAppInfo() {
        val info = packageManager.getPackageInfo(packageName, 0)
        val version = info.versionName ?: "1.0.1"
        val detail = Language.text(this@MainActivity, "Mã bản dựng: ") + info.longVersionCode + " · " + packageName
        val content = TextView(this).apply {
            text = Language.name(this@MainActivity) + "\n" + Language.text(this@MainActivity, "Phiên bản: {0}", version) + "\n$detail\n\n" + Language.text(this@MainActivity, "app chụp hình hiện trường thời gian thực") + "\n\n" + Language.text(this@MainActivity, "Phát triển") + "\nNguyễn Đắc Đạt\n\nEmail: chobanchotoi@gmail.com\n\n© 2026 Nguyễn Đắc Đạt"
            textSize = 17f; setPadding(dp(24), dp(16), dp(24), dp(16)); setTextIsSelectable(true)
        }
        AlertDialog.Builder(this).setTitle(Language.text(this@MainActivity, "Thông tin")).setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(Language.text(this@MainActivity, "Đóng"), null).show()
    }
    private fun showPurchaseOptions() {
        val title = Language.text(this, "Xóa quảng cáo")
        val message = if (adFree.owned) Language.text(this, "Quảng cáo đã được xóa")
            else adFree.price()?.let { Language.text(this, "Mua một lần: {0}", it) } ?: Language.text(this, "Sản phẩm chưa được cửa hàng cung cấp")
        val dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNeutralButton(Language.text(this, "Khôi phục mua hàng")) { _, _ -> adFree.restore() }
            .setNegativeButton(Language.text(this, "Đóng"), null)
            .setPositiveButton(title) { _, _ -> adFree.buy() }.create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !adFree.owned && !adFree.busy && adFree.price() != null }
        dialog.show()
    }
    private fun directionsLink(lat: Double, lon: Double): String =
        "https://www.google.com/maps/dir/?api=1&destination=" + String.format(Locale.US, "%.6f,%.6f", lat, lon) + "&travelmode=driving"

    private fun mapLinksOnly(text: String): String =
        Regex("""https://www\.google\.com/maps/dir/\?api=1&destination=-?\d+(?:\.\d+)?,-?\d+(?:\.\d+)?&travelmode=driving""")
            .findAll(text).map { it.value }.distinct().joinToString("\n")

    private fun shareText(d: Date, l: Location?): String =
        l?.let { directionsLink(it.latitude, it.longitude) } ?: ""

    private fun trimLogo(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val bounds = LogoAlphaBounds.find(bitmap.width, bitmap.height, pixels) ?: return bitmap
        if (bounds[0] == 0 && bounds[1] == 0 && bounds[2] == bitmap.width && bounds[3] == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, bounds[0], bounds[1], bounds[2], bounds[3])
    }

    private fun originalLogo(): Bitmap? {
        val file = File(filesDir, "logo.png")
        return (if (file.exists()) BitmapFactory.decodeFile(file.path) else null)
            ?: BitmapFactory.decodeResource(resources, R.drawable.default_antt_logo, BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })
    }
    private fun loadLogo() {
        val original = originalLogo()
        logoBmp = original?.let { if (sp.getBoolean("stampLogoRemoveWhiteBackground", false)) LogoProcessing.removeWhite(it) else trimLogo(it) }
    }
    private fun saveLogo(uri: Uri) {
        try {
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
            var ss = 1; while (maxOf(opt.outWidth, opt.outHeight) / ss > 1024) ss *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = ss }
            val bm = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: throw Exception("decode")
            File(filesDir, "logo.png").outputStream().use { trimLogo(bm).compress(Bitmap.CompressFormat.PNG, 100, it) }
            loadLogo(); refresh(); settingsScreen?.refreshPreview(); toast(Language.text(this@MainActivity, "Đã đặt logo"))
        } catch (e: Exception) { toast(Language.text(this@MainActivity, "Không đọc được ảnh logo")) }
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
        refresh()
    }
    private fun quickNote() {
        if (videoBusy()) { toast(Language.text(this@MainActivity, "Hãy đợi lưu video")); return }
        openStampSettings(true)
    }

    /* ---------- Thư viện + chia sẻ hàng loạt ---------- */
    private fun nearBy(a: Item, b2: Item): Boolean {
        val la = a.lat; val oa = a.lon; val lb = b2.lat; val ob = b2.lon
        if (la == null || oa == null || lb == null || ob == null) return false
        val r = FloatArray(1); Location.distanceBetween(la, oa, lb, ob, r); return r[0] < 100f
    }
    private fun requestMediaDeletion(
        selected: List<Uri>,
        finished: () -> Unit
    ) {
        if (selected.isEmpty()) {
            finished()
            return
        }

        if (pendingMediaDelete.isNotEmpty()) return

        pendingMediaDelete = selected.distinct()
        onMediaDeleteFinished = finished
        mediaDeleteIndex = 0

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            deleteNextAndroid10()
            return
        }

        try {
            val remaining = pendingMediaDelete.filter {
                mediaExists(it) != false
            }

            if (remaining.isEmpty()) {
                finishMediaDeletion()
                return
            }

            val request = MediaStore.createDeleteRequest(
                contentResolver,
                remaining
            )

            mediaDeleteLauncher.launch(
                IntentSenderRequest.Builder(
                    request.intentSender
                ).build()
            )
        } catch (_: Exception) {
            finishMediaDeletion()
        }
    }
    private fun openGallery() {
        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(0, dp(24), 0, 0) }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
        toolbar.addView(Button(this).apply { text = "‹"; contentDescription = Language.text(this@MainActivity, "Đóng"); setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(dp(52), dp(52)))
        toolbar.addView(TextView(this).apply { text = Language.text(this@MainActivity, "Xuất & Chia sẻ"); setTextColor(Color.BLACK); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        val menu = Button(this).apply { text = "⋮"; contentDescription = Language.text(this@MainActivity, "Chọn ảnh/video"); isEnabled = false }
        toolbar.addView(menu, LinearLayout.LayoutParams(dp(52), dp(52))); root.addView(toolbar)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val status = TextView(this).apply { text = Language.text(this@MainActivity, "Đang tải ảnh/video và vị trí…"); setTextColor(Color.DKGRAY); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        content.addView(status)
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(18)); setBackgroundColor(0xFFF2F5FA.toInt()) }
        val summary = TextView(this).apply { textSize = 17f; setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD }
        val hint = TextView(this).apply { textSize = 12f; setTextColor(Color.DKGRAY); setPadding(0, dp(6), 0, dp(8)) }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val share = Button(this).apply { text = Language.text(this@MainActivity, "Chia sẻ"); setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.ic_menu_share, 0, 0); isEnabled = false }
        val maps = Button(this).apply { text = Language.text(this@MainActivity, "Liên kết Maps"); setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.ic_menu_mylocation, 0, 0); isEnabled = false }
        val deleteMedia = Button(this).apply {
            text = Language.text(this@MainActivity, "Xóa")
            isEnabled = false
        }
        actions.addView(share, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(maps, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(deleteMedia, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(summary); footer.addView(hint); footer.addView(actions); root.addView(footer)
        val items = ArrayList<Item>(); val checks = HashMap<Item, TextView>(); val dayButtons = ArrayList<Pair<List<Item>, Button>>()
        var last: Item? = null; var preparing = false
        fun update() {
            val selected = items.filter { it.sel }
            val text = batchText(selected)
            summary.text = Language.text(this@MainActivity, "{0} ảnh/video đã chọn", (selected.size))
            hint.text = when {
                preparing -> Language.text(this@MainActivity, "Đang chuẩn bị tệp…")
                selected.isEmpty() -> Language.text(this@MainActivity, "Chọn ảnh/video để chia sẻ hoặc lấy link Maps")
                text.isEmpty() -> Language.text(this@MainActivity, "Tệp đã chọn không có vị trí GPS")
                else -> Language.text(this@MainActivity, "Kèm link Maps và chép sẵn để dán nếu ứng dụng nhận chỉ lấy ảnh/video")
            }
            share.isEnabled = selected.isNotEmpty() && !preparing
            maps.isEnabled = text.isNotEmpty() && !preparing
            deleteMedia.isEnabled = selected.isNotEmpty() && !preparing
            checks.forEach { (item, label) -> label.text = if (item.sel) "✓" else "○"; label.setBackgroundColor(if (item.sel) 0xFF0099F0.toInt() else 0x66000000) }
            dayButtons.forEach { (group, button) -> button.isSelected = group.all { it.sel }; button.setCompoundDrawablesWithIntrinsicBounds(0, 0, if (button.isSelected) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background, 0) }
        }
        share.setOnClickListener {
            if (!preparing) {
                preparing = true; update()
                shareBatch(items.filter { it.sel }.toList()) { preparing = false; if (dialog.isShowing) update() }
            }
        }
        maps.setOnClickListener { showMapsLinks(batchText(items.filter { it.sel })) }
        deleteMedia.setOnClickListener {
            val selected = items.filter { it.sel }.map { it.uri }

            if (selected.isNotEmpty() && !preparing) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Language.text(this@MainActivity, "Xóa ảnh/video"))
                    .setMessage(
                        Language.text(this@MainActivity, "Xóa ảnh/video đã chọn?") +
                            " (${selected.size})"
                    )
                    .setNegativeButton(
                        Language.text(this@MainActivity, "Hủy"),
                        null
                    )
                    .setPositiveButton(
                        Language.text(this@MainActivity, "Xóa")
                    ) { _, _ ->
                        preparing = true
                        update()

                        requestMediaDeletion(selected) {
                            preparing = false
                            if (dialog.isShowing) {
                                dialog.dismiss()
                                openGallery()
                            }
                        }
                    }
                    .show()
            }
        }
        menu.setOnClickListener {
            AlertDialog.Builder(this).setItems(arrayOf(if (items.all { it.sel }) Language.text(this@MainActivity, "Bỏ chọn tất cả") else Language.text(this@MainActivity, "Chọn tất cả"), Language.text(this@MainActivity, "Chọn cùng vị trí"))) { _, action ->
                if (action == 0) { val all = items.all { it.sel }; items.forEach { it.sel = !all } }
                else last?.let { ref -> items.forEach { if (it === ref || nearBy(ref, it)) it.sel = true } } ?: toast(Language.text(this@MainActivity, "Chọn một ảnh/video trước"))
                update()
            }.show()
        }
        dialog.setContentView(root); dialog.show(); update()
        thread {
            try {
                fun readMedia(collection: Uri, video: Boolean) {
                    contentResolver.query(collection, arrayOf("_id", "date_added", "_display_name"),
                        "relative_path LIKE ? AND is_pending = 0", arrayOf("%DatvipCam%"), "date_added DESC")?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val item = Item(ContentUris.withAppendedId(collection, cursor.getLong(0)), cursor.getLong(1) * 1000, video)
                            val coordinates = s("gps_${cursor.getString(2)}").split(',')
                            if (coordinates.size == 2) { item.lat = coordinates[0].toDoubleOrNull(); item.lon = coordinates[1].toDoubleOrNull() }
                            if (!video && item.lat == null) try { contentResolver.openInputStream(item.uri)?.use { input -> ExifInterface(input).latLong?.let { item.lat = it[0]; item.lon = it[1] } } } catch (_: Exception) {}
                            items.add(item)
                        }
                    }
                }
                readMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
                readMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
                items.sortByDescending { it.time }
                runOnUiThread {
                    if (!dialog.isShowing || destroying) return@runOnUiThread
                    content.removeAllViews(); menu.isEnabled = items.isNotEmpty()
                    if (items.isEmpty()) content.addView(TextView(this).apply { text = Language.text(this@MainActivity, "Chưa có ảnh/video. Hãy chụp hoặc quay trước."); setTextColor(Color.DKGRAY); setPadding(dp(16), dp(24), dp(16), dp(24)) })
                    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val today = format.format(Date()); val yesterday = format.format(Date(System.currentTimeMillis() - 86400000))
                    val thumbnails = HashMap<Item, ImageView>()
                    items.groupBy { format.format(Date(it.time)) }.forEach { (date, group) ->
                        val label = when (date) { today -> Language.text(this@MainActivity, "Hôm nay"); yesterday -> Language.text(this@MainActivity, "Hôm qua"); else -> SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(group.first().time)) }
                        val day = Button(this).apply { text = "$label (${group.size})"; gravity = Gravity.START or Gravity.CENTER_VERTICAL; setTextColor(Color.BLACK)
                            setOnClickListener { val all = group.all { it.sel }; group.forEach { it.sel = !all }; update() } }
                        dayButtons.add(group to day); content.addView(day, LinearLayout.LayoutParams(-1, -2))
                        val grid = GridLayout(this).apply { columnCount = 3 }
                        val size = resources.displayMetrics.widthPixels / 3
                        group.forEach { item ->
                            val cell = FrameLayout(this)
                            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFFE0E5EB.toInt()) }
                            thumbnails[item] = image; cell.addView(image, FrameLayout.LayoutParams(-1, -1).apply { setMargins(1, 1, 1, 1) })
                            val check = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
                            checks[item] = check; cell.addView(check, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.END or Gravity.TOP).apply { setMargins(0, dp(5), dp(5), 0) })
                            if (item.video) cell.addView(TextView(this).apply { text = "▶"; textSize = 24f; setTextColor(Color.WHITE); setShadowLayer(3f, 0f, 1f, Color.BLACK) }, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply { setMargins(dp(6), 0, 0, dp(6)) })
                            cell.contentDescription = if (item.video) Language.text(this@MainActivity, "Chọn video") else Language.text(this@MainActivity, "Chọn ảnh")
                            cell.setOnClickListener { item.sel = !item.sel; last = item; update() }
                            cell.setOnLongClickListener {
                                try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, if (item.video) "video/mp4" else "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                catch (_: Exception) { toast(Language.text(this@MainActivity, "Không có ứng dụng mở tệp này")) }; true
                            }
                            grid.addView(cell, GridLayout.LayoutParams().apply { width = size; height = size })
                        }
                        content.addView(grid)
                    }
                    update()
                    thread {
                        items.forEach { item ->
                            val bitmap = try { contentResolver.loadThumbnail(item.uri, Size(300, 300), null) } catch (_: Exception) { null }
                            runOnUiThread { if (dialog.isShowing && bitmap != null) thumbnails[item]?.setImageBitmap(bitmap) }
                        }
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { if (dialog.isShowing) status.text = Language.text(this@MainActivity, "Không đọc được thư viện: {0}", (error.message)) }
            }
        }
    }

    private fun showMapsLinks(text: String) {
        if (text.isBlank()) { toast(Language.text(this@MainActivity, "Tệp đã chọn không có vị trí GPS")); return }
        val linkView = TextView(this).apply { this.text = text; setTextIsSelectable(true); setPadding(dp(20), dp(16), dp(20), dp(16)) }
        AlertDialog.Builder(this).setTitle(Language.text(this@MainActivity, "Liên kết Maps")).setView(ScrollView(this).apply { addView(linkView) })
            .setPositiveButton(Language.text(this@MainActivity, "Sao chép")) { _, _ -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Google Maps", text)); toast(Language.text(this@MainActivity, "Đã chép link Maps")) }
            .setNeutralButton(Language.text(this@MainActivity, "Chia sẻ link")) { _, _ -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, Language.text(this@MainActivity, "Chia sẻ link Maps"))) }
            .setNegativeButton(Language.text(this@MainActivity, "Đóng"), null).show()
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
    private fun shareBatch(selected: List<Item>, finished: () -> Unit = {}) {
        if (selected.isEmpty()) { finished(); toast(Language.text(this@MainActivity, "Hãy chọn ảnh/video trước")); return }
        val selection = selected.toList()
        val text = batchText(selection)
        thread {
            try {
                val directory = File(cacheDir, "share/${UUID.randomUUID()}")
                check(directory.mkdirs()) { Language.text(this@MainActivity, "Không tạo được thư mục chia sẻ") }
                val uris = ArrayList<Uri>()
                selection.forEachIndexed { index, item ->
                    val file = File(directory, "DatvipCam_${index + 1}_${item.time}." + if (item.video) "mp4" else "jpg")
                    val stream = contentResolver.openInputStream(item.uri) ?: error(Language.text(this@MainActivity, "Không mở được một tệp đã chọn"))
                    stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                    check(file.length() > 0) { Language.text(this@MainActivity, "Một tệp đã chọn không có dữ liệu") }
                    uris.add(FileProvider.getUriForFile(this, "$packageName.fileprovider", file))
                }
                val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                    type = if (selection.all { it.video }) "video/mp4" else if (selection.none { it.video }) "image/jpeg" else "*/*"
                    if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    if (text.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, text)
                    clipData = ClipData.newRawUri("", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    finished()
                    if (destroying) return@runOnUiThread
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Google Maps", text))
                    try { startActivity(Intent.createChooser(intent, Language.text(this@MainActivity, "Chia sẻ ảnh/video"))) }
                    catch (error: Exception) { toast(Language.text(this@MainActivity, "Không mở được chia sẻ: {0}", (error.message))) }
                }
            } catch (error: Exception) { runOnUiThread { finished(); toast(Language.text(this@MainActivity, "Không chuẩn bị được tệp: {0}", (error.message))) } }
        }
    }

    private fun openLast() {
        val u = lastUri
        val i = if (u != null) Intent(Intent.ACTION_VIEW).setDataAndType(u, lastMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        else Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try { startActivity(i) } catch (e: Exception) { toast(Language.text(this@MainActivity, "Không mở được thư viện")) }
    }
    private fun toast(t: String) = runOnUiThread { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }
}
