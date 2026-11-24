package com.datvip.cam

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.GeomagneticField
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/** North-up map with a transparent phone-heading dial, corrected to true north. */
class CompassPanel(context: Context, private val heading: () -> Float?, private val accurate: () -> Boolean,
                   private val location: () -> Location?) : Dialog(context, android.R.style.Theme_Material_NoActionBar) {
    private val handler=Handler(Looper.getMainLooper())
    private var mapView: MapView?=null
    private var marker: Marker?=null
    private var mapShown=true
    private var centered=false
    private var locked: Float?=null
    private var lockRequested=false
    private lateinit var dial: CompassDialView
    private lateinit var readout: TextView
    private lateinit var state: TextView
    private lateinit var lockButton: Button
    private lateinit var attribution: TextView
    private val tick=object:Runnable { override fun run() {
        val magnetic=heading()
        val fix=location()
        val actual=if(mapShown) fix?.let { l -> magnetic?.let {
            CompassMath.normalize(it+GeomagneticField(l.latitude.toFloat(),l.longitude.toFloat(),l.altitude.toFloat(),System.currentTimeMillis()).declination)
        } } else magnetic
        val h=if(lockRequested) locked else actual
        dial.heading=h
        lockButton.text=Language.text(context,if(lockRequested) "Mở khóa" else "Khóa hướng")
        readout.text=if(h==null) Language.text(context,"Chưa có hướng la bàn") else String.format(java.util.Locale.US,"%.1f° · %s\n↔ %.1f° · %s",h,CompassMath.mountain(h),CompassMath.normalize(h+180),CompassMath.mountain(h+180))
        val reference=if(mapShown) "Bắc thật ở trên" else "Bắc ở trên · Hướng từ"
        val hint=if(mapShown && fix==null) "Đang chờ vị trí. Bật GPS và cấp quyền vị trí cho Camera HT." else if(!accurate()) "Tránh kim loại, di chuyển máy hình số 8 để hiệu chỉnh." else "Giữ điện thoại nằm phẳng khi đọc hướng."
        state.text=Language.text(context,reference)+"\n"+Language.text(context,hint)+if(mapShown) "\n"+Language.text(context,"Bản đồ đường phố · Cần Internet để tải bản đồ mới.") else ""
        if(fix!=null) {
            marker?.position=GeoPoint(fix.latitude,fix.longitude)
            marker?.isEnabled=true
            mapView?.invalidate()
            if(!centered) centerMap()
        }
        handler.postDelayed(this,250)
    } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        fun text(value:String,size:Float)=TextView(context).apply { text=value; textSize=size; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(12,8,12,8) }
        root.addView(text(Language.text(context,"La bàn phong thủy"),22f))
        readout=text("—",22f); root.addView(readout)
        val stage=FrameLayout(context); root.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        val config=Configuration.getInstance()
        config.userAgentValue="CameraHT/1.2.0 (${context.packageName}; https://github.com/datnguyenvip/datvip-cam)"
        config.osmdroidBasePath=File(context.filesDir,"osm")
        config.osmdroidTileCache=File(context.filesDir,"osm/tiles").apply { mkdirs() }
        config.expirationExtendedDuration=7L*24*60*60*1000
        mapView=MapView(context).also { view ->
            view.setTileSource(XYTileSource("CameraHT-OSM",0,19,256,".png",arrayOf("https://tile.openstreetmap.org/"),"© OpenStreetMap contributors"))
            view.setMultiTouchControls(true)
            view.minZoomLevel=3.0; view.maxZoomLevel=19.0
            view.controller.setZoom(5.0)
            view.controller.setCenter(GeoPoint(16.0,106.0))
            stage.addView(view,FrameLayout.LayoutParams(-1,-1))
            marker=Marker(view).apply { isEnabled=false; setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER); setOnMarkerClickListener { _, _ -> true } }
            view.overlays.add(marker)
            view.onResume()
        }
        dial=CompassDialView(context).apply { overMap=true }
        val size=(context.resources.displayMetrics.widthPixels*.80f).toInt()
        stage.addView(dial,FrameLayout.LayoutParams(size,size,Gravity.CENTER))
        attribution=text("© OpenStreetMap contributors",12f).apply {
            setTextColor(Color.BLACK); setBackgroundColor(0xEEFFFFFF.toInt())
            setOnClickListener { openUrl(Uri.parse("https://www.openstreetmap.org/copyright")) }
        }
        stage.addView(attribution,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        state=text("",12f); root.addView(state)
        val controls=LinearLayout(context)
        fun button(label:String,action:(Button)->Unit) { controls.addView(Button(context).apply { text=label; setOnClickListener { action(this) } },LinearLayout.LayoutParams(0,-2,1f)) }
        button(Language.text(context,"Vòng la bàn")) { dial.visibility=if(dial.visibility==View.VISIBLE) View.GONE else View.VISIBLE }
        button(Language.text(context,"Ẩn bản đồ")) { button ->
            mapShown=!mapShown; lockRequested=false; locked=null
            mapView?.visibility=if(mapShown) View.VISIBLE else View.GONE
            attribution.visibility=if(mapShown) View.VISIBLE else View.GONE
            dial.overMap=mapShown
            button.text=Language.text(context,if(mapShown) "Ẩn bản đồ" else "Bản đồ")
        }
        root.addView(controls)
        val footer=LinearLayout(context)
        footer.addView(Button(context).apply { text=Language.text(context,"Khóa hướng"); setOnClickListener {
            if(!lockRequested && dial.heading==null) return@setOnClickListener
            lockRequested=!lockRequested; locked=if(lockRequested) dial.heading else null
            text=Language.text(context,if(lockRequested) "Mở khóa" else "Khóa hướng")
        } }.also { lockButton=it },LinearLayout.LayoutParams(0,-2,1f))
        footer.addView(Button(context).apply { text=Language.text(context,"Vị trí của tôi"); setOnClickListener { centerMap(true) } },LinearLayout.LayoutParams(0,-2,1f))
        footer.addView(Button(context).apply { text=Language.text(context,"Xong"); setOnClickListener { dismiss() } },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(footer); setContentView(root); handler.post(tick)
    }
    private fun centerMap(force:Boolean=false) {
        val fix=location()
        if(fix==null) {
            if(force) AlertDialog.Builder(context).setMessage(Language.text(context,"Chưa có vị trí. Hãy bật vị trí, cấp quyền cho Camera HT và chờ nhận GPS rồi thử lại.")).setPositiveButton(Language.text(context,"Xong"),null).show()
            return
        }
        mapView?.let { if(force||!centered) {
            it.controller.setZoom(18.0); it.controller.setCenter(GeoPoint(fix.latitude,fix.longitude)); centered=true
        } }
    }
    private fun openUrl(uri:Uri) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch(_:ActivityNotFoundException) { Toast.makeText(context,Language.text(context,"Không tìm thấy ứng dụng bản đồ hoặc trình duyệt để mở vị trí."),Toast.LENGTH_LONG).show() }
    }
    override fun dismiss() {
        handler.removeCallbacks(tick)
        mapView?.onPause(); mapView?.onDetach(); mapView=null; marker=null
        super.dismiss()
    }
}
