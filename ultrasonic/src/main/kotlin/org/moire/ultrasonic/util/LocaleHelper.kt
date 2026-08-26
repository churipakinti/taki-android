/*
 * LocaleHelper.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * Simple Helper class to "wrap" a context with a new locale.
 */
class LocaleHelper(base: Context?) : ContextWrapper(base) {
    companion object {
        fun wrap(ctx: Context?, language: String): ContextWrapper {
            var context = ctx
            if (context != null && language != "") {
                val config = context.resources.configuration
                val locale = Locale.forLanguageTag(language)
                Locale.setDefault(locale)
                setSystemLocale(config, locale)

                config.setLayoutDirection(locale)
                context = context.createConfigurationContext(config)
            }
            return LocaleHelper(context)
        }

        // App Bundle language splitting is explicitly disabled
        // (bundle.language.enableSplit = false in ultrasonic/build.gradle), so all language
        // resources always stay packaged and this runtime locale switch cannot be affected by
        // split delivery. The lint check does not recognize the nested DSL form and flags this
        // as a false positive.
        @SuppressLint("AppBundleLocaleChanges")
        private fun setSystemLocale(config: Configuration, locale: Locale?) {
            config.setLocale(locale)
        }
    }
}
