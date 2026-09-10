package com.allmightgamebooster.gusdev.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.util.ShellExecutor

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var expandedView: LinearLayout? = null
    private var dotView: View? = null
    private var isExpanded = true
    private var monitorThread: Thread? = null
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) createOverlay()
        startMonitoring()
        return START_STICKY
    }

    private fun createOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_monitor, null)
        expandedView = overlayView?.findViewById(R.id.overlayExpanded)
        dotView = overlayView?.findViewById(R.id.overlayDot)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val pkg = BoostConfigStore.getActiveApp(this@OverlayService) ?: ""
            val (savedX, savedY) = BoostConfigStore.getOverlayPosition(this@OverlayService)
            x = if (savedX >= 0) savedX else 100
            y = if (savedY >= 0) savedY else 100
        }

        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (dx * dx + dy * dy > 100) moved = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            toggleExpandCollapse()
                        } else {
                            BoostConfigStore.setOverlayPosition(
                                this@OverlayService, params.x, params.y
                            )
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(overlayView, params)
        } catch (_: Exception) {}
    }

    private fun toggleExpandCollapse() {
        isExpanded = !isExpanded
        if (isExpanded) {
            expandedView?.visibility = View.VISIBLE
            dotView?.visibility = View.GONE
        } else {
            expandedView?.visibility = View.GONE
            dotView?.visibility = View.VISIBLE
        }
    }

    private fun startMonitoring() {
        monitorThread?.interrupt()
        monitorThread = Thread {
            while (isRunning) {
                try {
                    val temp = ShellExecutor.getThermalTemp()
                    val freqList = ShellExecutor.getCpuFreq()
                    val maxFreqGhz = freqList.maxByOrNull { it.second }?.let {
                        String.format("%.1f", it.second / 1000000.0)
                    } ?: "N/A"
                    val fps = MonitorService.currentFps

                    overlayView?.post {
                        overlayView?.findViewById<TextView>(R.id.tvOverlayTemp)?.text =
                            "${String.format("%.0f", temp)}\u00B0c"
                        overlayView?.findViewById<TextView>(R.id.tvOverlayFps)?.text =
                            "${String.format("%.0f", fps)}fps"
                        overlayView?.findViewById<TextView>(R.id.tvOverlayClock)?.text =
                            "${maxFreqGhz}ghz"
                    }
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    break
                } catch (_: Exception) {
                    Thread.sleep(2000)
                }
            }
        }.also { it.start() }
    }

    override fun onDestroy() {
        isRunning = false
        monitorThread?.interrupt()
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        super.onDestroy()
    }
}
