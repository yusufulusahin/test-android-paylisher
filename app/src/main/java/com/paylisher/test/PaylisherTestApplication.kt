package com.paylisher.test

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.paylisher.Paylisher
import com.paylisher.RepeatedIdentifyBehavior
import com.paylisher.android.PaylisherAndroid
import com.paylisher.android.PaylisherAndroidConfig
import com.paylisher.android.notification.FcmMessagingService

class PaylisherTestApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 0. Banka kendi notification channel'ı (test simülasyonu)
        BankNotificationManager.createChannel(this)

        // 1. SDK Setup
        val config = PaylisherAndroidConfig(
            apiKey = "phc_3wZe1GW8GRdeUGQK0LqaS25PEDUNS9EBSxe7FiQFqQW",
            host = "https://ds-tr.paylisher.com"
        ).apply {
            debug = true
            flushAt = 1
            captureApplicationLifecycleEvents = true
            captureScreenViews = true
            repeatedIdentifyBehavior = RepeatedIdentifyBehavior.CAPTURE
        }
        PaylisherAndroid.setup(this, config)

        // 2. Push tıklandığında açılacak Activity
        FcmMessagingService.setNotificationIntentClass(MainActivity::class.java)

        // 3. Device ID super property
        Paylisher.register("deviceID", DeviceIdHelper.get(this))

        // 4. FCM token al ve Paylisher'a kaydet
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                if (!token.isNullOrBlank()) {
                    Paylisher.register("token", token)
                    getSharedPreferences("paylisher_prefs", MODE_PRIVATE)
                        .edit().putString("fcm_token", token).apply()
                    Log.d("FCM", "Token kaydedildi: $token")
                }
            } else {
                Log.w("FCM", "Token alınamadı", task.exception)
            }
        }
    }
}
