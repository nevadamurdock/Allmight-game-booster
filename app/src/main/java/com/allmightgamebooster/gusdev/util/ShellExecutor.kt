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
        val cpuDirs = exec("ls /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor")
        cpuDirs.lines().filter { it.isNotBlank() }.forEach { path ->
            writeToFile(path.trim(), governor)
        }
    }

    fun setGpuGovernor(governor: String) {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/devfreq/gpufreq/governor",
            "/sys/kernel/gpu/gpu_governor"
        )
        paths.forEach { path ->
            if (readFromFile(path).isNotBlank()) {
                writeToFile(path, governor)
            }
        }
    }

    fun getCpuFreq(): List<Pair<Int, Long>> {
        val result = mutableListOf<Pair<Int, Long>>()
        for (i in 0..7) {
            val freq = readFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            freq.toLongOrNull()?.let { result.add(Pair(i, it)) }
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

    fun setGpuFreq(freqKhz: Long) {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/kernel/gpu/gpu_max_freq"
        )
        paths.forEach { writeToFile(it, freqKhz.toString()) }
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

    fun setCpuAffinity(pid: Int, mask: Long) {
        execRoot("taskset -p ${mask.toString(16)} $pid")
    }

    fun setIoPriority(pid: Int, classValue: Int, priority: Int) {
        execRoot("ionice -c $classValue -n $priority -p $pid")
    }

    fun setSurfaceFlingerArgs() {
        execRoot("service call SurfaceFlinger 1034 i32 1")
    }

    fun setRenderThreadPriority(pid: Int, priority: Int) {
        execRoot("renice -n $priority -p $pid")
    }

    fun setNetworkQos(uid: Int, enable: Boolean) {
        if (enable) {
            execRoot("iptables -A OUTPUT -m owner --uid-owner $uid -j ACCEPT")
        } else {
            execRoot("iptables -D OUTPUT -m owner --uid-owner $uid -j ACCEPT")
        }
    }

    fun setLowLatencyAudio(enable: Boolean) {
        val value = if (enable) "1" else "0"
        writeToFile("/proc/sys/kernel/sched_tunable_scaling", "0")
        writeToFile("/sys/module/snd_hda_intel/parameters/power_save", if (enable) "0" else "1")
    }

    fun setRefreshRate(rate: Int) {
        execRoot("settings put system peak_refresh_rate $rate")
        execRoot("settings put system min_refresh_rate $rate")
    }

    fun setScreenPinning(enabled: Boolean) {
        execRoot("settings put secure lock_to_app_enabled ${if (enabled) 1 else 0}")
    }

    fun hideRootForPackage(pkg: String) {
        execRoot("magisk hide --add $pkg")
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

    fun getInstalledApps(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val output = exec("pm list packages -3")
        output.lines().filter { it.startsWith("package:") }.forEach { line ->
            val pkg = line.removePrefix("package:").trim()
            val label = exec("pm dump $pkg | head -1")
            result.add(Pair(pkg, label.ifBlank { pkg }))
        }
        return result
    }

    fun getProcessPid(pkg: String): Int {
        val output = exec("pidof $pkg")
        return output.trim().split(" ").firstOrNull()?.toIntOrNull() ?: -1
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
        val output = exec("ps -A -o PID,NAME")
        val myPid = android.os.Process.myPid()
        output.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                val pid = parts[0].toIntOrNull() ?: return@forEach
                if (pid != myPid && pid > 1000) {
                    execRoot("kill -9 $pid")
                }
            }
        }
    }
}
