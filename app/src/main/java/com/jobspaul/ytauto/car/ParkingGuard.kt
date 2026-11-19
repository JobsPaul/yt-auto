package com.jobspaul.ytauto.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlin.math.abs

enum class DriveState { PARKED, MOVING, UNKNOWN }

/**
 * ตรวจว่ารถจอดอยู่หรือไม่ ถ้าไม่แน่ใจจะถือว่า "ไม่จอด" ไว้ก่อน
 *
 * 1. ใช้ความเร็วจากรถผ่าน Android Auto (CarInfo) เป็นหลัก
 * 2. ถ้ารถไม่ส่งความเร็วมา ใช้ความเร็วจาก GPS ของมือถือแทน
 * 3. ถ้าไม่มีข้อมูลจากทั้งสองทาง จะเป็น UNKNOWN และวิดีโอถูกล็อก
 */
class ParkingGuard(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
    private val onStateChanged: (DriveState) -> Unit,
) : DefaultLifecycleObserver {

    private val main = Handler(Looper.getMainLooper())

    private var carSpeedMs: Float? = null      // ค่าล่าสุดจากรถ (listener ส่งมาเมื่อค่าเปลี่ยน)
    private var gpsSpeedMs: Float? = null
    private var gpsTime = 0L
    private var lastState: DriveState? = null

    private var speedListener: OnCarDataAvailableListener<Speed>? = null
    private var locationListener: LocationListener? = null

    private val ticker = object : Runnable {
        override fun run() {
            evaluate()
            main.postDelayed(this, 1000)
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        startCarSpeed()
        startGps()
        main.post(ticker)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        main.removeCallbacks(ticker)
        speedListener?.let { l ->
            runCatching {
                carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(l)
            }
        }
        locationListener?.let { l ->
            runCatching { carContext.getSystemService(LocationManager::class.java).removeUpdates(l) }
        }
    }

    private fun startCarSpeed() {
        if (carContext.carAppApiLevel < CarAppApiLevels.LEVEL_3) return
        try {
            val carInfo = carContext.getCarService(CarHardwareManager::class.java).carInfo
            val listener = OnCarDataAvailableListener<Speed> { speed ->
                val v = speed.rawSpeedMetersPerSecond
                val value = v.value
                carSpeedMs = if (v.status == CarValue.STATUS_SUCCESS && value != null) abs(value) else null
                evaluate()
            }
            carInfo.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
            speedListener = listener
        } catch (e: Exception) {
            Log.w(TAG, "อ่านความเร็วจากรถไม่ได้ จะใช้ GPS แทน", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        val granted = ContextCompat.checkSelfPermission(
            carContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val lm = carContext.getSystemService(LocationManager::class.java) ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (loc.hasSpeed()) {
                    gpsSpeedMs = loc.speed
                    gpsTime = SystemClock.elapsedRealtime()
                    evaluate()
                }
            }

            // ต้อง override เองเพราะ Android ก่อน 11 ไม่มี default method ให้
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            locationListener = listener
        } catch (e: Exception) {
            Log.w(TAG, "เปิด GPS ไม่ได้", e)
        }
    }

    private fun evaluate() {
        val gpsFresh = SystemClock.elapsedRealtime() - gpsTime < GPS_STALE_MS
        val speed = carSpeedMs ?: gpsSpeedMs?.takeIf { gpsFresh }
        val state = when {
            speed == null -> DriveState.UNKNOWN
            speed < PARKED_THRESHOLD_MS -> DriveState.PARKED
            else -> DriveState.MOVING
        }
        if (state != lastState) {
            lastState = state
            onStateChanged(state)
        }
    }

    companion object {
        private const val TAG = "ParkingGuard"
        private const val PARKED_THRESHOLD_MS = 0.5f   // ~1.8 กม./ชม.
        private const val GPS_STALE_MS = 10_000L
    }
}
