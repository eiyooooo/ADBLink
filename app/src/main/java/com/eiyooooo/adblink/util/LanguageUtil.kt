package com.eiyooooo.adblink.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.eiyooooo.adblink.entity.Preferences
import java.util.Locale

object LanguageUtil {

    fun setLocale(context: Context): Context {
        return when (Preferences.appLanguage) {
            0 -> applySystemLocale(context)
            1 -> applyLocale(context, Locale.ENGLISH)
            2 -> applyLocale(context, Locale("zh", "CN"))
            else -> applySystemLocale(context)
        }
    }

    private fun applySystemLocale(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(Configuration(context.resources.configuration))
        } else {
            context
        }
    }

    private fun applyLocale(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
