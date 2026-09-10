package com.allmightgamebooster.gusdev.data

import android.content.Context
import android.content.SharedPreferences
import com.allmightgamebooster.gusdev.model.AppBoostConfig
import com.allmightgamebooster.gusdev.model.Preset
import com.allmightgamebooster.gusdev.model.BoostConfigData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object BoostConfigStore {

    private const val PREFS_NAME = "amgb_boost_configs"
    private const val KEY_CONFIGS = "configs"
    private const val KEY_ACTIVE_APP = "active_app"
    private const val KEY_BATTERY_THRESHOLD = "battery_threshold"
    private const val KEY_DEFAULT_PRESET = "default_preset"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_OVERLAY_X = "overlay_x"
    private const val KEY_OVERLAY_Y = "overlay_y"

    private const val MODULE_CONFIG_DIR = "/data/local/tmp/amgb"
    private const val MODULE_CONFIG_FILE = "$MODULE_CONFIG_DIR/boost_configs.json"

    private val gson = Gson()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(ctx: Context, done: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    fun getAllConfigs(ctx: Context): Map<String, AppBoostConfig> {
        val json = prefs(ctx).getString(KEY_CONFIGS, "{}") ?: "{}"
        val type = object : TypeToken<Map<String, AppBoostConfig>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getConfig(ctx: Context, pkg: String): AppBoostConfig {
        val all = getAllConfigs(ctx)
        return all[pkg] ?: AppBoostConfig(pkg).also {
            saveConfig(ctx, it)
        }
    }

    fun saveConfig(ctx: Context, config: AppBoostConfig) {
        val all = getAllConfigs(ctx).toMutableMap()
        all[config.packageName] = config
        prefs(ctx).edit().putString(KEY_CONFIGS, gson.toJson(all)).apply()
        syncConfigsToFile(all.values.toList())
    }

    fun removeConfig(ctx: Context, pkg: String) {
        val all = getAllConfigs(ctx).toMutableMap()
        all.remove(pkg)
        prefs(ctx).edit().putString(KEY_CONFIGS, gson.toJson(all)).apply()
        syncConfigsToFile(all.values.toList())
    }

    private fun syncConfigsToFile(configs: List<AppBoostConfig>) {
        try {
            val dir = File(MODULE_CONFIG_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(MODULE_CONFIG_FILE)
            file.writeText(gson.toJson(configs))
            Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 644 $MODULE_CONFIG_FILE")).waitFor()
        } catch (_: Throwable) {}
    }

    fun getActiveApp(ctx: Context): String? =
        prefs(ctx).getString(KEY_ACTIVE_APP, null)

    fun setActiveApp(ctx: Context, pkg: String?) {
        prefs(ctx).edit().putString(KEY_ACTIVE_APP, pkg).apply()
    }

    fun getBatteryThreshold(ctx: Context): Int =
        prefs(ctx).getInt(KEY_BATTERY_THRESHOLD, 20)

    fun setBatteryThreshold(ctx: Context, percent: Int) {
        prefs(ctx).edit().putInt(KEY_BATTERY_THRESHOLD, percent).apply()
    }

    fun getDefaultPreset(ctx: Context): Preset =
        Preset.fromName(prefs(ctx).getString(KEY_DEFAULT_PRESET, "BALANCED") ?: "BALANCED")

    fun setDefaultPreset(ctx: Context, preset: Preset) {
        prefs(ctx).edit().putString(KEY_DEFAULT_PRESET, preset.name).apply()
    }

    fun getOverlayPosition(ctx: Context): Pair<Int, Int> {
        val x = prefs(ctx).getInt(KEY_OVERLAY_X, -1)
        val y = prefs(ctx).getInt(KEY_OVERLAY_Y, -1)
        return Pair(x, y)
    }

    fun setOverlayPosition(ctx: Context, x: Int, y: Int) {
        prefs(ctx).edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply()
    }

    fun saveSession(ctx: Context, record: com.allmightgamebooster.gusdev.model.SessionRecord) {
        val json = prefs(ctx).getString(KEY_SESSIONS, "[]") ?: "[]"
        val type = object : TypeToken<MutableList<com.allmightgamebooster.gusdev.model.SessionRecord>>() {}.type
        val list: MutableList<com.allmightgamebooster.gusdev.model.SessionRecord> = gson.fromJson(json, type)
        list.add(0, record)
        if (list.size > 200) list.subList(200, list.size).clear()
        prefs(ctx).edit().putString(KEY_SESSIONS, gson.toJson(list)).apply()
    }

    fun getSessions(ctx: Context): List<com.allmightgamebooster.gusdev.model.SessionRecord> {
        val json = prefs(ctx).getString(KEY_SESSIONS, "[]") ?: "[]"
        val type = object : TypeToken<List<com.allmightgamebooster.gusdev.model.SessionRecord>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getConfigFromModule(pkg: String): BoostConfigData {
        return try {
            val file = File(MODULE_CONFIG_FILE)
            if (!file.exists()) return BoostConfigData()
            val json = file.readText()
            val type = object : TypeToken<List<AppBoostConfig>>() {}.type
            val configs: List<AppBoostConfig> = gson.fromJson(json, type)
            val config = configs.find { it.packageName == pkg } ?: return BoostConfigData()
            BoostConfigData(
                packageName = config.packageName,
                governorLock = config.governorLock,
                adaptiveThermal = config.adaptiveThermal,
                fpsUnlock = config.fpsUnlock,
                freezeBackground = config.freezeBackground,
                preLaunchClean = config.preLaunchClean,
                cpuAffinity = config.cpuAffinity,
                ioPriority = config.ioPriority,
                refreshRateLock = config.refreshRateLock,
                brightnessLock = config.brightnessLock,
                inputLatency = config.inputLatency,
                renderLatency = config.renderLatency,
                processPriority = config.processPriority,
                preventKill = config.preventKill,
                autoDnd = config.autoDnd,
                networkQos = config.networkQos,
                lowLatencyAudio = config.lowLatencyAudio,
                screenPin = config.screenPin,
                rootHide = config.rootHide
            )
        } catch (_: Throwable) {
            BoostConfigData()
        }
    }
}
