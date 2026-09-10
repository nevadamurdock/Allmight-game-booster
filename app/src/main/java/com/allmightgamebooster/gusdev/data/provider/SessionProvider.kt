package com.allmightgamebooster.gusdev.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.database.MatrixCursor

class SessionProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val sessions = com.allmightgamebooster.gusdev.data.BoostConfigStore.getSessions(context!!)
        val cursor = MatrixCursor(arrayOf(
            "package_name", "app_name", "start_time", "end_time",
            "peak_temperature", "avg_fps", "preset"
        ))
        sessions.forEach { s ->
            cursor.addRow(arrayOf(
                s.packageName, s.appName, s.startTime, s.endTime,
                s.peakTemperature.toDouble(), s.avgFps.toDouble(), s.preset.name
            ))
        }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.allmightgamebooster.sessions"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
