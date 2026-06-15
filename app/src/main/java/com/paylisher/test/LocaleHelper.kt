package com.paylisher.test

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * Uygulama dilini (Türkçe / İngilizce / cihaz dili) yöneten yardımcı.
 *
 *  - [SYSTEM] → cihaz diline göre çözümlenir (varsayılan; desteklenmeyen
 *    dillerde `values/` yani İngilizce'ye düşer).
 *  - "tr" / "en" → kullanıcının menüden manuel seçtiği dil.
 *
 * Seçim SharedPreferences'ta saklanır. [wrap] hem Application hem de Activity'nin
 * `attachBaseContext`'inde çağrılır; böylece tüm uygulama (UI + bildirim metinleri)
 * seçilen dilde çalışır. Dil değiştiğinde Activity `recreate()` edilir.
 */
object LocaleHelper {

    const val SYSTEM = "system"

    private const val PREFS = "paylisher_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun getPersistedLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    fun persistLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language).apply()
    }

    /** Saklanan dile göre [context]'i sarmalanmış bir context'e çevirir. */
    fun wrap(context: Context): Context =
        applyLanguage(context, getPersistedLanguage(context))

    private fun applyLanguage(context: Context, language: String): Context {
        if (language == SYSTEM) return context
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}

/** Compose [Context]'inden onu barındıran [Activity]'yi bulur (recreate için). */
fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
