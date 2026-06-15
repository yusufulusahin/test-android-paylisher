package com.paylisher.test

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.paylisher.Paylisher
import com.paylisher.PaylisherDeepLinkConfig
import com.paylisher.PaylisherDeferredDeepLinkConfig
import com.paylisher.RepeatedIdentifyBehavior
import com.paylisher.android.PaylisherAndroid
import com.paylisher.android.PaylisherAndroidConfig
import com.paylisher.android.PaylisherDeferredDeepLinkManager
import com.paylisher.android.PaylisherEngageInAppConfig
import com.paylisher.android.notification.FcmMessagingService

class PaylisherTestApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

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

            // Deeplink: custom scheme + app link + auth-gate gerektiren hedefler.
            deepLinkConfig = PaylisherDeepLinkConfig(
                captureDeepLinkEvents = true,
                autoHandleDeepLinks = true,
                authRequiredDestinations = listOf("wallet"),
                customSchemes = listOf("paylishertest"),
                universalLinkDomains = listOf("link.paylisher.com"),
                debugLogging = true,
            )
            // Deferred deeplink (ilk kurulum attribution) — test profili (1 saat pencere, debug açık).
            deferredDeepLinkConfig = PaylisherDeferredDeepLinkConfig.forTesting().withEnabled(true)

            // Engage API Pull mode: in-app mesajları SDK ile Engage'den çek (mirrors
            // dietapp DietApplication.kt). teamId/projectId/sourceId/sdkKey zorunlu
            // değil — Engage public sdkKey'den (SDK apiKey'i) reverse-resolve eder.
            // LOCAL: fetchEndpoint = "http://10.0.2.2:1924/v1/push/inapp/fetch"
            // LAN:   fetchEndpoint = "http://10.254.132.106:1924/v1/push/inapp/fetch"
            // Test projesi EU/test ortamında (app-eu.paylisher.com) → Engage endpoint'i de EU.
            engageInAppConfig = PaylisherEngageInAppConfig(
                fetchEndpoint = "https://api-eu.paylisher.com/engage/v1/push/inapp/fetch",
            ).apply {
                autoFetchOnForeground = true
                maxMessages = 5
                debugLogging = true
            }
        }
        PaylisherAndroid.setup(this, config)

        // Deeplink: closure handler — PaylisherDeepLinkHandler interface'ini implement etmeye gerek YOK.
        // Application'da kaydedildiği için cold-start (kapalı uygulamaya gelen link) de yakalanır;
        // Activity yalnızca onNewIntent'i forward eder.
        PaylisherAndroid.onDeepLink { dl, requiresAuth -> DeepLinkBus.onReceive(dl, requiresAuth) }
        PaylisherAndroid.onDeepLinkRequiresAuth { dl, complete -> DeepLinkBus.onAuthRequired(dl, complete) }
        PaylisherAndroid.onDeepLinkFailed { url, error -> DeepLinkBus.failed(url, error) }

        // İlk açılışta deferred deeplink kontrolü (ilk kurulumda match aranır).
        if (PaylisherDeferredDeepLinkManager.isSetup()) {
            PaylisherDeferredDeepLinkManager.getInstance().check(
                onSuccess = { dl -> DeepLinkBus.deferredMatch(dl) },
                onNoMatch = { DeepLinkBus.setDeferredStatus("ℹ️ no-match (organik kurulum)") },
                onError = { e -> DeepLinkBus.setDeferredStatus("❌ error: ${e.message}") },
            )
        }

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
