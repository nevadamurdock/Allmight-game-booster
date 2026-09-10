package com.allmightgamebooster.gusdev.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.allmightgamebooster.gusdev.data.BoostConfigStore
import com.allmightgamebooster.gusdev.service.BoostService

class BatteryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val activeApp = BoostConfigStore.getActiveApp(context) ?: return

        when (intent.action) {
            Intent.ACTION_BATTERY_LOW -> {
                val threshold = BoostConfigStore.getBatteryThreshold(context)
                val level = intent.getIntExtra("level", 0)
                if (level <= threshold) {
                    BoostService.stop(context)
                }
            }
        }
    }
}
