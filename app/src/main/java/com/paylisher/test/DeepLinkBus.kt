package com.paylisher.test

import android.net.Uri
import com.paylisher.android.PaylisherDeepLink
import com.paylisher.android.PaylisherDeepLinkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Test sabitleri
object DeepLinkTestConfig {
    const val SCHEME = "paylishertest"
    const val DOMAIN = "link.paylisher.com"
    // TODO: Canlı kampanya keyName'i ile değiştir (debug ekranındaki alandan da girilir).
    const val DEFAULT_CAMPAIGN_KEY = "REPLACE_WITH_LIVE_KEY"
}

// Navigasyon modeli (iOS ile parite)
enum class AppTab { HOME, PRODUCTS, WALLET, PROFILE }

sealed class ProductRoute {
    data class Detail(val id: String) : ProductRoute()
    data class Content(val id: String) : ProductRoute()
}

/** Debug olay kaydı. */
data class DeepLinkEvent(
    val time: String,
    val kind: String,
    val url: String,
    val destination: String,
    val scheme: String,
    val jid: String?,
    val campaignKey: String?,
    val campaignTitle: String?,
    val note: String?,
)

/**
 * Hem SDK handler köprüsü hem de uygulamanın MERKEZİ navigasyon durumu (StateFlow).
 * MainActivity'nin handler callback'leri buraya yazar; Compose UI `collectAsState` ile dinler.
 * Gelen deeplink URL'ini kendimiz parse edip (host + path) sekme + iç içe ürün yoluna çeviririz.
 */
object DeepLinkBus {
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Navigasyon durumu
    private val _selectedTab = MutableStateFlow(AppTab.HOME)
    val selectedTab: StateFlow<AppTab> = _selectedTab

    private val _productsPath = MutableStateFlow<List<ProductRoute>>(emptyList())
    val productsPath: StateFlow<List<ProductRoute>> = _productsPath

    // App login durumu — wallet auth-gate buna bakar.
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated
    // Login yoksa SDK completion'ı burada bekler; login olunca tamamlanır (cold-start).
    @Volatile private var pendingAuthCompletion: ((Boolean) -> Unit)? = null

    // Debug
    private val _events = MutableStateFlow<List<DeepLinkEvent>>(emptyList())
    val events: StateFlow<List<DeepLinkEvent>> = _events
    private val _deferredStatus = MutableStateFlow("henüz kontrol edilmedi")
    val deferredStatus: StateFlow<String> = _deferredStatus

    // ───── SDK handler köprüsü ─────

    fun onReceive(dl: PaylisherDeepLink, requiresAuth: Boolean) {
        logEvent(dl, if (requiresAuth) "🔐 auth" else "✅ received", if (requiresAuth) "auth gerekli" else null)
        if (!requiresAuth) navigate(dl.url)
    }

    fun onAuthRequired(dl: PaylisherDeepLink, completion: (Boolean) -> Unit) {
        if (_isAuthenticated.value) {
            completion(true)                       // app'e login'li → direkt geç (re-login yok)
        } else {
            pendingAuthCompletion = completion     // login yok (cold-start) → login'i bekle
        }
    }

    fun failed(url: String, error: Exception?) {
        add(DeepLinkEvent(sdf.format(Date()), "❌ failed", url, "-", "-", null, null, null, error?.message))
    }

    fun deferredMatch(dl: PaylisherDeepLink) {
        setDeferredStatus("🎯 match: ${dl.url}")
        logEvent(dl, "🎯 deferred", "deferred match")
        navigate(dl.url)
    }

    // ───── Navigasyon ─────

    fun navigate(urlString: String) {
        val uri = try { Uri.parse(urlString) } catch (e: Exception) { return }
        val host = (uri.host ?: "").lowercase()
        val segs = uri.pathSegments ?: emptyList()
        when (host) {
            "home", "" -> _selectedTab.value = AppTab.HOME
            "products", "product" -> {
                val id = segs.firstOrNull() ?: uri.getQueryParameter("id")
                _selectedTab.value = AppTab.PRODUCTS
                _productsPath.value = when {
                    id == null -> emptyList()
                    segs.size >= 2 && segs[1].lowercase() == "content" ->
                        listOf(ProductRoute.Detail(id), ProductRoute.Content(id))
                    else -> listOf(ProductRoute.Detail(id))
                }
            }
            "wallet" -> _selectedTab.value = AppTab.WALLET
            "profile" -> _selectedTab.value = AppTab.PROFILE
            else -> _selectedTab.value = AppTab.HOME
        }
    }

    fun selectTab(tab: AppTab) { _selectedTab.value = tab }

    fun pushProduct(route: ProductRoute) { _productsPath.update { it + route } }
    fun popProduct() { _productsPath.update { if (it.isEmpty()) it else it.dropLast(1) } }
    fun resetProducts() { _productsPath.value = emptyList() }

    // ───── Auth (app login) ─────

    /** App login/logout'ta çağrılır. Login olunca, login'i bekleyen (cold-start) auth-gate
     *  deeplink'i varsa onu tamamlar → SDK pending deeplink'i tamamlar → wallet'e yönlenir. */
    fun setAuthenticated(value: Boolean) {
        _isAuthenticated.value = value
        if (value) {
            pendingAuthCompletion?.let { c ->
                pendingAuthCompletion = null
                c(true)
                info("Login sonrası bekleyen auth-gate deeplink tamamlandı")
            }
        }
    }

    // ───── Log ─────

    fun setDeferredStatus(s: String) { _deferredStatus.value = s }
    fun clear() { _events.value = emptyList() }
    fun info(note: String) {
        add(DeepLinkEvent(sdf.format(Date()), "ℹ️ info", "-", "-", "-", null, null, null, note))
    }

    private fun logEvent(dl: PaylisherDeepLink, kind: String, note: String?) {
        add(DeepLinkEvent(sdf.format(Date()), kind, dl.url, dl.destination, dl.scheme, dl.jid,
            dl.campaignKeyName, dl.campaignData?.title, note))
    }

    private fun add(e: DeepLinkEvent) { _events.update { (listOf(e) + it).take(100) } }
}

// Ürün test verisi (iOS ile parite)
data class Product(val id: String, val name: String, val summary: String, val price: String) {
    companion object {
        val all = listOf(
            Product("a", "Ürün A", "Kablosuz kulaklık", "₺1.299"),
            Product("b", "Ürün B", "Akıllı saat", "₺2.499"),
            Product("c", "Ürün C", "Bluetooth hoparlör", "₺899"),
        )
        fun find(id: String) = all.firstOrNull { it.id == id }
            ?: Product(id, "Ürün ${id.uppercase()}", "Deeplink ile gelen ürün", "—")
    }
}
