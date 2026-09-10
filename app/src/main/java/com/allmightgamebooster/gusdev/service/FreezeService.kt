package com.allmightgamebooster.gusdev.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.allmightgamebooster.gusdev.util.ShellExecutor

class FreezeService : Service() {

    companion object {
        private var frozen = false
        fun isFrozen(): Boolean = frozen
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pkg = intent?.getStringExtra("package") ?: return START_NOT_STICKY
        Thread {
            frozen = true
            ShellExecutor.killBackgroundApps()
        }.start()
        return START_NOT_STICKY
    }

    fun unfreezeAll() {
        frozen = false
    }
}
