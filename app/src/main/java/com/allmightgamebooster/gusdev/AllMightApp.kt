package com.allmightgamebooster.gusdev

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AllMightApp : Application() {

    companion object {
        const val CHANNEL_BOOST = "boost_service"
        const val CHANNEL_OVERLAY = "overlay_monitor"
        lateinit var instance: AllMightApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val boostChannel = NotificationChannel(
            CHANNEL_BOOST,
            "Game Boost",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifikasi saat mode boost aktif"
            setShowBadge(false)
        }

        val overlayChannel = NotificationChannel(
            CHANNEL_OVERLAY,
            "Overlay Monitor",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Overlay monitoring suhu, FPS, clock"
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(boostChannel, overlayChannel))
    }
}
