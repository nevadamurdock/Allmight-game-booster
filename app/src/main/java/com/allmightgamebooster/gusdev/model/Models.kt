package com.allmightgamebooster.gusdev.model

enum class Preset(val label: String) {
    PERFORMANCE("Performance"),
    BALANCED("Balanced"),
    BATTERY_SAVER("Battery Saver");

    companion object {
        fun fromName(name: String): Preset = entries.find { it.name == name } ?: BALANCED
    }
}

data class AppBoostConfig(
    val packageName: String,
    var preset: Preset = Preset.BALANCED,
    var governorLock: Boolean = false,
    var adaptiveThermal: Boolean = false,
    var fpsUnlock: Boolean = false,
    var freezeBackground: Boolean = false,
    var preLaunchClean: Boolean = false,
    var cpuAffinity: Boolean = false,
    var ioPriority: Boolean = false,
    var refreshRateLock: Boolean = false,
    var brightnessLock: Boolean = false,
    var inputLatency: Boolean = false,
    var renderLatency: Boolean = false,
    var processPriority: Boolean = false,
    var preventKill: Boolean = false,
    var autoDnd: Boolean = false,
    var networkQos: Boolean = false,
    var lowLatencyAudio: Boolean = false,
    var screenPin: Boolean = false,
    var rootHide: Boolean = false
) {
    fun applyPreset(p: Preset) {
        preset = p
        when (p) {
            Preset.PERFORMANCE -> {
                governorLock = true
                adaptiveThermal = true
                fpsUnlock = true
                freezeBackground = true
                preLaunchClean = true
                cpuAffinity = true
                ioPriority = true
                refreshRateLock = true
                inputLatency = true
                renderLatency = true
                processPriority = true
                preventKill = true
                autoDnd = true
                networkQos = true
                lowLatencyAudio = true
                screenPin = false
                rootHide = false
                brightnessLock = false
            }
            Preset.BALANCED -> {
                governorLock = false
                adaptiveThermal = true
                fpsUnlock = true
                freezeBackground = false
                preLaunchClean = true
                cpuAffinity = false
                ioPriority = true
                refreshRateLock = true
                inputLatency = true
                renderLatency = false
                processPriority = true
                preventKill = false
                autoDnd = false
                networkQos = false
                lowLatencyAudio = true
                screenPin = false
                rootHide = false
                brightnessLock = false
            }
            Preset.BATTERY_SAVER -> {
                governorLock = false
                adaptiveThermal = true
                fpsUnlock = false
                freezeBackground = true
                preLaunchClean = true
                cpuAffinity = false
                ioPriority = false
                refreshRateLock = false
                inputLatency = false
                renderLatency = false
                processPriority = false
                preventKill = false
                autoDnd = false
                networkQos = false
                lowLatencyAudio = false
                screenPin = false
                rootHide = false
                brightnessLock = false
            }
        }
    }
}

data class SessionRecord(
    val packageName: String,
    val appName: String,
    val startTime: Long,
    val endTime: Long,
    val peakTemperature: Float,
    val avgFps: Float,
    val preset: Preset
) {
    val durationMinutes: Long get() = (endTime - startTime) / 60000L
}

data class BoostConfigData(
    val packageName: String = "",
    val governorLock: Boolean = false,
    val adaptiveThermal: Boolean = false,
    val fpsUnlock: Boolean = false,
    val freezeBackground: Boolean = false,
    val preLaunchClean: Boolean = false,
    val cpuAffinity: Boolean = false,
    val ioPriority: Boolean = false,
    val refreshRateLock: Boolean = false,
    val brightnessLock: Boolean = false,
    val inputLatency: Boolean = false,
    val renderLatency: Boolean = false,
    val processPriority: Boolean = false,
    val preventKill: Boolean = false,
    val autoDnd: Boolean = false,
    val networkQos: Boolean = false,
    val lowLatencyAudio: Boolean = false,
    val screenPin: Boolean = false,
    val rootHide: Boolean = false
)
