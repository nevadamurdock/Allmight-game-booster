package com.allmightgamebooster.gusdev.xposed

import android.content.ComponentName
import android.os.IBinder
import android.util.Log
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.util.ShellExecutor
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Fondasi deteksi app masuk/keluar foreground, dipasang di scope "android" (system_server).
 * Semua fitur boost (governor, freeze, overlay, dsb) dipicu dari sini lewat
 * TweakDispatcher.applyAll() / revertAll().
 *
 * Catatan: signature updateActivityUsageStats() bisa beda antar versi Android/vendor.
 * Kalau hook gagal di device tertentu, cek signature asli lewat dex inspection.
 */
object ForegroundDetectorHook {

    private const val TAG = "AllMight"
    private var currentForegroundPkg: String? = null

    fun init(module: com.allmightgamebooster.gusdev.xposed.ModuleMain, param: PackageLoadedParam) {
        if (param.packageName != "android") return

        try {
            val amsClass = Class.forName(
                "com.android.server.am.ActivityManagerService",
                false,
                param.classLoader
            )

            val method = amsClass.declaredMethods.firstOrNull { m ->
                m.name == "updateActivityUsageStats" &&
                        m.parameterTypes.size == 5 &&
                        m.parameterTypes[0] == ComponentName::class.java &&
                        m.parameterTypes[3] == IBinder::class.java
            }

            if (method == null) {
                module.log(Log.ERROR, TAG, "ForegroundDetector: updateActivityUsageStats not found")
                return
            }

            method.isAccessible = true

            module.hook(method).intercept { chain ->
                try {
                    val component = chain.args[0] as? ComponentName ?: return@intercept chain.proceed()
                    val event = chain.args[2] as? Int ?: return@intercept chain.proceed()
                    val pkg = component.packageName

                    // UsageEvents.Event.MOVE_TO_FOREGROUND = 1, MOVE_TO_BACKGROUND = 2
                    when (event) {
                        1 -> onForeground(module, pkg)
                        2 -> onBackground(module, pkg)
                    }
                } catch (e: Throwable) {
                    module.log(Log.ERROR, TAG, "ForegroundDetector error: ${e.message}")
                }
                chain.proceed()
            }

            module.log(Log.INFO, TAG, "ForegroundDetector: hooked")
        } catch (e: Throwable) {
            module.log(Log.ERROR, TAG, "ForegroundDetector hook failed: ${e.message}")
        }
    }

    private fun onForeground(module: com.allmightgamebooster.gusdev.xposed.ModuleMain, pkg: String) {
        if (pkg == currentForegroundPkg) return
        currentForegroundPkg = pkg

        val config = BoostConfigStore.getConfigFromModule(pkg)
        val hasActive = config.governorLock || config.fpsUnlock ||
                config.processPriority || config.preventKill ||
                config.freezeBackground || config.refreshRateLock ||
                config.brightnessLock || config.inputLatency ||
                config.renderLatency || config.cpuAffinity ||
                config.ioPriority || config.autoDnd ||
                config.networkQos || config.lowLatencyAudio ||
                config.screenPin

        if (!hasActive) return

        module.log(Log.INFO, TAG, "$pkg masuk foreground -> apply()")
        TweakDispatcher.applyAll(module, pkg, config)
    }

    private fun onBackground(module: com.allmightgamebooster.gusdev.xposed.ModuleMain, pkg: String) {
        if (pkg != currentForegroundPkg) return
        currentForegroundPkg = null

        val config = BoostConfigStore.getConfigFromModule(pkg)
        val hasActive = config.governorLock || config.fpsUnlock ||
                config.processPriority || config.preventKill ||
                config.freezeBackground || config.refreshRateLock ||
                config.brightnessLock || config.inputLatency ||
                config.renderLatency || config.cpuAffinity ||
                config.ioPriority || config.autoDnd ||
                config.networkQos || config.lowLatencyAudio ||
                config.screenPin

        if (!hasActive) return

        module.log(Log.INFO, TAG, "$pkg keluar foreground -> revert()")
        TweakDispatcher.revertAll(module, pkg, config)
    }
}
