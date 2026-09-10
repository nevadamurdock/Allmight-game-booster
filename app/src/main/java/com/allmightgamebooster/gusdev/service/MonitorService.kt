package com.allmightgamebooster.gusdev.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.allmightgamebooster.gusdev.AllMightApp
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.ui.dashboard.DashboardActivity
import com.allmightgamebooster.gusdev.util.ShellExecutor

class MonitorService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1002
        var temperature: Float = 0f
        var avgFps: Float = 0f
        var currentFps: Float = 0f
        var peakTemperature: Float = 0f
        var isMonitoring = false
            private set
        private var monitorThread: Thread? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        isMonitoring = true
        peakTemperature = 0f
        var totalFps = 0f
        var fpsCount = 0
        var lastFrameCount = -1L

        monitorThread = Thread {
            while (isMonitoring) {
                try {
                    temperature = ShellExecutor.getThermalTemp()
                    if (temperature > peakTemperature) peakTemperature = temperature

                    val frameCount = readFrameCount()
                    if (lastFrameCount >= 0 && frameCount > 0) {
                        currentFps = (frameCount - lastFrameCount).toFloat()
                        if (currentFps in 0f..300f) {
                            totalFps += currentFps
                            fpsCount++
                            avgFps = if (fpsCount > 0) totalFps / fpsCount else 0f
                        }
                    }
                    lastFrameCount = frameCount

                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.also { it.start() }
    }

    private fun readFrameCount(): Long {
        val output = ShellExecutor.exec("service call SurfaceFlinger 1013")
        val match = Regex("([0-9a-fA-F]+)").findAll(output).lastOrNull()?.value
        return match?.toLongOrNull(16) ?: 0L
    }

    fun stopMonitoring(): Float {
        isMonitoring = false
        monitorThread?.interrupt()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return peakTemperature
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AllMightApp.CHANNEL_BOOST)
            .setContentTitle("Monitor aktif")
            .setContentText("Merekam suhu & FPS")
            .setSmallIcon(R.drawable.ic_boost)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isMonitoring = false
        monitorThread?.interrupt()
        super.onDestroy()
    }
}
