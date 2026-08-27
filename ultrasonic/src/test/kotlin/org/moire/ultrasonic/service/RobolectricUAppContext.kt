/*
 * RobolectricUAppContext.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.app.UApp

/**
 * Makes `UApp.applicationContext()` resolve inside a plain unit test.
 *
 * This module's Robolectric setup runs resource-less, so the manifest's `UApp` is never the test
 * [android.app.Application] and `UApp.instance` stays null. `UApp` is also `final` with an
 * `init {}` block that calls a `StrictMode` VM-policy method Robolectric doesn't implement, so it
 * can neither be subclassed nor plainly constructed. This installs a `UApp` stand-in (a Mockito
 * instance, so no constructor / no `init {}` runs) whose `applicationContext` is the Robolectric
 * application with one adjustment: its [Resources] degrade a missing string id to a synthetic key
 * instead of throwing [Resources.NotFoundException]. `object Settings`'s `<clinit>` resolves ~30
 * `R.string` preference keys eagerly and would otherwise be unloadable here (it also sits under
 * `Storage` / `FileUtil` / `toMediaItem`). Everything else delegates to the real context.
 *
 * Idempotent; safe to call from every `@Before`.
 */
internal object RobolectricUAppContext {

    fun install() {
        val instanceField = UApp::class.java.getDeclaredField("instance").apply { isAccessible = true }
        if (instanceField.get(null) != null) return

        val context = ResourceTolerantContext(ApplicationProvider.getApplicationContext())
        val shell = mock<UApp> {
            on { applicationContext } doReturn context
        }
        instanceField.set(null, shell)
    }

    private class ResourceTolerantContext(base: Context) : ContextWrapper(base) {
        private val tolerantResources: Resources = spy(base.resources) {
            val fallback = doAnswer { invocation ->
                try {
                    invocation.callRealMethod()
                } catch (_: Resources.NotFoundException) {
                    "res_${invocation.arguments[0]}"
                }
            }
            fallback.whenever(mock).getText(any())
            fallback.whenever(mock).getString(any())
        }

        override fun getResources(): Resources = tolerantResources

        override fun getApplicationContext(): Context = this
    }
}
