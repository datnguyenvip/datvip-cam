package com.datvip.cam

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.hardware.GeomagneticField
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng

/** The map stays north-up; the red pointer shows the phone's heading on that map. */
class CompassPanel(context: Context, private val heading: () -> Float?, private val accurate: () -> Boolean,
                   private val location: () -> Location?) : Dialog(context, android.R.style.Theme_Material_NoActionBar) {
    private val handler=Handler(Looper.getMainLooper())
    private var mapView: MapView?=null
    private var map: GoogleMap?=null
    private var locked: Float?=null
    private var lockRequested=false
    private lateinit var dial: CompassDialView
    private lateinit var readout: TextView
    private lateinit var state: TextView
    private lateinit var lockButton: Button
    private var centered=false
    private val tick=object:Runnable { override fun run() {
        val magnetic=heading()
        val satellite=mapView?.visibility==android.view.View.VISIBLE
        val actual=if(satellite) location()?.let { l -> magnetic?.let { CompassMath.normalize(it+GeomagneticField(l.latitude.toFloat(),l.longitude.toFloat(),l.altitude.toFloat(),System.currentTimeMillis()).declination) } } else magnetic
        val h=if(lockRequested) locked else actual
        dial.heading=h
        lockButton.text=if(lockRequested) Language.text(context,"Mở khóa") else Language.text(context,"Khóa hướng")
        readout.text=if(h==null) Language.text(context,"Chưa có hướng la bàn") else String.format(java.util.Locale.US,"%.1f° · %s\n↔ %.1f° · %s",h,CompassMath.mountain(h),CompassMath.normalize(h+180),CompassMath.mountain(h+180))
        state.text=(if(satellite) Language.text(context,"Bắc thật ở trên") else Language.text(context,"Bắc ở trên · Hướng từ"))+"\n"+if(!accurate()) Language.text(context,"Tránh kim loại, di chuyển máy hình số 8 để hiệu chỉnh.") else Language.text(context,"Giữ điện thoại nằm phẳng khi đọc hướng.")
        if(!centered) centerMap()
        handler.postDelayed(this,100)
    } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        fun text(value:String,size:Float)=TextView(context).apply { text=value; textSize=size; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(12,12,12,12) }
        root.addView(text(Language.text(context,"La bàn phong thủy"),22f))
        readout=text("—",22f); root.addView(readout)
        val stage=FrameLayout(context); root.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        val apiKey=context.packageManager.getApplicationInfo(context.packageName,PackageManager.GET_META_DATA).metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        if(apiKey.isNotBlank()) {
            mapView=MapView(context).also { view ->
                stage.addView(view,FrameLayout.LayoutParams(-1,-1)); view.onCreate(savedInstanceState); view.onStart(); view.onResume()
                view.visibility=android.view.View.GONE
                view.getMapAsync { googleMap -> map=googleMap; googleMap.mapType=GoogleMap.MAP_TYPE_HYBRID
                    googleMap.uiSettings.isRotateGesturesEnabled=false; googleMap.uiSettings.isTiltGesturesEnabled=false
                    googleMap.setPadding(0,0,0,24); centerMap()
                }
            }
        }
        dial=CompassDialView(context)
        val size=(context.resources.displayMetrics.widthPixels*.80f).toInt()
        stage.addView(dial,FrameLayout.LayoutParams(size,size,Gravity.CENTER))
        state=text("",13f); root.addView(state)
        val controls=LinearLayout(context)
        fun button(label:String,action:(Button)->Unit) { controls.addView(Button(context).apply { text=label; setOnClickListener { action(this) } },LinearLayout.LayoutParams(0,-2,1f)) }
        button(Language.text(context,"Vòng la bàn")) { dial.visibility=if(dial.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE }
        button("Google Maps") { openExternalMap() }
        if(mapView!=null) button(Language.text(context,"Vệ tinh")) {
            val view=mapView ?: return@button
            lockRequested=false; locked=null; view.visibility=if(view.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
            if(view.visibility==android.view.View.VISIBLE) centerMap(true)
        }
        root.addView(controls)
        val footer=LinearLayout(context)
        footer.addView(Button(context).apply { text=Language.text(context,"Khóa hướng"); setOnClickListener {
            if(!lockRequested && dial.heading==null) return@setOnClickListener
            lockRequested=!lockRequested; locked=if(lockRequested) dial.heading else null
            text=if(lockRequested) Language.text(context,"Mở khóa") else Language.text(context,"Khóa hướng")
        } }.also { lockButton=it },LinearLayout.LayoutParams(0,-2,1f))
        footer.addView(Button(context).apply { text=Language.text(context,"Vị trí của tôi"); setOnClickListener {
            if(mapView?.visibility==android.view.View.VISIBLE) centerMap(true) else openExternalMap()
        } },LinearLayout.LayoutParams(0,-2,1f))
        footer.addView(Button(context).apply { text=Language.text(context,"Xong"); setOnClickListener { dismiss() } },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(footer); setContentView(root); handler.post(tick)
    }
    private fun centerMap(force:Boolean=false) {
        val l=location() ?: return
        map?.let { if(force||!centered) { it.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(l.latitude,l.longitude),18f)); centered=true } }
    }
    private fun openExternalMap() {
        val l=location()
        if(l==null) {
            AlertDialog.Builder(context)
                .setMessage(Language.text(context,"Chưa có vị trí. Hãy bật vị trí, cấp quyền cho Camera HT và chờ nhận GPS rồi thử lại."))
                .setPositiveButton(Language.text(context,"Xong"),null).show()
            return
        }
        // Maps URLs need no API key and also work in a browser without the Maps app.
        val uri=Uri.parse("https://www.google.com/maps/search/").buildUpon()
            .appendQueryParameter("api","1")
            .appendQueryParameter("query","${l.latitude},${l.longitude}").build()
        val intent=Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent(intent).setPackage("com.google.android.apps.maps"))
        } catch(_:ActivityNotFoundException) {
            try { context.startActivity(intent) }
            catch(_:ActivityNotFoundException) {
                Toast.makeText(context,Language.text(context,"Không tìm thấy ứng dụng bản đồ hoặc trình duyệt để mở vị trí."),Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun dismiss() { handler.removeCallbacks(tick); mapView?.onPause(); mapView?.onStop(); mapView?.onDestroy(); mapView=null; super.dismiss() }
}
