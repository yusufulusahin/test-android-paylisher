package com.paylisher.test

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * "Banka tarafının kendi NotificationManager'ı" simülasyonu.
 *
 * Gerçek mobil bankacılık uygulamalarında bu sınıfın işlevini
 * encryption + decrypt, deep link routing, kendi template'leri olan
 * daha kompleks bir yapı görür. Burada amacımız sadece banka push
 * akışının bizim Paylisher SDK 1.1.0 forward kodumuz tarafından
 * ETKİLENMEDİĞİNİ kanıtlamak — o yüzden basit bir NotificationCompat
 * builder yeterli.
 */
object BankNotificationManager {

    const val CHANNEL_ID = "bank_channel"
    private const val CHANNEL_NAME = "Banka Bildirimleri"
    private const val TAG = "BankNotif"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Banka uygulamasının kendi bildirim kanalı (Paylisher değil)"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Banka push payload'unu kendi template'iyle çizer.
     *
     * GERÇEK bankada burada şunlar olurdu:
     *   - Payload decrypt
     *   - Deep link intent oluşturma
     *   - Analytics event
     *   - Kullanıcı bazlı segmentation
     *
     * Test için sadece basit bir notification çiziyoruz.
     */
    fun showBankNotification(context: Context, data: Map<String, String>) {
        val title = data["title"] ?: "Banka Bildirimi"
        val body = data["body"] ?: ""
        val txId = data["tx_id"]

        Log.d(TAG, "showBankNotification | title=$title | body=$body | tx_id=$txId")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationId = (System.currentTimeMillis() and 0xFFFFFF).toInt()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.d(TAG, "Bank notification shown | id=$notificationId")
        } catch (e: SecurityException) {
            Log.e(TAG, "Bildirim izni yok? POST_NOTIFICATIONS verilmemiş olabilir.", e)
        }
    }
}
