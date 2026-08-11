/*
 * PerfMetricsInterceptor.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * Baseline network instrumentation (Fase 0). Logs method, path (never the full URL -- query
 * params carry the session's `u`/`s`/`t` auth params), duration and response size for every
 * Subsonic API call. Only ever attached to the debug-build OkHttpClient, see
 * `MusicServiceModule.kt`.
 */
class PerfMetricsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startToken = SystemClock.elapsedRealtime()
        val response = chain.proceed(request)
        val elapsedMs = SystemClock.elapsedRealtime() - startToken
        val bytes = response.body.contentLength()

        Timber.tag(TAG).d(
            "NET %s %s %dms %db",
            request.method,
            request.url.encodedPath,
            elapsedMs,
            bytes
        )

        return response
    }

    private companion object {
        const val TAG = "PerfMetrics"
    }
}
