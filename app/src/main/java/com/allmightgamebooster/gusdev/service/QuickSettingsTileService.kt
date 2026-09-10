package com.allmightgamebooster.gusdev.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore

class QuickSettingsTileService : TileService() {

    companion object {
        private const val PREFS_NAME = "amgb_quick_tile"
        private const val KEY_GLOBAL_BOOST = "boost_global_enabled"

        fun isGlobalBoostEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_GLOBAL_BOOST, false)
        }
    }

    private fun prefs(): SharedPreferences =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val wasEnabled = prefs().getBoolean(KEY_GLOBAL_BOOST, false)
        val newState = !wasEnabled

        prefs().edit().putBoolean(KEY_GLOBAL_BOOST, newState).apply()

        if (newState) {
            val apps = BoostConfigStore.getAllConfigs(this).values
                .filter { it.preset != com.allmightgamebooster.gusdev.model.Preset.BATTERY_SAVER }
            val firstApp = apps.firstOrNull()
            if (firstApp != null) {
                BoostService.start(this, firstApp.packageName)
            }
        } else {
            BoostService.stop(this)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val enabled = prefs().getBoolean(KEY_GLOBAL_BOOST, false)

        if (enabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Boost On"
            tile.contentDescription = "Boost aktif"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.qs_tile_label)
            tile.contentDescription = "Boost tidak aktif"
        }
        tile.updateTile()
    }
}
