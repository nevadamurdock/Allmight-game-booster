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
import com.allmightgamebooster.gusdev.model.AppBoostConfig
import com.allmightgamebooster.gusdev.model.Preset
import com.allmightgamebooster.gusdev.model.SessionRecord
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
    private var boostStartTime: Long = 0L

    private var savedCpuGovernors = emptyMap<Int, String>()
    private var savedGpuGovernor = ""
    private var savedAnimationScales = Triple(1f, 1f, 1f)
    private var savedBrightness = 128
    private var savedAutoBrightness = 1
    private var savedBoostPid = -1

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
        boostStartTime = System.currentTimeMillis()
        BoostConfigStore.setActiveApp(this, pkg)

        val config = BoostConfigStore.getConfig(this, pkg)
        savedBoostPid = ShellExecutor.getProcessPid(pkg)

        startForeground(NOTIFICATION_ID, buildNotification(pkg))

        if (config.preLaunchClean) {
            ShellExecutor.getRunningApps().filter { it != pkg }.take(20).forEach {
                ShellExecutor.forceStopApp(it)
            }
        }

        if (config.governorLock) {
            savedCpuGovernors = ShellExecutor.getCpuGovernor()
            savedGpuGovernor = ShellExecutor.getGpuGovernor()
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
            ShellExecutor.unlockFps()
        }

        if (config.refreshRateLock) {
            ShellExecutor.setRefreshRate(120)
        }

        if (config.brightnessLock) {
            savedBrightness = ShellExecutor.getBrightness()
            savedAutoBrightness = ShellExecutor.getAutoBrightness()
            ShellExecutor.setAutoBrightness(false)
            ShellExecutor.setBrightness(255)
        }

        if (config.inputLatency) {
            savedAnimationScales = ShellExecutor.getAnimationScales()
            ShellExecutor.setAnimationScales(0f, 0f, 0f)
            ShellExecutor.writeToFile("/proc/sys/kernel/sched_child_runs_first", "1")
            ShellExecutor.writeToFile("/proc/sys/vm/dirty_writeback_centisecs", "500")
        }

        if (config.renderLatency) {
            ShellExecutor.unlockFps()
        }

        if (config.processPriority && savedBoostPid > 0) {
            ShellExecutor.setOomScoreAdj(savedBoostPid, -1000)
            ShellExecutor.addToDozeWhitelist(pkg)
        }

        if (config.preventKill && savedBoostPid > 0) {
            ShellExecutor.setOomScoreAdj(savedBoostPid, -900)
        }

        if (config.cpuAffinity && savedBoostPid > 0) {
            ShellExecutor.moveToCpuset(savedBoostPid, "top-app")
        }

        if (config.ioPriority && savedBoostPid > 0) {
            ShellExecutor.setIoPriority(savedBoostPid, 1, 0)
        }

        if (config.freezeBackground) {
            val myPid = android.os.Process.myPid()
            ShellExecutor.getRunningApps().filter { it != pkg }.forEach { otherPkg ->
                val pid = ShellExecutor.getProcessPid(otherPkg)
                if (pid > 0 && pid != myPid) ShellExecutor.freezeProcess(pid)
            }
        }

        if (config.autoDnd) {
            activateDnd()
        }

        if (config.lowLatencyAudio) {
            ShellExecutor.writeToFile("/proc/sys/kernel/sched_tunable_scaling", "0")
        }

        if (config.screenPin) {
            ShellExecutor.setScreenPinning(true)
        }

        if (config.networkQos) {
            val uid = getUidForPackage(pkg)
            if (uid > 0) ShellExecutor.setupNetworkQos(uid)
        }

        startService(Intent(this, OverlayService::class.java))
    }

    private fun stopBoost() {
        val pkg = activePackage ?: return
        val config = BoostConfigStore.getConfig(this, pkg)

        if (config.governorLock) {
            savedCpuGovernors.forEach { (cpu, gov) ->
                ShellExecutor.writeToFile("/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor", gov)
            }
            if (savedGpuGovernor.isNotBlank()) ShellExecutor.setGpuGovernor(savedGpuGovernor)
        }

        if (config.refreshRateLock) {
            ShellExecutor.unlockRefreshRate()
        }

        if (config.brightnessLock) {
            ShellExecutor.setAutoBrightness(savedAutoBrightness == 1)
            ShellExecutor.setBrightness(savedBrightness)
        }

        if (config.inputLatency) {
            ShellExecutor.setAnimationScales(savedAnimationScales.first, savedAnimationScales.second, savedAnimationScales.third)
            ShellExecutor.writeToFile("/proc/sys/kernel/sched_child_runs_first", "0")
            ShellExecutor.writeToFile("/proc/sys/vm/dirty_writeback_centisecs", "5000")
        }

        if (config.autoDnd) {
            deactivateDnd()
        }

        if (config.screenPin) {
            ShellExecutor.setScreenPinning(false)
        }

        if (config.networkQos) {
            val uid = getUidForPackage(pkg)
            if (uid > 0) ShellExecutor.removeNetworkQos(uid)
        }

        if (config.processPriority) {
            ShellExecutor.removeFromDozeWhitelist(pkg)
        }

        saveSession(pkg, config.preset)

        stopService(Intent(this, OverlayService::class.java))
        BoostConfigStore.setActiveApp(this, null)
        activePackage = null
        savedBoostPid = -1

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveSession(pkg: String, preset: Preset) {
        val peakTemp = MonitorService.peakTemperature
        val avgFps = MonitorService.avgFps
        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Throwable) { pkg }

        val record = SessionRecord(
            packageName = pkg,
            appName = appLabel,
            startTime = boostStartTime,
            endTime = System.currentTimeMillis(),
            peakTemperature = peakTemp,
            avgFps = avgFps,
            preset = preset
        )
        BoostConfigStore.saveSession(this, record)
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
            packageManager.getApplicationInfo(pkg, 0).uid
        } catch (_: Throwable) { -1 }
    }

    private fun buildNotification(pkg: String): Notification {
        val intent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, AllMightApp.CHANNEL_BOOST)
            .setContentTitle(getString(R.string.boost_notification_title))
            .setContentText(getString(R.string.boost_notification_text, pkg))
            .setSmallIcon(R.drawable.ic_boost)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
