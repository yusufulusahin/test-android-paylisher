package com.paylisher.test

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.paylisher.android.PaylisherDeepLinkManager

/**
 * Deeplink Log & Manuel Test (Profil → Geliştirici). Asıl test gerçek ekranlarla yapılır;
 * bu ekran debug içindir: handler olaylarını listeler + Studio'ya gitmeden hızlı URL denemesi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepLinkTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var campaignKey by remember { mutableStateOf(DeepLinkTestConfig.DEFAULT_CAMPAIGN_KEY) }

    val events by DeepLinkBus.events.collectAsState()
    val deferredStatus by DeepLinkBus.deferredStatus.collectAsState()

    val scheme = DeepLinkTestConfig.SCHEME
    val domain = DeepLinkTestConfig.DOMAIN

    val customUrls = listOf(
        "Ana Sayfa" to "$scheme://home",
        "Ürünler" to "$scheme://products",
        "Ürün A detay" to "$scheme://products/a",
        "Ürün A içerik (en iç)" to "$scheme://products/a/content",
        "Kampanyalar" to "$scheme://campaigns",
        "Çeyiz Hesabı detay" to "$scheme://campaigns/ceyiz",
        "Cüzdan (auth-gate)" to "$scheme://wallet",
        "Profil" to "$scheme://profile",
    )
    // Bir firmanın Studio'da kurduğu kampanyaya bağlayacağı deeplink'ler — keyName SDK'da
    // campaignData'ya resolve olur, source/campaign_id attribution'a girer, auth=required gate açar.
    val firmCampaignUrls = listOf(
        "Çeyiz — push (key + source)" to "$scheme://campaigns/ceyiz?keyName=$campaignKey&campaign_id=CMP-001&source=push",
        "Çeyiz — başvuruya (auth-gate)" to "$scheme://campaigns/ceyiz/apply?auth=required&source=email",
        "Sadece key (resolve → yönlen)" to "$scheme://campaigns?keyName=$campaignKey&source=sms",
    )
    val universalUrls = listOf(
        "Ürün A detay" to "https://$domain/products/a",
        "Çeyiz Hesabı" to "https://$domain/campaigns/ceyiz",
    )

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            DeepLinkBus.info("açılamadı: $url (${e.message})")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deeplink Log") },
                navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineSmall) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Kampanya Key (resolve testi)", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(value = campaignKey, onValueChange = { campaignKey = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (campaignKey == DeepLinkTestConfig.DEFAULT_CAMPAIGN_KEY) {
                            Text("⚠️ Canlı keyName gir.", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                        }
                    }
                }
            }

            item { Text("Custom Scheme — gerçek ekranlara yönlenir", style = MaterialTheme.typography.titleSmall) }
            items(customUrls) { (label, url) ->
                UrlRow(label, url, "Aç", { openUrl(url) }) { clipboard.setText(AnnotatedString(url)) }
            }

            item {
                Column {
                    Text("🏢 Firma kampanya deeplink'i", style = MaterialTheme.typography.titleSmall)
                    Text("Bir firmanın Studio'da kurduğu kampanyaya bağlayacağı link (keyName + source + auth).",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            items(firmCampaignUrls) { (label, url) ->
                UrlRow(label, url, "Aç", { openUrl(url) }) { clipboard.setText(AnnotatedString(url)) }
            }

            item { Text("Universal / App Link", style = MaterialTheme.typography.titleSmall) }
            items(universalUrls) { (label, url) ->
                UrlRow(label, url, "Simüle Et", { PaylisherDeepLinkManager.getInstance().handleUrl(url) }) { clipboard.setText(AnnotatedString(url)) }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Deferred: $deferredStatus", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Olay Günlüğü (${events.size})", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { DeepLinkBus.clear() }) { Text("Temizle") }
                }
            }
            if (events.isEmpty()) {
                item { Text("Henüz olay yok.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
            } else {
                items(events) { e -> EventRow(e) }
            }
        }
    }
}

@Composable
private fun UrlRow(label: String, url: String, action: String, onAction: () -> Unit, onCopy: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) { Text(action) }
                OutlinedButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) { Text("Kopyala") }
            }
        }
    }
}

@Composable
private fun EventRow(e: DeepLinkEvent) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(e.kind, style = MaterialTheme.typography.labelMedium)
                Text(e.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (e.url != "-") Text(e.url, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            if (e.destination != "-") Text("dest=${e.destination}  scheme=${e.scheme}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            if (e.campaignKey != null || e.jid != null || e.campaignTitle != null)
                Text("key=${e.campaignKey ?: "-"}  jid=${e.jid ?: "-"}  title=${e.campaignTitle ?: "-"}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
            e.note?.let { Text("· $it", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100)) }
        }
    }
}
