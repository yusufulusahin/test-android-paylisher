package com.paylisher.test

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.paylisher.android.PaylisherAndroid
import com.paylisher.android.notification.FcmMessagingService

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.util.Log.d("FCM", "Bildirim izni: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            PaylisherTestApp()
        }
        // Deeplink handler PaylisherTestApplication'da kaydedildi; cold-start intent'i SDK
        // ActivityLifecycleCallbacks ile OTOMATİK işliyor. Activity'de sadece onNewIntent kaldı.
    }

    override fun onResume() {
        super.onResume()
        // In-app mesajların gösterilebilmesi için aktif Activity'yi SDK'ya ver
        FcmMessagingService.setMainActivity(this)
    }

    // Uygulama açıkken gelen deeplink (warm start). launchMode=singleTop + setIntent şart.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PaylisherAndroid.handleDeepLink(intent)
    }
}
