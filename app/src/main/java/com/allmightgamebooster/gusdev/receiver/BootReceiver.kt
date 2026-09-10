package com.allmightgamebooster.gusdev.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Auto-restore boost if was active before reboot
            // Currently just log — user re-activates manually
        }
    }
}
