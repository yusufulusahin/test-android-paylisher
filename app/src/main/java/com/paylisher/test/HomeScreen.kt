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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paylisher.Paylisher
import java.text.SimpleDateFormat
import java.util.*

private data class EventButton(val labelRes: Int, val event: String, val props: Map<String, String>)

private val EVENT_BUTTONS = listOf(
    EventButton(R.string.event_screen_view,    "screen_view",    mapOf("screen" to "home")),
    EventButton(R.string.event_product_click,  "product_click",  mapOf("product_id" to "abc123")),
    EventButton(R.string.event_add_to_cart,    "add_to_cart",    mapOf("product_id" to "abc123", "price" to "99.9")),
    EventButton(R.string.event_checkout_start, "checkout_start", mapOf("amount" to "99.9")),
)

private data class BankPushButton(val labelRes: Int, val titleRes: Int, val bodyRes: Int, val txPrefix: String)

private val BANK_PUSH_BUTTONS = listOf(
    BankPushButton(R.string.bank_transfer_button, R.string.bank_transfer_title, R.string.bank_transfer_body, "TRX"),
    BankPushButton(R.string.bank_3ds_button,      R.string.bank_3ds_title,      R.string.bank_3ds_body,      "AUTH"),
)

/** Ana Sayfa sekmesi — SDK event ve multi-SDK push testleri. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabContent() {
    val context = LocalContext.current
    val logs = remember { mutableStateListOf<String>() }
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }, actions = { LanguageMenu() }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Paylisher deeplink test uygulaması. Linkler gerçek ekranlara yönlenir; aşağıdan SDK event/push testlerini de tetikleyebilirsin.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline
                )
            }

            items(EVENT_BUTTONS) { btn ->
                OutlinedButton(
                    onClick = {
                        Paylisher.capture(btn.event, properties = btn.props)
                        logs.add("[${sdf.format(Date())}] ${btn.event}")
                        Log.d("SDK", "capture(${btn.event}) props: ${btn.props}")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(btn.labelRes)) }
            }

            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.bank_sim_card_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.bank_sim_card_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            items(BANK_PUSH_BUTTONS) { btn ->
                OutlinedButton(
                    onClick = {
                        val payload = mapOf(
                            "title" to context.getString(btn.titleRes),
                            "body" to context.getString(btn.bodyRes),
                            "tx_id" to "${btn.txPrefix}-${System.currentTimeMillis()}"
                        )
                        BankFcmService.simulateBankPushFromTestApp(context, payload)
                        logs.add("[${sdf.format(Date())}] BANK push: ${payload["title"]}")
                        Log.d("BankSim", "Triggered: $payload")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(btn.labelRes)) }
            }

            if (logs.isNotEmpty()) {
                item { Text(stringResource(R.string.home_events_log_header), style = MaterialTheme.typography.labelMedium) }
                items(logs.takeLast(20).reversed()) { log ->
                    Text(log, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
