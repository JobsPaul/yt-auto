package com.jobspaul.ytauto.car

import android.annotation.SuppressLint
import android.app.Presentation
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.jobspaul.ytauto.R

/**
 * วาด WebView ที่เปิด YouTube ลงบน Surface ของจอรถ
 *
 * Surface (จาก Android Auto) → VirtualDisplay → Presentation → WebView
 * การแตะ/ปัดบนจอรถจะส่งต่อเข้า WebView
 */
class SurfaceRenderer(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
) : SurfaceCallback, DefaultLifecycleObserver {

    private val main = Handler(Looper.getMainLooper())

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: FrameLayout? = null
    private var webView: WebView? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var blocker: TextView? = null

    private var currentUrl: String = HOME_URL
    private var savedState: Bundle? = null
    private var driveState = DriveState.UNKNOWN

    /** ตอนรถไม่ได้จอด สั่งหยุดวิดีโอซ้ำทุกวินาที กันวิดีโอเล่นต่อเองหรือ autoplay */
    private val pauseLoop = object : Runnable {
        override fun run() {
            if (!isParked()) {
                webView?.evaluateJavascript(JS_PAUSE_ALL, null)
                main.postDelayed(this, 1000)
            }
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        main.removeCallbacksAndMessages(null)
        release()
    }

    // ---------- Surface ----------

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        release()
        try {
            val dm = carContext.getSystemService(DisplayManager::class.java)
            val vd = dm.createVirtualDisplay(
                "YtAutoDisplay",
                surfaceContainer.width,
                surfaceContainer.height,
                surfaceContainer.dpi,
                surface,
                0, // private display ของแอปเอง
            )
            virtualDisplay = vd

            val p = Presentation(carContext, vd.display, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val ctx = p.context
            val frame = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }
            val wv = createWebView(ctx)
            frame.addView(wv, matchParent())
            val block = TextView(ctx).apply {
                setBackgroundColor(Color.BLACK)
                setTextColor(Color.WHITE)
                textSize = 28f
                gravity = Gravity.CENTER
                isClickable = true // กันการแตะทะลุไปถึง WebView
            }
            frame.addView(block, matchParent())

            p.setContentView(frame)
            p.show()

            presentation = p
            root = frame
            webView = wv
            blocker = block

            val state = savedState
            if (state != null && wv.restoreState(state) != null) {
                savedState = null
            } else {
                wv.loadUrl(currentUrl)
            }
            applyDriveState()
        } catch (e: Exception) {
            Log.e(TAG, "สร้างหน้าจอบนรถไม่สำเร็จ", e)
            release()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        release()
    }

    private fun release() {
        webView?.let { wv ->
            wv.evaluateJavascript(JS_PAUSE_ALL, null)
            currentUrl = wv.url ?: currentUrl
            savedState = Bundle().also { wv.saveState(it) }
        }
        exitFullscreen()
        runCatching { presentation?.dismiss() }
        runCatching { webView?.destroy() }
        runCatching { virtualDisplay?.release() }
        presentation = null
        webView = null
        root = null
        blocker = null
        virtualDisplay = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(ctx: android.content.Context): WebView = WebView(ctx).apply {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // เปิดลิงก์ youtube.com/google.com (หน้าล็อกอิน) ในแอป ส่วนลิงก์อื่นไม่เปิด
                val host = request.url.host ?: return true
                return !(host.endsWith("youtube.com") || host.endsWith("google.com") ||
                    host.endsWith("youtu.be") || host.endsWith("gstatic.com"))
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                // ปุ่มเต็มจอของ YouTube
                exitFullscreen()
                val frame = root ?: return callback.onCustomViewHidden()
                fullscreenView = view
                fullscreenCallback = callback
                frame.addView(view, frame.childCount - 1, matchParent()) // วางใต้ blocker เสมอ
            }

            override fun onHideCustomView() = exitFullscreen()
        }
    }

    private fun exitFullscreen() {
        fullscreenView?.let { root?.removeView(it) }
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView = null
        fullscreenCallback = null
    }

    // ---------- ล็อกตอนขับ ----------

    fun setDriveState(state: DriveState) {
        driveState = state
        applyDriveState()
    }

    private fun isParked() = driveState == DriveState.PARKED

    private fun applyDriveState() {
        val block = blocker ?: return
        if (isParked()) {
            block.visibility = View.GONE
            main.removeCallbacks(pauseLoop)
        } else {
            block.setText(
                if (driveState == DriveState.MOVING) R.string.blocked_moving else R.string.blocked_unknown
            )
            block.visibility = View.VISIBLE
            block.bringToFront()
            main.removeCallbacks(pauseLoop)
            main.post(pauseLoop)
        }
    }

    // ---------- คำสั่งจากปุ่ม ----------

    fun search(query: String) {
        navigate("https://m.youtube.com/results?search_query=" + Uri.encode(query))
    }

    fun goHome() = navigate(HOME_URL)

    fun goBack() {
        val wv = webView ?: return
        if (fullscreenView != null) exitFullscreen()
        else if (wv.canGoBack()) wv.goBack()
    }

    fun togglePlay() {
        if (!isParked()) return
        webView?.evaluateJavascript(JS_TOGGLE, null)
    }

    private fun navigate(url: String) {
        currentUrl = url
        savedState = null
        exitFullscreen()
        webView?.loadUrl(url)
    }

    // ---------- การแตะ/ปัดบนจอรถ ----------

    override fun onClick(x: Float, y: Float) {
        val target = root ?: return
        val t = SystemClock.uptimeMillis()
        dispatchTouch(target, MotionEvent.ACTION_DOWN, x, y, t, t)
        dispatchTouch(target, MotionEvent.ACTION_UP, x, y, t, t + 60)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (!isParked()) return
        webView?.evaluateJavascript("window.scrollBy(${distanceX.toInt()}, ${distanceY.toInt()});", null)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (!isParked()) return
        val dy = (-velocityY / 4).toInt()
        webView?.evaluateJavascript("window.scrollBy({top: $dy, behavior: 'smooth'});", null)
    }

    private fun dispatchTouch(view: View, action: Int, x: Float, y: Float, down: Long, time: Long) {
        val ev = MotionEvent.obtain(down, time, action, x, y, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
    )

    companion object {
        private const val TAG = "SurfaceRenderer"
        const val HOME_URL = "https://m.youtube.com/"
        private const val JS_PAUSE_ALL =
            "document.querySelectorAll('video').forEach(function(v){v.pause();});"
        private const val JS_TOGGLE =
            "(function(){var v=document.querySelector('video');if(!v)return;if(v.paused)v.play();else v.pause();})();"
    }
}
