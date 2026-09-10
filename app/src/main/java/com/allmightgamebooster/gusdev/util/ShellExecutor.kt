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
        return exec("cat $path 2>/dev/null")
    }

    // ── Governor ──────────────────────────────────────────────────

    fun getCpuGovernor(): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for (i in 0..7) {
            val gov = readFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor").trim()
            if (gov.isNotBlank() && gov != "error") result[i] = gov
        }
        return result
    }

    fun setCpuGovernor(governor: String) {
        for (i in 0..7) {
            writeToFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor", governor)
        }
    }

    fun getGpuGovernorPath(): String? {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/devfreq/gpufreq/governor",
            "/sys/kernel/gpu/gpu_governor",
            "/sys/class/devfreq/soc:qcom,kgsl-busmon/governor",
            "/sys/class/misc/mali0/device/devfreq/governor",
            "/sys/devices/platform/mali.0/devfreq/mali.0/governor"
        )
        return paths.firstOrNull { readFromFile(it).trim().let { v -> v.isNotBlank() && v != "error" } }
    }

    fun getGpuGovernor(): String {
        return getGpuGovernorPath()?.let { readFromFile(it).trim() } ?: "unknown"
    }

    fun setGpuGovernor(governor: String) {
        getGpuGovernorPath()?.let { writeToFile(it, governor) }
    }

    // ── CPU Frequency ─────────────────────────────────────────────

    fun getCpuFreq(): List<Pair<Int, Long>> {
        val result = mutableListOf<Pair<Int, Long>>()
        for (i in 0..7) {
            val freq = readFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").trim()
            freq.toLongOrNull()?.let { result.add(Pair(i, it)) }
        }
        return result
    }

    // ── Thermal ───────────────────────────────────────────────────

    fun getThermalTemp(): Float {
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
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

    // ── Process Priority (PRD §3) ─────────────────────────────────

    fun getProcessPid(pkg: String): Int {
        val output = exec("pidof $pkg")
        return output.trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: -1
    }

    fun setOomScoreAdj(pid: Int, adj: Int) {
        writeToFile("/proc/$pid/oom_score_adj", adj.toString())
    }

    fun addToDozeWhitelist(pkg: String) {
        execRoot("dumpsys deviceidle whitelist +$pkg")
    }

    fun removeFromDozeWhitelist(pkg: String) {
        execRoot("dumpsys deviceidle whitelist -$pkg")
    }

    // ── CPU Affinity via cpuset (PRD §5) ──────────────────────────

    fun moveToCpuset(pid: Int, group: String) {
        writeToFile("/dev/cpuset/$group/tasks", pid.toString())
    }

    // ── Freeze via cgroup freezer (PRD §7) ────────────────────────

    fun freezeProcess(pid: Int) {
        val cgroupPath = findCgroupFreezerPath(pid)
        if (cgroupPath != null) {
            writeToFile("$cgroupPath/freezer.state", "FROZEN")
        } else {
            execRoot("kill -STOP $pid")
        }
    }

    fun unfreezeProcess(pid: Int) {
        val cgroupPath = findCgroupFreezerPath(pid)
        if (cgroupPath != null) {
            writeToFile("$cgroupPath/freezer.state", "THAWED")
        } else {
            execRoot("kill -CONT $pid")
        }
    }

    private fun findCgroupFreezerPath(pid: Int): String? {
        val cgroupV1 = readFromFile("/proc/$pid/cgroup").trim()
        val freezerLine = cgroupV1.lines().firstOrNull { it.contains("freezer") }
        if (freezerLine != null) {
            val parts = freezerLine.split(":")
            if (parts.size >= 3) {
                val path = parts[2].trim()
                val fullPath = "/sys/fs/cgroup/freezer$path"
                if (readFromFile("$fullPath/freezer.state").trim().isNotBlank()) return fullPath
            }
        }
        val cgroupV2Path = "/sys/fs/cgroup/uid_${getUidForPid(pid)}_pid_$pid"
        if (readFromFile("$cgroupV2Path/cgroup.freeze").trim().isNotBlank()) return cgroupV2Path
        return null
    }

    private fun getUidForPid(pid: Int): Int {
        val status = readFromFile("/proc/$pid/status")
        return status.lines().firstOrNull { it.startsWith("Uid:") }
            ?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    // ── Network QoS (PRD §8) ──────────────────────────────────────

    fun setupNetworkQos(uid: Int) {
        execRoot("iptables -t mangle -A OUTPUT -m owner --uid-owner $uid -j MARK --set-mark 1")
        execRoot("tc qdisc add dev wlan0 root handle 1: htb default 10")
        execRoot("tc class add dev wlan0 parent 1: classid 1:1 htb rate 100mbit ceil 100mbit")
        execRoot("tc class add dev wlan0 parent 1:1 classid 1:10 htb rate 80mbit ceil 100mbit")
        execRoot("tc class add dev wlan0 parent 1:1 classid 1:20 htb rate 20mbit ceil 40mbit")
        execRoot("tc filter add dev wlan0 protocol ip parent 1:0 prio 1 handle 1 fw flowid 1:10")
        execRoot("tc filter add dev wlan0 protocol ip parent 1:0 prio 2 flowid 1:20")
    }

    fun removeNetworkQos(uid: Int) {
        execRoot("iptables -t mangle -D OUTPUT -m owner --uid-owner $uid -j MARK --set-mark 1")
        execRoot("tc qdisc del dev wlan0 root 2>/dev/null")
    }

    // ── Latency (PRD §4) ──────────────────────────────────────────

    fun setAnimationScales(animation: Float, transition: Float, animator: Float) {
        execRoot("settings put global window_animation_scale $animation")
        execRoot("settings put global transition_animation_scale $transition")
        execRoot("settings put global animator_duration_scale $animator")
    }

    fun getAnimationScales(): Triple<Float, Float, Float> {
        val a = exec("settings get global window_animation_scale").trim().toFloatOrNull() ?: 1f
        val t = exec("settings get global transition_animation_scale").trim().toFloatOrNull() ?: 1f
        val d = exec("settings get global animator_duration_scale").trim().toFloatOrNull() ?: 1f
        return Triple(a, t, d)
    }

    // ── I/O Priority (PRD §14) ────────────────────────────────────

    fun setIoPriority(pid: Int, classValue: Int, priority: Int) {
        val hasIonice = readFromFile("/system/bin/ionice").trim().isNotBlank()
        if (hasIonice) {
            execRoot("ionice -c $classValue -n $priority -p $pid")
        }
    }

    // ── Display (PRD §17) ─────────────────────────────────────────

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

    fun getAutoBrightness(): Int {
        return exec("settings get system screen_brightness_mode").trim().toIntOrNull() ?: 1
    }

    fun setAutoBrightness(enabled: Boolean) {
        execRoot("settings put system screen_brightness_mode ${if (enabled) 1 else 0}")
    }

    fun getBrightness(): Int {
        return exec("settings get system screen_brightness").trim().toIntOrNull() ?: 128
    }

    // ── Screen Pinning (PRD §19) ──────────────────────────────────

    fun setScreenPinning(enabled: Boolean) {
        execRoot("settings put secure lock_to_app_enabled ${if (enabled) 1 else 0}")
    }

    // ── SurfaceFlinger ────────────────────────────────────────────

    fun unlockFps() {
        execRoot("service call SurfaceFlinger 1034 i32 1")
    }

    // ── FPS Reading ───────────────────────────────────────────────

    fun getFpsFromDumpsys(): Float {
        val output = exec("dumpsys SurfaceFlinger --latency")
        val lines = output.lines().filter { it.contains("\t") }
        if (lines.size < 2) return 0f
        var totalFrameTime = 0L
        var count = 0
        for (i in 1 until minOf(lines.size, 129)) {
            val parts = lines[i].split("\t")
            if (parts.size >= 3) {
                val present = parts[1].trim().toLongOrNull() ?: continue
                val desired = parts[0].trim().toLongOrNull() ?: continue
                val delta = present - desired
                if (delta in 0..100_000_000) {
                    totalFrameTime += delta
                    count++
                }
            }
        }
        return if (count > 0) (count * 1_000_000_000f) / totalFrameTime else 0f
    }

    // ── Pre-Launch Clean (PRD §18) ────────────────────────────────

    fun forceStopApp(pkg: String) {
        execRoot("am force-stop $pkg")
    }

    fun getRunningApps(): List<String> {
        val output = exec("pm list packages -3")
        return output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
    }

    // ── RootHide (PRD §21) ────────────────────────────────────────

    fun detectRootManager(): String {
        if (exec("ksud --version").contains("ksud", ignoreCase = true)) return "kernelsu"
        if (exec("magisk --version").contains("Magisk", ignoreCase = true)) return "magisk"
        if (exec("apatch --version").contains("apatch", ignoreCase = true)) return "apatch"
        return "unknown"
    }

    fun hideRootAdd(pkg: String) {
        when (detectRootManager()) {
            "kernelsu" -> execRoot("ksud module hide --add $pkg")
            "magisk" -> execRoot("magisk denylist add $pkg")
            "apatch" -> execRoot("apatch hide add $pkg")
        }
    }

    fun hideRootRemove(pkg: String) {
        when (detectRootManager()) {
            "kernelsu" -> execRoot("ksud module hide --remove $pkg")
            "magisk" -> execRoot("magisk denylist rm $pkg")
            "apatch" -> execRoot("apatch hide remove $pkg")
        }
    }

    // ── Misc ──────────────────────────────────────────────────────

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
}
