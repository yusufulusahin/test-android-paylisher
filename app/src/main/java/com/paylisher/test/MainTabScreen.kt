package com.paylisher.test

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Giriş sonrası ana ekran: bottom nav + 4 sekme. */
@Composable
fun MainTabScreen(userId: String, onLogout: () -> Unit) {
    val selectedTab by DeepLinkBus.selectedTab.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabItem("🏠", "Ana Sayfa", selectedTab == AppTab.HOME) { DeepLinkBus.selectTab(AppTab.HOME) }
                tabItem("🛍️", "Ürünler", selectedTab == AppTab.PRODUCTS) { DeepLinkBus.selectTab(AppTab.PRODUCTS) }
                tabItem("💳", "Cüzdan", selectedTab == AppTab.WALLET) { DeepLinkBus.selectTab(AppTab.WALLET) }
                tabItem("👤", "Profil", selectedTab == AppTab.PROFILE) { DeepLinkBus.selectTab(AppTab.PROFILE) }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                AppTab.HOME -> HomeTabContent()
                AppTab.PRODUCTS -> ProductsTab()
                AppTab.WALLET -> WalletTab()
                AppTab.PROFILE -> ProfileTab(userId, onLogout)
            }
        }
    }
}

@Composable
private fun RowScope.tabItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Text(icon) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}

// ───── Ürünler (iç içe: liste → detay → içerik) ─────

@Composable
private fun ProductsTab() {
    val path by DeepLinkBus.productsPath.collectAsState()
    when (val top = path.lastOrNull()) {
        null -> ProductListScreen()
        is ProductRoute.Detail -> {
            BackHandler { DeepLinkBus.popProduct() }
            ProductDetailScreen(top.id)
        }
        is ProductRoute.Content -> {
            BackHandler { DeepLinkBus.popProduct() }
            ProductContentScreen(top.id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductListScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Ürünler") }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Product.all) { product ->
                Card(onClick = { DeepLinkBus.pushProduct(ProductRoute.Detail(product.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                        Text(product.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text(product.price, style = MaterialTheme.typography.labelMedium, color = Color(0xFF1565C0))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDetailScreen(id: String) {
    val product = Product.find(id)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(product.name) },
            navigationIcon = { IconButton(onClick = { DeepLinkBus.popProduct() }) { Text("‹", style = MaterialTheme.typography.headlineSmall) } }
        )
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(product.name, style = MaterialTheme.typography.headlineMedium)
            Text(product.summary, color = MaterialTheme.colorScheme.outline)
            Text(product.price, style = MaterialTheme.typography.titleLarge, color = Color(0xFF1565C0))
            Text("Bu ${product.name} için örnek detay ekranıdır.\nDeeplink: paylishertest://products/$id", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { DeepLinkBus.pushProduct(ProductRoute.Content(id)) }, modifier = Modifier.fillMaxWidth()) {
                Text("İçeriği Gör (en iç ekran)")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductContentScreen(id: String) {
    val product = Product.find(id)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("İçerik") },
            navigationIcon = { IconButton(onClick = { DeepLinkBus.popProduct() }) { Text("‹", style = MaterialTheme.typography.headlineSmall) } }
        )
    }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("${product.name} — İçerik", style = MaterialTheme.typography.titleLarge) }
            item {
                Text("Bu, ürünler sekmesindeki 3. seviye (en iç) ekrandır.\n\nDeeplink: paylishertest://products/$id/content\n\nUygulama kapalıyken bu linke tıklanırsa, giriş sonrası doğrudan bu ekrana kadar yönlenir (Ürünler → ${product.name} → İçerik).",
                    style = MaterialTheme.typography.bodyMedium)
            }
            items((1..3).toList()) { i ->
                Text("İçerik bölümü $i: ${product.name} hakkında örnek metin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ───── Cüzdan (auth-gate) ─────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletTab() {
    // Tab'a yalnızca app'e login'liyken ulaşılır → içerik DOĞRUDAN gösterilir (re-login yok).
    // Auth-gate login YOKKEN (cold-start) devreye girer; login olunca DeepLinkBus.setAuthenticated(true).
    Scaffold(topBar = { TopAppBar(title = { Text("Cüzdan") }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Bakiye", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Text("₺ 4.250,00", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            item { Text("Son İşlemler", style = MaterialTheme.typography.titleSmall) }
            items(listOf("Market — ₺120", "Maaş + ₺3.000", "Fatura — ₺340")) { tx ->
                Card(Modifier.fillMaxWidth()) { Text(tx, Modifier.padding(14.dp)) }
            }
        }
    }
}

// ───── Profil ─────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTab(userId: String, onLogout: () -> Unit) {
    var showLog by remember { mutableStateOf(false) }
    if (showLog) {
        BackHandler { showLog = false }
        DeepLinkTestScreen(onBack = { showLog = false })
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceId = remember { DeviceIdHelper.get(context) }
    val deferred by DeepLinkBus.deferredStatus.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profil") }, actions = { LanguageMenu() }) }
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("userId: $userId", style = MaterialTheme.typography.bodyMedium)
                        Text("deviceID: $deviceId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            item {
                Card(onClick = { showLog = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("🔗 Deeplink Log & Manuel Test", style = MaterialTheme.typography.titleSmall)
                        Text("Deferred: $deferred", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            item {
                Button(
                    onClick = { DeepLinkBus.setAuthenticated(false); onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(androidx.compose.ui.res.stringResource(R.string.logout_button)) }
            }
        }
    }
}
