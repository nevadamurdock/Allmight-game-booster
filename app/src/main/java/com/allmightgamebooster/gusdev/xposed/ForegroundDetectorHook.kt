package com.allmightgamebooster.gusdev.xposed

import android.content.ComponentName
import android.os.IBinder
import android.util.Log
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Fondasi deteksi app masuk/keluar foreground.
 * Dipasang di scope "android" (system_server) via handleLoadPackage.
 */
object ForegroundDetectorHook {

    private const val TAG = "AllMight"
    private var currentForegroundPkg: String? = null

    fun init(classLoader: ClassLoader) {
        XposedBridge.log("[$TAG] ForegroundDetector: initializing")

        try {
            val amsClass = Class.forName(
                "com.android.server.am.ActivityManagerService",
                false,
                classLoader
            )

            // Cari method dengan signature yang benar
            val method = amsClass.declaredMethods.firstOrNull { m ->
                m.name == "updateActivityUsageStats" &&
                        m.parameterTypes.size == 5 &&
                        m.parameterTypes[0] == ComponentName::class.java
            }

            if (method == null) {
                // Fallback: coba 3-param signature
                val altMethod = amsClass.declaredMethods.firstOrNull { m ->
                    m.name == "updateActivityUsageStats" &&
                            m.parameterTypes.size == 3 &&
                            m.parameterTypes[0] == ComponentName::class.java
                }
                if (altMethod == null) {
                    XposedBridge.log("[$TAG] ForegroundDetector: method not found")
                    val candidates = amsClass.declaredMethods.filter {
                        it.name.contains("Usage") || it.name.contains("Activity")
                    }.map { "${it.name}(${it.parameterTypes.joinToString { it.simpleName }})" }
                    XposedBridge.log("[$TAG] Candidates: $candidates")
                    return
                }
                hookMethod(altMethod, isThreeParam = true)
                return
            }

            hookMethod(method, isThreeParam = false)
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] ForegroundDetector hook failed: ${e.message}")
        }
    }

    private fun hookMethod(method: java.lang.reflect.Method, isThreeParam: Boolean) {
        method.isAccessible = true

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val component = param.args[0] as? ComponentName ?: return
                    val event = if (isThreeParam) {
                        param.args[1] as? Int ?: return
                    } else {
                        param.args[2] as? Int ?: return
                    }
                    val pkg = component.packageName

                    when (event) {
                        1 -> onForeground(pkg)
                        2 -> onBackground(pkg)
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("[$TAG] ForegroundDetector error: ${e.message}")
                }
            }
        })

        val sig = if (isThreeParam) "3-param" else "5-param"
        XposedBridge.log("[$TAG] ForegroundDetector: hooked ($sig signature)")
    }

    private fun onForeground(pkg: String) {
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

        XposedBridge.log("[$TAG] $pkg masuk foreground -> apply()")
        TweakDispatcher.applyAll(pkg, config)
    }

    private fun onBackground(pkg: String) {
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

        XposedBridge.log("[$TAG] $pkg keluar foreground -> revert()")
        TweakDispatcher.revertAll(pkg, config)
    }
}
