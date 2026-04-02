package com.paylisher.test

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paylisher.Paylisher
import java.text.SimpleDateFormat
import java.util.*

private val EVENT_BUTTONS = listOf(
    Triple("Ekran Görüntülendi", "screen_view",   mapOf("screen" to "home")),
    Triple("Ürün Tıklandı",     "product_click",  mapOf("product_id" to "abc123")),
    Triple("Sepete Eklendi",    "add_to_cart",    mapOf("product_id" to "abc123", "price" to "99.9")),
    Triple("Ödeme Başlatıldı",  "checkout_start", mapOf("amount" to "99.9")),
)

@Composable
fun HomeScreen(userId: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val deviceId = remember { DeviceIdHelper.get(context) }
    val logs = remember { mutableStateListOf<String>() }
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("Home") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Oturum bilgisi
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("userId: $userId", style = MaterialTheme.typography.bodyMedium)
                        Text("deviceID: $deviceId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Event butonları
            items(EVENT_BUTTONS) { (label, event, props) ->
                OutlinedButton(
                    onClick = {
                        Paylisher.capture(event, properties = props)
                        logs.add("[${sdf.format(Date())}] $event")
                        Log.d("SDK", "capture($event) props: $props")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(label) }
            }

            // Log listesi
            if (logs.isNotEmpty()) {
                item {
                    Text("Gönderilen Eventler:", style = MaterialTheme.typography.labelMedium)
                }
                items(logs.takeLast(20).reversed()) { log ->
                    Text(log, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Logout
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        Paylisher.reset()
                        Paylisher.register("deviceID", deviceId)
                        Log.d("SDK", "reset() + register()  deviceID: $deviceId")
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Çıkış Yap (reset)") }
            }
        }
    }
}
