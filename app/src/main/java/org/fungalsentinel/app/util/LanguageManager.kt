package org.fungalsentinel.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** In-app language switcher (system / en / zh / es / ny), minSdk-24 safe. */
object LanguageManager {
    const val SYSTEM = "system"
    private const val PREF_NAME = "app_language_prefs"
    private const val KEY_LANG = "language_code"

    fun get(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_LANG, SYSTEM) ?: SYSTEM

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LANG, code).apply()
    }

    fun wrap(context: Context): Context {
        val code = get(context)
        if (code == SYSTEM) return context
        val locale = Locale.forLanguageTag(code)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}