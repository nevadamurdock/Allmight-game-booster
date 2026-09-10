package com.allmightgamebooster.gusdev.xposed

import android.content.ComponentName
import android.os.IBinder
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class ModuleMain : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "AllMight"
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        XposedBridge.log("[$TAG] Module initialized in Zygote")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName

        // Root hide hooks di dalam proses target app
        applyRootHideHooks(lpparam)

        // ForegroundDetector hanya di system_server
        if (pkg == "android") {
            ForegroundDetectorHook.init(lpparam.classLoader)
        }
    }

    // ── Root Hide (PRD §21) ──────────────────────────────────────

    private fun applyRootHideHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        val hiddenPackages = try {
            com.allmightgamebooster.gusdev.util.RootHideConfig.getHiddenPackages()
        } catch (_: Throwable) { emptySet() }

        if (pkg !in hiddenPackages) return
        XposedBridge.log("[$TAG] Hiding root for: $pkg")

        try {
            val buildClass = Class.forName("android.os.Build", false, lpparam.classLoader)
            XposedHelpers.setStaticObjectField(buildClass, "TAG", "Android")
            hookRootIndicators(lpparam)
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Root hide failed: ${e.message}")
        }
    }

    private fun hookRootIndicators(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val fileClass = Class.forName("java.io.File", false, lpparam.classLoader)
            val rootPaths = listOf(
                "/su/bin/su", "/system/xbin/su", "/system/bin/su",
                "/sbin/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su",
                "/data/local/su", "/su/bin/supolicy",
                "/dev/socket/su daemon", "/system/app/Superuser.apk",
                "/system/app/SuperSU.apk", "/data/adb/magisk",
                "/sbin/.magisk", "/data/adb/modules"
            )

            XposedHelpers.findAndHookMethod(fileClass, "exists", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val path = (param.thisObject as java.io.File).absolutePath
                    if (rootPaths.any { path.contains(it, ignoreCase = true) }) {
                        param.result = false
                    }
                }
            })

            XposedHelpers.findAndHookMethod(fileClass, "canExecute", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val path = (param.thisObject as java.io.File).absolutePath
                    if (rootPaths.any { path.contains(it, ignoreCase = true) }) {
                        param.result = false
                    }
                }
            })

            XposedHelpers.findAndHookMethod(fileClass, "isFile", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val path = (param.thisObject as java.io.File).absolutePath
                    if (rootPaths.any { path.contains(it, ignoreCase = true) }) {
                        param.result = false
                    }
                }
            })
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] File hook error: ${e.message}")
        }
    }
}
