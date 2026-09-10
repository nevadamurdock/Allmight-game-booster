package com.allmightgamebooster.gusdev.service

import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore

class QuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val activeApp = BoostConfigStore.getActiveApp(this)
        if (activeApp != null) {
            BoostService.stop(this)
            val tile = qsTile
            tile?.state = Tile.STATE_INACTIVE
            tile?.label = getString(R.string.qs_tile_label)
            tile?.updateTile()
        } else {
            val activeConfig = getAllConfigs().firstOrNull()
            if (activeConfig != null) {
                BoostService.start(this, activeConfig.packageName)
                val tile = qsTile
                tile?.state = Tile.STATE_ACTIVE
                tile?.label = activeConfig.packageName.substringAfterLast('.')
                tile?.updateTile()
            }
        }
    }

    private fun updateTileState() {
        val activeApp = BoostConfigStore.getActiveApp(this)
        val tile = qsTile ?: return
        if (activeApp != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = activeApp.substringAfterLast('.')
            tile.contentDescription = "Boost aktif: $activeApp"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.qs_tile_label)
            tile.contentDescription = "Boost tidak aktif"
        }
        tile.updateTile()
    }

    private fun getAllConfigs() = BoostConfigStore.getAllConfigs(this).values.toList()
}
