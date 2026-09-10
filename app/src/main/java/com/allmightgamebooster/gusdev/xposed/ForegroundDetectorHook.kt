package com.allmightgamebooster.gusdev.xposed

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.ComponentName
import android.content.Context
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.model.BoostConfigData
import com.allmightgamebooster.gusdev.util.ShellExecutor
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

object ForegroundDetectorHook {

    private const val TAG = "AllMight"

    private var currentForegroundPkg: String? = null
    private var savedGovernors = emptyMap<Int, String>()
    private var savedGpuGovernor = ""
    private var savedAnimationScales = Triple(1f, 1f, 1f)
    private var savedBrightness = 128
    private var savedAutoBrightness = 1
    private var thermalThreadRunning = false

    fun init(module: com.allmightgamebooster.gusdev.xposed.ModuleMain, param: PackageLoadedParam) {
        if (param.packageName != "android") return

        module.log(android.util.Log.INFO, TAG, "ForegroundDetector: hooking system_server")

        try {
            val amsClass = Class.forName(
                "com.android.server.am.ActivityManagerService",
                false,
                param.classLoader
            )

            val updateMethod = amsClass.declaredMethods.firstOrNull {
                it.name == "updateActivityUsageStats" &&
                        it.parameterTypes.size >= 3 &&
                        it.parameterTypes[0] == ComponentName::class.java
            }

            if (updateMethod == null) {
                module.log(android.util.Log.ERROR, TAG, "ForegroundDetector: updateActivityUsageStats not found")
                return
            }

            updateMethod.isAccessible = true

            module.hook(updateMethod).intercept { chain ->
                try {
                    val component = chain.args[0] as? ComponentName ?: return@intercept chain.proceed()
                    val pkg = component.packageName
                    val action = chain.args[1] as? Int ?: return@intercept chain.proceed()

                    val config = BoostConfigStore.getConfigFromModule(pkg)
                    val hasActive = config.governorLock || config.fpsUnlock ||
                            config.processPriority || config.preventKill ||
                            config.freezeBackground || config.refreshRateLock ||
                            config.brightnessLock || config.inputLatency ||
                            config.renderLatency || config.cpuAffinity ||
                            config.ioPriority || config.autoDnd ||
                            config.networkQos || config.lowLatencyAudio ||
                            config.screenPin

                    if (!hasActive) return@intercept chain.proceed()

                    val enteringForeground = action == 1 || action == 2
                    val leavingForeground = action == 0 || action == 3

                    if (enteringForeground && currentForegroundPkg != pkg) {
                        revertAll(module)
                        currentForegroundPkg = pkg
                        applyAll(module, pkg, config)
                    } else if (leavingForeground && currentForegroundPkg == pkg) {
                        revertAll(module)
                        currentForegroundPkg = null
                    }
                } catch (e: Throwable) {
                    module.log(android.util.Log.ERROR, TAG, "ForegroundDetector error: ${e.message}")
                }

                chain.proceed()
            }

            module.log(android.util.Log.INFO, TAG, "ForegroundDetector: hooked successfully")
        } catch (e: Throwable) {
            module.log(android.util.Log.ERROR, TAG, "ForegroundDetector hook failed: ${e.message}")
        }
    }

    private fun applyAll(module: com.allmightgamebooster.gusdev.xposed.ModuleMain, pkg: String, config: BoostConfigData) {
        module.log(android.util.Log.INFO, TAG, "apply() → $pkg")
        val pid = ShellExecutor.getProcessPid(pkg)

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
        if (config.freezeBackground) {
            val myPid = android.os.Process.myPid()
            ShellExecutor.getRunningApps().filter { it != pkg }.take(20).forEach { other ->
                val otherPid = ShellExecutor.getProcessPid(other)
                if (otherPid > 0 && otherPid != myPid) ShellExecutor.freezeProcess(otherPid)
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
    }

    private fun revertAll(module: com.allmightgamebooster.gusdev.xposed.ModuleMain) {
        val pkg = currentForegroundPkg ?: return
        module.log(android.util.Log.INFO, TAG, "revert() ← $pkg")
        val config = BoostConfigStore.getConfigFromModule(pkg)

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
}
