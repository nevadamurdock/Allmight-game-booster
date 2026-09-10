package com.allmightgamebooster.gusdev.xposed

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.content.Intent
import android.util.Log
import com.allmightgamebooster.gusdev.model.BoostConfigData
import com.allmightgamebooster.gusdev.util.ShellExecutor

/**
 * Titik orkestrasi tunggal yang dipanggil ForegroundDetectorHook.
 * PresetManager menentukan Tweak mana yang aktif sesuai preset,
 * lalu apply/revert masing-masing dari sini.
 */
object TweakDispatcher {

    private const val TAG = "AllMight"

    private var savedGovernors = emptyMap<Int, String>()
    private var savedGpuGovernor = ""
    private var savedAnimationScales = Triple(1f, 1f, 1f)
    private var savedBrightness = 128
    private var savedAutoBrightness = 1
    private var thermalThreadRunning = false
    private var boostStartTime = 0L

    fun applyAll(module: com.allmightgamebooster.gusdev.xposed.ModuleMain, pkg: String, config: BoostConfigData) {
        module.log(Log.INFO, TAG, "TweakDispatcher.applyAll($pkg)")
        boostStartTime = System.currentTimeMillis()
        val pid = ShellExecutor.getProcessPid(pkg)

        // Governor
        if (config.governorLock) {
            savedGovernors = ShellExecutor.getCpuGovernor()
            savedGpuGovernor = ShellExecutor.getGpuGovernor()
            ShellExecutor.setCpuGovernor("performance")
            ShellExecutor.setGpuGovernor("performance")
        }

        if (config.adaptiveThermal) {
            thermalThreadRunning = true
            Thread {
                var checks = 0
                while (thermalThreadRunning && checks < 600) {
                    val temp = ShellExecutor.getThermalTemp()
                    when {
                        temp > 45f -> ShellExecutor.setCpuGovernor("schedutil")
                        temp > 40f -> ShellExecutor.setCpuGovernor("ondemand")
                        config.governorLock -> ShellExecutor.setCpuGovernor("performance")
                    }
                    checks++
                    Thread.sleep(3000)
                }
                thermalThreadRunning = false
            }.start()
        }

        // Process
        if (config.processPriority && pid > 0) {
            ShellExecutor.setOomScoreAdj(pid, -1000)
            ShellExecutor.addToDozeWhitelist(pkg)
        }
        if (config.preventKill && pid > 0) {
            ShellExecutor.setOomScoreAdj(pid, -900)
        }
        if (config.cpuAffinity && pid > 0) {
            ShellExecutor.moveToCpuset(pid, "top-app")
        }
        if (config.ioPriority && pid > 0) {
            ShellExecutor.setIoPriority(pid, 1, 0)
        }

        // Display
        if (config.fpsUnlock) ShellExecutor.unlockFps()
        if (config.refreshRateLock) ShellExecutor.setRefreshRate(120)

        if (config.brightnessLock) {
            savedBrightness = ShellExecutor.getBrightness()
            savedAutoBrightness = ShellExecutor.getAutoBrightness()
            ShellExecutor.setAutoBrightness(false)
            ShellExecutor.setBrightness(255)
        }

        // Latency
        if (config.inputLatency) {
            savedAnimationScales = ShellExecutor.getAnimationScales()
            ShellExecutor.setAnimationScales(0f, 0f, 0f)
            ShellExecutor.writeToFile("/proc/sys/kernel/sched_child_runs_first", "1")
            ShellExecutor.writeToFile("/proc/sys/vm/dirty_writeback_centisecs", "500")
        }
        if (config.renderLatency) ShellExecutor.unlockFps()

        // Freeze
        if (config.freezeBackground) {
            val myPid = android.os.Process.myPid()
            ShellExecutor.getRunningApps().filter { it != pkg }.take(20).forEach { other ->
                val otherPid = ShellExecutor.getProcessPid(other)
                if (otherPid > 0 && otherPid != myPid) ShellExecutor.freezeProcess(otherPid)
            }
        }

        // DND
        if (config.autoDnd) activateDnd()

        // Audio
        if (config.lowLatencyAudio) {
            ShellExecutor.writeToFile("/proc/sys/kernel/sched_tunable_scaling", "0")
        }

        // Screen Pin
        if (config.screenPin) ShellExecutor.setScreenPinning(true)

        // Network
        if (config.networkQos) {
            val uid = getUidForPackage(pkg)
            if (uid > 0) ShellExecutor.setupNetworkQos(uid)
        }

        // Start overlay
        startOverlay()
    }

    fun revertAll(module: com.allmightgamebooster.gusdev.xposed.ModuleMain, pkg: String, config: BoostConfigData) {
        module.log(Log.INFO, TAG, "TweakDispatcher.revertAll($pkg)")

        if (config.governorLock) {
            savedGovernors.forEach { (cpu, gov) ->
                ShellExecutor.writeToFile("/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor", gov)
            }
            if (savedGpuGovernor.isNotBlank()) ShellExecutor.setGpuGovernor(savedGpuGovernor)
        }
        if (config.refreshRateLock) ShellExecutor.unlockRefreshRate()
        if (config.brightnessLock) {
            ShellExecutor.setAutoBrightness(savedAutoBrightness == 1)
            ShellExecutor.setBrightness(savedBrightness)
        }
        if (config.inputLatency) {
            ShellExecutor.setAnimationScales(savedAnimationScales.first, savedAnimationScales.second, savedAnimationScales.third)
            ShellExecutor.writeToFile("/proc/sys/kernel/sched_child_runs_first", "0")
            ShellExecutor.writeToFile("/proc/sys/vm/dirty_writeback_centisecs", "5000")
        }
        if (config.autoDnd) deactivateDnd()
        if (config.screenPin) ShellExecutor.setScreenPinning(false)
        if (config.networkQos) {
            val uid = getUidForPackage(pkg)
            if (uid > 0) ShellExecutor.removeNetworkQos(uid)
        }
        if (config.processPriority) ShellExecutor.removeFromDozeWhitelist(pkg)
        thermalThreadRunning = false

        // Stop overlay + save session
        stopOverlay()
        saveSession(pkg, config)
    }

    private fun activateDnd() {
        try {
            val atClass = Class.forName("android.app.ActivityThread", false, null)
            val app = atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context ?: return
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setNotificationPolicy(Policy(
                    Policy.PRIORITY_CATEGORY_CALLS or Policy.PRIORITY_CATEGORY_MESSAGES,
                    Policy.PRIORITY_SENDERS_STARRED,
                    Policy.PRIORITY_SENDERS_STARRED
                ))
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        } catch (_: Throwable) {}
    }

    private fun deactivateDnd() {
        try {
            val atClass = Class.forName("android.app.ActivityThread", false, null)
            val app = atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context ?: return
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        } catch (_: Throwable) {}
    }

    private fun getUidForPackage(pkg: String): Int {
        return try {
            val atClass = Class.forName("android.app.ActivityThread", false, null)
            val app = atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context ?: return -1
            app.packageManager.getApplicationInfo(pkg, 0).uid
        } catch (_: Throwable) { -1 }
    }

    private fun startOverlay() {
        try {
            val atClass = Class.forName("android.app.ActivityThread", false, null)
            val app = atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context ?: return
            val intent = Intent(app, com.allmightgamebooster.gusdev.service.OverlayService::class.java)
            app.startService(intent)
        } catch (_: Throwable) {}
    }

    private fun stopOverlay() {
        try {
            val atClass = Class.forName("android.app.ActivityThread", false, null)
            val app = atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context ?: return
            val intent = Intent(app, com.allmightgamebooster.gusdev.service.OverlayService::class.java)
            app.stopService(intent)
        } catch (_: Throwable) {}
    }

    private fun saveSession(pkg: String, config: BoostConfigData) {
        try {
            val atClass = Class.forName("android.app.ActivityThread", false, null)
            val app = atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context ?: return

            val appLabel = try {
                app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Throwable) { pkg }

            val preset = try {
                com.allmightgamebooster.gusdev.model.Preset.fromName(config.packageName)
            } catch (_: Throwable) { com.allmightgamebooster.gusdev.model.Preset.BALANCED }

            val record = com.allmightgamebooster.gusdev.model.SessionRecord(
                packageName = pkg,
                appName = appLabel,
                startTime = boostStartTime,
                endTime = System.currentTimeMillis(),
                peakTemperature = com.allmightgamebooster.gusdev.service.MonitorService.peakTemperature,
                avgFps = com.allmightgamebooster.gusdev.service.MonitorService.avgFps,
                preset = com.allmightgamebooster.gusdev.model.Preset.BALANCED
            )
            com.allmightgamebooster.gusdev.data.BoostConfigStore.saveSession(app, record)
        } catch (_: Throwable) {}
    }
}
