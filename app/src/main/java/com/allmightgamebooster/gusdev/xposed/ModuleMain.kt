package com.allmightgamebooster.gusdev.xposed

import android.util.Log
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.model.BoostConfigData
import com.allmightgamebooster.gusdev.util.ShellExecutor
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class ModuleMain : XposedModule() {

    companion object {
        const val TAG = "AllMight"

        private val ROOT_PATHS = listOf(
            "/su/bin/su", "/system/xbin/su", "/system/bin/su",
            "/sbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/su/bin/supolicy",
            "/dev/socket/su daemon", "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk", "/data/adb/magisk",
            "/sbin/.magisk", "/data/adb/modules"
        )
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "All Might Game Booster loaded in: ${param.processName}")
        log(Log.INFO, TAG, "framework: $frameworkName ($frameworkVersionCode) API $apiVersion")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val pkg = param.packageName
        log(Log.INFO, TAG, "Package loaded: $pkg (uid=${param.uid})")

        applyRootHideHooks(param)
        applyBoostHooks(param)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        log(Log.INFO, TAG, "Package ready: ${param.packageName}")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log(Log.INFO, TAG, "System server starting")
    }

    private fun applyRootHideHooks(param: PackageLoadedParam) {
        val pkg = param.packageName
        val hiddenPackages = try {
            com.allmightgamebooster.gusdev.util.RootHideConfig.getHiddenPackages()
        } catch (_: Throwable) {
            emptySet()
        }

        if (pkg !in hiddenPackages) return
        log(Log.INFO, TAG, "Hiding root for: $pkg")

        try {
            val buildClass = Class.forName("android.os.Build", false, param.defaultClassLoader)
            val tagField = buildClass.getDeclaredField("TAG")
            tagField.isAccessible = true
            tagField.set(null, "Android")

            hookRootIndicators(param)
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Root hide setup failed: ${e.message}")
        }
    }

    private fun hookRootIndicators(param: PackageLoadedParam) {
        try {
            val fileClass = Class.forName("java.io.File", false, param.defaultClassLoader)
            val existsMethod = fileClass.getMethod("exists")

            hook(existsMethod).intercept { chain ->
                val file = chain.thisObject as java.io.File
                val path = file.absolutePath
                if (ROOT_PATHS.any { path.contains(it, ignoreCase = true) }) {
                    false
                } else {
                    chain.proceed()
                }
            }

            val canExecuteMethod = fileClass.getMethod("canExecute")
            hook(canExecuteMethod).intercept { chain ->
                val file = chain.thisObject as java.io.File
                val path = file.absolutePath
                if (ROOT_PATHS.any { path.contains(it, ignoreCase = true) }) {
                    false
                } else {
                    chain.proceed()
                }
            }

            val isFileMethod = fileClass.getMethod("isFile")
            hook(isFileMethod).intercept { chain ->
                val file = chain.thisObject as java.io.File
                val path = file.absolutePath
                if (ROOT_PATHS.any { path.contains(it, ignoreCase = true) }) {
                    false
                } else {
                    chain.proceed()
                }
            }
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "File hook error: ${e.message}")
        }
    }

    private fun applyBoostHooks(param: PackageLoadedParam) {
        val pkg = param.packageName
        val config = try {
            BoostConfigStore.getConfigFromModule(pkg)
        } catch (_: Throwable) {
            return
        }

        if (!hasActiveBoostFeatures(config)) return

        log(Log.INFO, TAG, "Applying boost hooks for: $pkg")

        try {
            applyGovernorHooks(config)
            applyProcessHooks(config, param)
            applyDisplayHooks(config)
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Boost hook error: ${e.message}")
        }
    }

    private fun hasActiveBoostFeatures(config: BoostConfigData): Boolean {
        return config.governorLock || config.adaptiveThermal || config.fpsUnlock ||
                config.freezeBackground || config.cpuAffinity || config.ioPriority ||
                config.refreshRateLock || config.inputLatency || config.renderLatency ||
                config.processPriority || config.preventKill || config.screenPin ||
                config.networkQos || config.lowLatencyAudio
    }

    private fun applyGovernorHooks(config: BoostConfigData) {
        if (config.governorLock) {
            ShellExecutor.setCpuGovernor("performance")
            ShellExecutor.setGpuGovernor("performance")
            log(Log.INFO, TAG, "Governor locked to performance")
        }

        if (config.adaptiveThermal) {
            Thread {
                while (true) {
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
    }

    private fun applyProcessHooks(config: BoostConfigData, param: PackageLoadedParam) {
        val pkg = param.packageName
        val pid = ShellExecutor.getProcessPid(pkg)

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
    }

    private fun applyDisplayHooks(config: BoostConfigData) {
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

        if (config.screenPin) {
            ShellExecutor.setScreenPinning(true)
        }

        if (config.lowLatencyAudio) {
            ShellExecutor.setLowLatencyAudio(true)
        }
    }
}
