package com.paylisher.test

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.paylisher.android.notification.FcmMessagingService

/**
 * "Banka senaryosu" — SDK 1.1.1 **static forward** pattern'i.
 *
 * Önceki versiyonda (SDK 1.1.0) `BankFcmService : FcmMessagingService()` şeklindeydi
 * (extend pattern). Bu çalışıyordu ama çoklu push-SDK kullanan host'lar için yetersizdi
 * çünkü Java/Kotlin multiple inheritance yasak — banka aynı anda hem Paylisher hem
 * X SDK'yı extend edemez.
 *
 * SDK 1.1.1'de eklenen `FcmMessagingService.handleRemoteMessage(...)` statik API
 * sayesinde host kendi `FirebaseMessagingService`'ini doğrudan extend edip Paylisher
 * mesajlarını **fonksiyon çağrısıyla** forward edebilir. Aynı pattern X SDK, Y SDK
 * vb. için de uygulanabilir; multiple inheritance kısıtı bertaraf olur.
 */
class BankFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "[BANK] onNewToken (forwarding to Paylisher SDK): $token")

        // Paylisher SDK'ya statik bildirim
        FcmMessagingService.handleNewToken(token)

        // Diğer SDK'lar olsaydı burada onların API'leri çağrılırdı:
        // XSdk.setPushToken(token)
        // YSdk.setPushToken(token)

        // Banka kendi backend'ine token kaydı
        // bankBackend.registerDeviceToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val source = data["source"]

        if (source == "Paylisher") {
            Log.d(TAG, "[BANK] Paylisher mesajı → SDK'ya STATIC forward (type=${data["type"]}, pushId=${data["pushId"]})")
            FcmMessagingService.handleRemoteMessage(this, remoteMessage)
            return
        }

        // Diğer SDK'lar varsa burada kontrol edilir:
        // if (source == "XSdk") { XSdk.handleRemoteMessage(this, remoteMessage); return }
        // if (source == "YSdk") { YSdk.handleRemoteMessage(this, remoteMessage); return }

        // Banka kendi push'u
        Log.d(TAG, "[BANK] Banka mesajı (Paylisher değil) | data=$data")
        BankNotificationManager.showBankNotification(applicationContext, data)
    }

    companion object {
        private const val TAG = "BankFCM"

        /**
         * HomeScreen'deki "Banka Push'u Simüle Et" butonu için.
         */
        fun simulateBankPushFromTestApp(context: Context, data: Map<String, String>) {
            Log.d(TAG, "[BANK SIM] Banka push'u simüle ediliyor | data=$data")
            BankNotificationManager.showBankNotification(context, data)
        }
    }
}
