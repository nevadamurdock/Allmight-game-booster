package com.allmightgamebooster.gusdev.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.allmightgamebooster.gusdev.AllMightApp
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.ui.dashboard.DashboardActivity
import com.allmightgamebooster.gusdev.util.ShellExecutor

class BoostService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.allmightgamebooster.gusdev.ACTION_BOOST_START"
        const val ACTION_STOP = "com.allmightgamebooster.gusdev.ACTION_BOOST_STOP"
        const val EXTRA_PACKAGE = "extra_package"

        fun start(context: Context, pkg: String) {
            val intent = Intent(context, BoostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACKAGE, pkg)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BoostService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var activePackage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return START_NOT_STICKY
                startBoost(pkg)
            }
            ACTION_STOP -> stopBoost()
        }
        return START_STICKY
    }

    private fun startBoost(pkg: String) {
        activePackage = pkg
        BoostConfigStore.setActiveApp(this, pkg)

        val config = BoostConfigStore.getConfig(this, pkg)
        val pid = ShellExecutor.getProcessPid(pkg)

        startForeground(NOTIFICATION_ID, buildNotification(pkg))

        if (config.preLaunchClean) {
            ShellExecutor.killBackgroundApps()
        }

        if (config.governorLock) {
            ShellExecutor.setCpuGovernor("performance")
            ShellExecutor.setGpuGovernor("performance")
        }

        if (config.adaptiveThermal) {
            Thread {
                while (activePackage != null) {
                    val temp = ShellExecutor.getThermalTemp()
                    when {
                        temp > 45f -> ShellExecutor.setCpuGovernor("schedutil")
                        temp > 40f -> ShellExecutor.setCpuGovernor("ondemand")
                        config.governorLock -> ShellExecutor.setCpuGovernor("performance")
                    }
                    Thread.sleep(3000)
                }
            }.start()
        }

        if (config.fpsUnlock) {
            ShellExecutor.setSurfaceFlingerArgs()
        }

        if (config.refreshRateLock) {
            ShellExecutor.setRefreshRate(120)
        }

        if (config.inputLatency) {
            ShellExecutor.writeToFile("/proc/sys/kernel/sched_child_runs_first", "1")
            ShellExecutor.writeToFile("/proc/sys/vm/dirty_writeback_centisecs", "500")
        }

        if (config.renderLatency) {
            ShellExecutor.execRoot("service call SurfaceFlinger 1034 i32 1")
        }

        if (config.processPriority && pid > 0) {
            ShellExecutor.setTopAppPid(pid, -1000)
        }

        if (config.preventKill && pid > 0) {
            ShellExecutor.setTopAppPid(pid, -900)
        }

        if (config.cpuAffinity && pid > 0) {
            ShellExecutor.setCpuAffinity(pid, 0x0F)
        }

        if (config.ioPriority && pid > 0) {
            ShellExecutor.setIoPriority(pid, 2, 0)
        }

        if (config.freezeBackground) {
            ShellExecutor.killBackgroundApps()
        }

        if (config.autoDnd) {
            activateDnd()
        }

        if (config.lowLatencyAudio) {
            ShellExecutor.setLowLatencyAudio(true)
        }

        if (config.screenPin) {
            ShellExecutor.setScreenPinning(true)
        }

        if (config.networkQos) {
            val uid = getUidForPackage(pkg)
            if (uid > 0) ShellExecutor.setNetworkQos(uid, true)
        }

        startService(Intent(this, OverlayService::class.java))
    }

    private fun stopBoost() {
        val pkg = activePackage ?: return
        val config = BoostConfigStore.getConfig(this, pkg)

        if (config.governorLock) {
            ShellExecutor.setCpuGovernor("schedutil")
        }

        ShellExecutor.execRoot("settings delete system peak_refresh_rate")
        ShellExecutor.execRoot("settings delete system min_refresh_rate")

        if (config.autoDnd) {
            deactivateDnd()
        }

        if (config.lowLatencyAudio) {
            ShellExecutor.setLowLatencyAudio(false)
        }

        if (config.screenPin) {
            ShellExecutor.setScreenPinning(false)
        }

        if (config.networkQos) {
            val uid = getUidForPackage(pkg)
            if (uid > 0) ShellExecutor.setNetworkQos(uid, false)
        }

        stopService(Intent(this, OverlayService::class.java))
        BoostConfigStore.setActiveApp(this, null)
        activePackage = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun activateDnd() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                val policy = NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                            NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED
                )
                nm.setNotificationPolicy(policy)
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        } catch (_: Throwable) {}
    }

    private fun deactivateDnd() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        } catch (_: Throwable) {}
    }

    private fun getUidForPackage(pkg: String): Int {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            appInfo.uid
        } catch (_: Throwable) {
            -1
        }
    }

    private fun buildNotification(pkg: String): Notification {
        val intent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AllMightApp.CHANNEL_BOOST)
            .setContentTitle(getString(R.string.boost_notification_title))
            .setContentText(getString(R.string.boost_notification_text, pkg))
            .setSmallIcon(R.drawable.ic_boost)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
