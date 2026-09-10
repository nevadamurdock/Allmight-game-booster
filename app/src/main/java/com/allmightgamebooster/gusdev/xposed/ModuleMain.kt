package com.allmightgamebooster.gusdev.xposed

import android.util.Log
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
        log(Log.INFO, TAG, "Package loaded: $pkg")

        if (pkg == "android") {
            ForegroundDetectorHook.init(this, param)
        }

        applyRootHideHooks(param)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        log(Log.INFO, TAG, "Package ready: ${param.packageName}")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log(Log.INFO, TAG, "System server starting")
    }

    // ── Root Hide (PRD §21) ──────────────────────────────────

    private fun applyRootHideHooks(param: PackageLoadedParam) {
        val pkg = param.packageName
        val hiddenPackages = try {
            com.allmightgamebooster.gusdev.util.RootHideConfig.getHiddenPackages()
        } catch (_: Throwable) { emptySet() }

        if (pkg !in hiddenPackages) return
        log(Log.INFO, TAG, "Hiding root for: $pkg")

        try {
            val buildClass = Class.forName("android.os.Build", false, param.defaultClassLoader)
            val tagField = buildClass.getDeclaredField("TAG")
            tagField.isAccessible = true
            tagField.set(null, "Android")

            hookRootIndicators(param)
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Root hide failed: ${e.message}")
        }
    }

    private fun hookRootIndicators(param: PackageLoadedParam) {
        try {
            val fileClass = Class.forName("java.io.File", false, param.defaultClassLoader)

            hook(fileClass.getMethod("exists")).intercept { chain ->
                val path = (chain.thisObject as java.io.File).absolutePath
                if (ROOT_PATHS.any { path.contains(it, ignoreCase = true) }) false
                else chain.proceed()
            }

            hook(fileClass.getMethod("canExecute")).intercept { chain ->
                val path = (chain.thisObject as java.io.File).absolutePath
                if (ROOT_PATHS.any { path.contains(it, ignoreCase = true) }) false
                else chain.proceed()
            }

            hook(fileClass.getMethod("isFile")).intercept { chain ->
                val path = (chain.thisObject as java.io.File).absolutePath
                if (ROOT_PATHS.any { path.contains(it, ignoreCase = true) }) false
                else chain.proceed()
            }
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "File hook error: ${e.message}")
        }
    }
}
