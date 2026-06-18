package com.paylisher.test

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.paylisher.Paylisher

/**
 * Kampanyalar sekmesi (iç içe: liste → detay → başvuru).
 * Bir firmanın deeplink ile bağlayacağı iniş hedefi. Çeyiz Hesabı senaryosu flagship.
 */
@Composable
fun CampaignsTab(userId: String) {
    val path by DeepLinkBus.campaignsPath.collectAsState()
    when (val top = path.lastOrNull()) {
        null -> CampaignListScreen()
        is CampaignRoute.Detail -> {
            BackHandler { DeepLinkBus.popCampaign() }
            CampaignDetailScreen(top.slug)
        }
        is CampaignRoute.Apply -> {
            BackHandler { DeepLinkBus.popCampaign() }
            CampaignApplyScreen(top.slug, userId)
        }
    }
}

// ───── Liste ─────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampaignListScreen() {
    val resolved by DeepLinkBus.resolvedCampaign.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Kampanyalar") }) }) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            resolved?.let { r ->
                item { ResolvedCampaignBanner(r) }
            }
            item {
                Text(
                    "Bu sekme, bir firmanın kurduğu kampanyaya bağlayacağı deeplink iniş hedefidir. " +
                        "Örn. paylishertest://campaigns/ceyiz",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline
                )
            }
            items(Campaign.all) { c ->
                Card(
                    onClick = { DeepLinkBus.pushCampaign(CampaignRoute.Detail(c.slug)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${c.emoji}  ${c.title}", style = MaterialTheme.typography.titleMedium, color = Color(c.accent))
                        Text(c.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolvedCampaignBanner(r: ResolvedCampaignInfo) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
        Column(Modifier.padding(12.dp)) {
            Text("🎯 Studio'dan çözülen kampanya", style = MaterialTheme.typography.labelMedium, color = Color(0xFF1565C0))
            Text(r.title ?: "—", style = MaterialTheme.typography.titleSmall)
            r.keyName?.let { Text("key=$it", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline) }
            r.targetUrl?.let { Text("hedef=$it", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline) }
            r.adId?.let { Text("adId=$it", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline) }
        }
    }
}

// ───── Detay ─────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampaignDetailScreen(slug: String) {
    val c = Campaign.find(slug)
    // Kampanya görüntüleme event'i (SDK analytics testi)
    LaunchedEffect(slug) {
        Paylisher.capture("campaign_view", properties = mapOf("campaign" to slug, "keyName" to c.keyName))
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(c.title) },
            navigationIcon = { IconButton(onClick = { DeepLinkBus.popCampaign() }) { Text("‹", style = MaterialTheme.typography.headlineSmall) } }
        )
    }) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("${c.emoji}  ${c.title}", style = MaterialTheme.typography.headlineSmall, color = Color(c.accent))
                Text(c.tagline, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
            }
            item { Text(c.summary, style = MaterialTheme.typography.bodyMedium) }
            item { Text("Öne çıkanlar", style = MaterialTheme.typography.titleSmall) }
            items(c.highlights) { h ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", color = Color(c.accent))
                    Text(h, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Button(
                    onClick = { DeepLinkBus.pushCampaign(CampaignRoute.Apply(slug)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(c.accent))
                ) { Text("Hemen Başvur") }
            }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Firma bu kampanyayı deeplink ile şöyle bağlar:", style = MaterialTheme.typography.labelMedium)
                        Text("paylishertest://campaigns/$slug?keyName=${c.keyName}&source=push",
                            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                        Text("Başvuru (auth-gate): paylishertest://campaigns/$slug/apply?auth=required",
                            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

// ───── Başvuru (en iç, auth-gate'li) ─────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampaignApplyScreen(slug: String, userId: String) {
    val c = Campaign.find(slug)
    var submitted by remember(slug) { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Başvuru") },
            navigationIcon = { IconButton(onClick = { DeepLinkBus.popCampaign() }) { Text("‹", style = MaterialTheme.typography.headlineSmall) } }
        )
    }) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("${c.emoji}  ${c.title} — Başvuru", style = MaterialTheme.typography.titleLarge, color = Color(c.accent))
            }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🔐 Bu ekran auth-gate'li", style = MaterialTheme.typography.labelMedium, color = Color(0xFFE65100))
                        Text(
                            "Kapalı uygulamaya gelen paylishertest://campaigns/$slug/apply?auth=required " +
                                "linki önce giriş ister, sonra doğrudan bu ekrana yönlenir.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            if (submitted) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                        Column(Modifier.padding(14.dp)) {
                            Text("✅ Başvurun alındı", style = MaterialTheme.typography.titleSmall)
                            Text("Müşteri No: $userId · Kampanya: ${c.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            } else {
                item {
                    Text("Müşteri No: $userId", style = MaterialTheme.typography.bodyMedium)
                    Text("“${c.title}” için başvurunu tamamla. Onayınca SDK'ya campaign_apply event'i gider.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                item {
                    Button(
                        onClick = {
                            Paylisher.capture("campaign_apply", properties = mapOf(
                                "campaign" to slug, "keyName" to c.keyName, "userId" to userId
                            ))
                            submitted = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(c.accent))
                    ) { Text("Başvuruyu Gönder") }
                }
            }
        }
    }
}
