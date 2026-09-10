package com.allmightgamebooster.gusdev.xposed

import android.content.ComponentName
import android.os.IBinder
import android.util.Log
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.util.ShellExecutor
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Fondasi deteksi app masuk/keluar foreground, dipasang di scope "android" (system_server).
 * Semua fitur boost dipicu dari sini lewat TweakDispatcher.applyAll() / revertAll().
 */
object ForegroundDetectorHook {

    private const val TAG = "AllMight"
    private var currentForegroundPkg: String? = null

    fun init(module: com.allmightgamebooster.gusdev.xposed.ModuleMain, param: SystemServerStartingParam) {
        module.log(Log.INFO, TAG, "ForegroundDetector: initializing")

        try {
            // Coba beberapa classloader untuk temukan AMS
            val classloaders = listOf(
                param.classLoader,
                ClassLoader.getSystemClassLoader(),
                Thread.currentThread().contextClassLoader
            ).filterNotNull()

            var amsClass: Class<*>? = null
            for (cl in classloaders) {
                try {
                    amsClass = Class.forName(
                        "com.android.server.am.ActivityManagerService",
                        false,
                        cl
                    )
                    module.log(Log.INFO, TAG, "ForegroundDetector: AMS found via ${cl.javaClass.simpleName}")
                    break
                } catch (_: ClassNotFoundException) {}
            }

            if (amsClass == null) {
                module.log(Log.ERROR, TAG, "ForegroundDetector: AMS class not found in any classloader")
                return
            }

            val method = amsClass.declaredMethods.firstOrNull { m ->
                m.name == "updateActivityUsageStats" &&
                        m.parameterTypes.size == 5 &&
                        m.parameterTypes[0] == ComponentName::class.java &&
                        m.parameterTypes[3] == IBinder::class.java
            }

            if (method == null) {
                // Coba signature alternatif (3 params)
                val altMethod = amsClass.declaredMethods.firstOrNull { m ->
                    m.name == "updateActivityUsageStats" &&
                            m.parameterTypes.size == 3 &&
                            m.parameterTypes[0] == ComponentName::class.java
                }

                if (altMethod == null) {
                    module.log(Log.ERROR, TAG, "ForegroundDetector: updateActivityUsageStats not found")
                    val methods = amsClass.declaredMethods.filter { it.name.contains("Usage") || it.name.contains("Activity") }
                    module.log(Log.INFO, TAG, "Available methods: ${methods.joinToString { it.name }}")
                    return
                }

                altMethod.isAccessible = true
                module.hook(altMethod).intercept { chain ->
                    try {
                        val component = chain.args[0] as? ComponentName ?: return@intercept chain.proceed()
                        val pkg = component.packageName
                        val event = chain.args[1] as? Int ?: return@intercept chain.proceed()
                        when (event) {
                            1 -> onForeground(module, pkg)
                            2 -> onBackground(module, pkg)
                        }
                    } catch (e: Throwable) {
                        module.log(Log.ERROR, TAG, "ForegroundDetector error: ${e.message}")
                    }
                    chain.proceed()
                }
                module.log(Log.INFO, TAG, "ForegroundDetector: hooked (3-param signature)")
                return
            }

            method.isAccessible = true
            module.hook(method).intercept { chain ->
                try {
                    val component = chain.args[0] as? ComponentName ?: return@intercept chain.proceed()
                    val event = chain.args[2] as? Int ?: return@intercept chain.proceed()
                    val pkg = component.packageName
                    when (event) {
                        1 -> onForeground(module, pkg)
                        2 -> onBackground(module, pkg)
                    }
                } catch (e: Throwable) {
                    module.log(Log.ERROR, TAG, "ForegroundDetector error: ${e.message}")
                }
                chain.proceed()
            }
            module.log(Log.INFO, TAG, "ForegroundDetector: hooked (5-param signature)")
        } catch (e: Throwable) {
            module.log(Log.ERROR, TAG, "ForegroundDetector hook failed: ${e.message}")
        }
    }

    private fun onForeground(module: com.allmightgamebooster.gusdev.xposed.ModuleMain, pkg: String) {
        if (pkg == currentForegroundPkg) return
        currentForegroundPkg = pkg

        if (!isGlobalBoostEnabled()) return

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

    private fun isGlobalBoostEnabled(): Boolean {
        return try {
            val atClass = Class.forName("android.app.ActivityThread", false, null)
            val app = atClass.getDeclaredMethod("currentApplication").invoke(null) as? android.content.Context
                ?: return true
            app.getSharedPreferences("amgb_quick_tile", android.content.Context.MODE_PRIVATE)
                .getBoolean("boost_global_enabled", true)
        } catch (_: Throwable) { true }
    }
}
