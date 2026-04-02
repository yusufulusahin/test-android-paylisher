package com.paylisher.test

import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceIdHelper {
    private const val PREF = "paylisher_prefs"
    private const val KEY = "device_id"

    fun get(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") return androidId

        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY, it).apply()
        }
    }
}
