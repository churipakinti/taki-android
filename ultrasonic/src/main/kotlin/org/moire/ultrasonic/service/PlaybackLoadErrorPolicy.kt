/*
 * PlaybackLoadErrorPolicy.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

// Issue #35. A reverse proxy, a tunnel or a restarting Navidrome answers a stream request with 502/503/504
// (or 429) for a few seconds. Media3's default policy retried those exactly like any other error: three
// quick attempts (about 3 s), then a fatal ERROR_CODE_IO_BAD_HTTP_STATUS. The player then sat in STATE_IDLE
// for good - even when the server was back 0.3 s after the last attempt - and the error was reported as an
// unavailable track. A status that says "try again later" is now retried for a bounded time, in place, by
// Media3's own loader; the position, queue and session are untouched. Every other error keeps the default
// behaviour (at most three attempts, then the existing error handling, including #19's recovery).

/** HTTP statuses that mean "the server is busy or briefly unreachable", never "this track is wrong". */
internal val TRANSIENT_HTTP_STATUSES = setOf(429, 502, 503, 504)

/** Retries (after the first failure) granted to a transient HTTP status: 1 + 2 + 4 + 8 + 8 + 8 = 31 s at most. */
internal const val MAX_TRANSIENT_HTTP_RETRIES = 6

/** Retries every other error keeps: Media3's own default, unchanged. */
internal const val DEFAULT_LOAD_RETRIES = 3

private const val TRANSIENT_HTTP_BASE_DELAY_MS = 1_000L
private const val TRANSIENT_HTTP_MAX_DELAY_MS = 8_000L

/** A server's `Retry-After` is honoured, but never longer than this (and only as a number of seconds). */
private const val RETRY_AFTER_CAP_MS = 10_000L

/** Delay before retry number [errorCount] (1-based) of a transient HTTP status. Pure, for unit tests. */
internal fun transientHttpRetryDelayMs(errorCount: Int, retryAfterSeconds: Long?): Long {
    val backoff = (TRANSIENT_HTTP_BASE_DELAY_MS shl (errorCount - 1).coerceIn(0, 10))
        .coerceAtMost(TRANSIENT_HTTP_MAX_DELAY_MS)
    val requested = retryAfterSeconds?.takeIf { it > 0 }?.times(1_000L)?.coerceAtMost(RETRY_AFTER_CAP_MS)
    return maxOf(backoff, requested ?: 0L)
}

@OptIn(UnstableApi::class)
internal class PlaybackLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {

    // Media3 throws the load error once this many retries are spent, whatever the error is; the
    // per-error limit below is what keeps non-transient errors at the default three.
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = MAX_TRANSIENT_HTTP_RETRIES

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val http = loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException
        if (http != null && http.responseCode in TRANSIENT_HTTP_STATUSES) {
            if (loadErrorInfo.errorCount > MAX_TRANSIENT_HTTP_RETRIES) return C.TIME_UNSET
            return transientHttpRetryDelayMs(loadErrorInfo.errorCount, retryAfterSeconds(http))
        }
        if (loadErrorInfo.errorCount > DEFAULT_LOAD_RETRIES) return C.TIME_UNSET
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    private fun retryAfterSeconds(exception: HttpDataSource.InvalidResponseCodeException): Long? =
        exception.headerFields.entries
            .firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
            ?.value?.firstOrNull()?.trim()?.toLongOrNull()
}
