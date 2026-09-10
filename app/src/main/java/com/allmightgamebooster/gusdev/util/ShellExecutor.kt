package com.allmightgamebooster.gusdev.util

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellExecutor {

    fun exec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun execRoot(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun writeToFile(path: String, value: String) {
        execRoot("echo '$value' > $path")
    }

    fun readFromFile(path: String): String {
        return exec("cat $path")
    }

    fun setCpuGovernor(governor: String) {
        for (i in 0..7) {
            writeToFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor", governor)
        }
    }

    fun setGpuGovernor(governor: String) {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/devfreq/gpufreq/governor",
            "/sys/kernel/gpu/gpu_governor",
            "/sys/class/devfreq/soc:qcom,kgsl-busmon/governor",
            "/sys/devices/platform/soc/soc:qcom,kgsl-busmon.0/devfreq/soc:qcom,kgsl-busmon.0/governor"
        )
        paths.forEach { path ->
            val content = readFromFile(path).trim()
            if (content.isNotBlank() && content != "error") {
                writeToFile(path, governor)
            }
        }
    }

    fun getCpuFreq(): List<Pair<Int, Long>> {
        val result = mutableListOf<Pair<Int, Long>>()
        for (i in 0..7) {
            val freq = readFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            freq.trim().toLongOrNull()?.let { result.add(Pair(i, it)) }
        }
        return result
    }

    fun getThermalTemp(): Float {
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp"
        )
        for (path in thermalPaths) {
            val raw = readFromFile(path).trim()
            raw.toFloatOrNull()?.let { temp ->
                return if (temp > 1000) temp / 1000f else temp
            }
        }
        return 0f
    }

    fun setTopAppPid(pid: Int, adj: Int) {
        execRoot("echo $adj > /proc/$pid/oom_score_adj")
    }

    fun freezeProcess(pid: Int) {
        execRoot("kill -STOP $pid")
    }

    fun unfreezeProcess(pid: Int) {
        execRoot("kill -CONT $pid")
    }

    fun setCpuAffinity(pid: Int, mask: Int) {
        execRoot("taskset -p ${Integer.toHexString(mask)} $pid")
    }

    fun setIoPriority(pid: Int, classValue: Int, priority: Int) {
        execRoot("ionice -c $classValue -n $priority -p $pid")
    }

    fun setSurfaceFlingerArgs() {
        execRoot("service call SurfaceFlinger 1034 i32 1")
    }

    fun setNetworkQos(uid: Int, enable: Boolean) {
        if (enable) {
            execRoot("iptables -A OUTPUT -m owner --uid-owner $uid -j ACCEPT")
        } else {
            execRoot("iptables -D OUTPUT -m owner --uid-owner $uid -j ACCEPT")
        }
    }

    fun setLowLatencyAudio(enable: Boolean) {
        writeToFile("/proc/sys/kernel/sched_tunable_scaling", "0")
        val hdaPath = "/sys/module/snd_hda_intel/parameters/power_save"
        val content = readFromFile(hdaPath).trim()
        if (content.isNotBlank() && content != "error") {
            writeToFile(hdaPath, if (enable) "0" else "1")
        }
    }

    fun setRefreshRate(rate: Int) {
        execRoot("settings put system peak_refresh_rate $rate")
        execRoot("settings put system min_refresh_rate $rate")
    }

    fun unlockRefreshRate() {
        execRoot("settings delete system peak_refresh_rate")
        execRoot("settings delete system min_refresh_rate")
    }

    fun setBrightness(value: Int) {
        execRoot("settings put system screen_brightness $value")
    }

    fun setScreenPinning(enabled: Boolean) {
        execRoot("settings put secure lock_to_app_enabled ${if (enabled) 1 else 0}")
    }

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    fun getProcessPid(pkg: String): Int {
        val output = exec("pidof $pkg")
        return output.trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: -1
    }

    fun getRamUsage(): Pair<Long, Long> {
        val memInfo = exec("cat /proc/meminfo")
        var total = 0L
        var available = 0L
        memInfo.lines().forEach { line ->
            when {
                line.startsWith("MemTotal:") -> total = line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0
                line.startsWith("MemAvailable:") -> available = line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0
            }
        }
        return Pair(total, available)
    }

    fun killBackgroundApps() {
        val output = exec("ps -A")
        val myPid = android.os.Process.myPid()
        val myPackage = "com.allmightgamebooster.gusdev"
        output.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                val pid = parts[1].toIntOrNull() ?: return@forEach
                val name = parts.last()
                if (pid != myPid && pid > 1000 && !name.contains(myPackage)) {
                    execRoot("kill -9 $pid")
                }
            }
        }
    }
}
