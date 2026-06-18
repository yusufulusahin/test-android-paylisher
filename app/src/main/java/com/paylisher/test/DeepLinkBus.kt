package com.paylisher.test

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
enum class AppTab { HOME, PRODUCTS, CAMPAIGNS, WALLET, PROFILE }

sealed class ProductRoute {
    data class Detail(val id: String) : ProductRoute()
    data class Content(val id: String) : ProductRoute()
}

// Kampanya sekmesinin iç içe yolları: liste → detay → başvuru (en iç, auth-gate'li).
sealed class CampaignRoute {
    data class Detail(val slug: String) : CampaignRoute()
    data class Apply(val slug: String) : CampaignRoute()
}

/**
 * Studio/keyName üzerinden SDK'nın çözdüğü kampanya verisi (firma tarafı).
 * Bir firma Paylisher'da kampanya kurup `keyName` alır; deeplink'inde `?keyName=...`
 * taşır → SDK `campaignData`'yı resolve eder → burada UI'ya köprülenir.
 */
data class ResolvedCampaignInfo(
    val title: String?,
    val keyName: String?,
    val targetUrl: String?,   // androidUrl ?: scheme (firmanın bağladığı hedef)
    val webUrl: String?,
    val adId: String?,
)

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

    private val _campaignsPath = MutableStateFlow<List<CampaignRoute>>(emptyList())
    val campaignsPath: StateFlow<List<CampaignRoute>> = _campaignsPath

    // keyName'den çözülen son kampanya (Kampanyalar sekmesinde banner olarak gösterilir).
    private val _resolvedCampaign = MutableStateFlow<ResolvedCampaignInfo?>(null)
    val resolvedCampaign: StateFlow<ResolvedCampaignInfo?> = _resolvedCampaign

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
        captureResolved(dl)
        logEvent(dl, if (requiresAuth) "🔐 auth" else "✅ received", if (requiresAuth) "auth gerekli" else null)
        if (!requiresAuth) navigate(dl)
    }

    /** keyName'den çözülmüş kampanya varsa (campaignData) UI'ya köprüle. */
    private fun captureResolved(dl: PaylisherDeepLink) {
        val cd = dl.campaignData ?: return
        _resolvedCampaign.value = ResolvedCampaignInfo(
            title = cd.title,
            keyName = cd.keyName ?: dl.campaignKeyName,
            targetUrl = cd.androidUrl ?: cd.scheme,
            webUrl = cd.webUrl,
            adId = cd.adId,
        )
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
        captureResolved(dl)
        setDeferredStatus("🎯 match: ${dl.url}")
        logEvent(dl, "🎯 deferred", "deferred match")
        navigate(dl)
    }

    // ───── Navigasyon ─────

    // SDK zaten normalize edilmiş `pathSegments` veriyor → URL'i TEKRAR PARSE ETMEYE GEREK YOK.
    // ["products","a","content"]  (custom scheme + universal link, iOS + Android: hepsi aynı)
    fun navigate(dl: PaylisherDeepLink) {
        val segs = dl.pathSegments
        when (segs.firstOrNull()?.lowercase()) {
            null, "home" -> _selectedTab.value = AppTab.HOME
            "products", "product" -> {
                val id = segs.getOrNull(1) ?: dl.parameters["id"]
                _selectedTab.value = AppTab.PRODUCTS
                _productsPath.value = when {
                    id == null -> emptyList()
                    segs.getOrNull(2)?.lowercase() == "content" ->
                        listOf(ProductRoute.Detail(id), ProductRoute.Content(id))
                    else -> listOf(ProductRoute.Detail(id))
                }
            }
            "campaigns", "campaign" -> {
                // Firma deeplink'i: campaigns/<slug>[/apply]  veya  campaigns?slug=<slug>
                val slug = segs.getOrNull(1) ?: dl.parameters["slug"]
                _selectedTab.value = AppTab.CAMPAIGNS
                _campaignsPath.value = when {
                    slug == null -> emptyList()
                    segs.getOrNull(2)?.lowercase() == "apply" ->
                        listOf(CampaignRoute.Detail(slug), CampaignRoute.Apply(slug))
                    else -> listOf(CampaignRoute.Detail(slug))
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

    fun pushCampaign(route: CampaignRoute) { _campaignsPath.update { it + route } }
    fun popCampaign() { _campaignsPath.update { if (it.isEmpty()) it else it.dropLast(1) } }
    fun resetCampaigns() { _campaignsPath.value = emptyList() }

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

// Kampanya test verisi — Vakıf Katılım "Çeyiz Hesabı" senaryosundan esinli.
// keyName: bir firmanın Paylisher Studio'da kampanyayı kurunca alacağı anahtar.
data class Campaign(
    val slug: String,
    val emoji: String,
    val title: String,
    val tagline: String,
    val summary: String,
    val highlights: List<String>,
    val keyName: String,
    val accent: Long,
) {
    companion object {
        val all = listOf(
            Campaign(
                slug = "ceyiz", emoji = "💍", title = "Çeyiz Hesabı",
                tagline = "Devlet katkılı evlilik birikimi",
                summary = "Düzenli biriktir, evlenince devlet katkısını al. Katılım esaslı (faizsiz); birikimin kâr payı ile değerlenir.",
                highlights = listOf(
                    "Devlet katkısı: birikimin %20'si (üst sınırlı)",
                    "Katılım hesabı — faizsiz, kâr payı esaslı",
                    "18–27 yaş, düzenli aylık ödeme planı",
                    "Min. birikim süresi sonunda evlilikte ödeme",
                ),
                keyName = "CEYIZ2026", accent = 0xFFAD1457,
            ),
            Campaign(
                slug = "konut", emoji = "🏠", title = "Konut Hesabı",
                tagline = "Devlet destekli ev birikimi",
                summary = "İlk evin için düzenli biriktir; devlet katkısıyla peşinatını büyüt.",
                highlights = listOf(
                    "Devlet katkısı: %20 (üst sınırlı)",
                    "Katılım esaslı, faizsiz birikim",
                    "Konut alımında kullanım önceliği",
                ),
                keyName = "KONUT2026", accent = 0xFF1565C0,
            ),
            Campaign(
                slug = "altin", emoji = "🪙", title = "Altın Birikim Hesabı",
                tagline = "Gram altın ile biriktir",
                summary = "Birikimini gram altına çevir; dalgalanmaya karşı değer biriktir.",
                highlights = listOf(
                    "Fiziki/sanal gram altın olarak birikim",
                    "Dilediğin an TL'ye dönüş",
                    "Aylık otomatik altın alım talimatı",
                ),
                keyName = "ALTIN2026", accent = 0xFFF9A825,
            ),
            Campaign(
                slug = "cocuk", emoji = "🧸", title = "Geleceğim Çocuk Hesabı",
                tagline = "Çocuğun için erken başla",
                summary = "Çocuğun 18 yaşına geldiğinde kullanabileceği uzun vadeli birikim.",
                highlights = listOf(
                    "Uzun vadeli katılım hesabı",
                    "Düzenli küçük ödemelerle büyüyen birikim",
                    "18 yaşında hak sahibine devir",
                ),
                keyName = "COCUK2026", accent = 0xFF2E7D32,
            ),
        )

        fun find(slug: String) = all.firstOrNull { it.slug == slug }
            ?: Campaign(slug, "🎁", "Kampanya ${slug.uppercase()}", "Deeplink ile gelen kampanya",
                "Bu kampanya bir deeplink ile açıldı.", listOf("Detaylar yakında"), slug.uppercase(), 0xFF6A1B9A)
    }
}
