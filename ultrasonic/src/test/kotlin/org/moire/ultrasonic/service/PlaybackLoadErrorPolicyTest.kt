/*
 * PlaybackLoadErrorPolicyTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * #35. A status that means "try again later" is retried by Media3's own loader for a bounded time; every
 * other error behaves exactly as with the stock policy (at most three attempts). No clock, no player,
 * no network: the policy is a pure function of the failed load.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackLoadErrorPolicyTest {

    private val policy = PlaybackLoadErrorPolicy()
    private val stock = DefaultLoadErrorHandlingPolicy()

    private fun info(exception: IOException, errorCount: Int): LoadErrorHandlingPolicy.LoadErrorInfo =
        LoadErrorHandlingPolicy.LoadErrorInfo(
            LoadEventInfo(1L, DataSpec(Uri.parse("http://x/")), Uri.parse("http://x/"), emptyMap(), 0L, 0L, 0L),
            MediaLoadData(C.DATA_TYPE_MEDIA),
            exception,
            errorCount
        )

    private fun http(status: Int, headers: Map<String, List<String>> = emptyMap()) =
        HttpDataSource.InvalidResponseCodeException(
            status, "status", null, headers, DataSpec(Uri.parse("http://x/")), ByteArray(0)
        )

    private fun delays(exception: IOException, upTo: Int = 8) =
        (1..upTo).map { policy.getRetryDelayMsFor(info(exception, it)) }

    private val unset = C.TIME_UNSET

    @Test
    fun `a transient HTTP status is retried with a bounded, growing backoff`() {
        for (status in listOf(429, 502, 503, 504)) {
            assertEquals(
                "status $status",
                listOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L, 8_000L, unset, unset),
                delays(http(status))
            )
        }
        assertEquals("the whole budget is about half a minute", 31_000L, delays(http(503)).filter { it != unset }.sum())
    }

    @Test
    fun `the loader is allowed to keep retrying that long and no longer`() {
        assertEquals(MAX_TRANSIENT_HTTP_RETRIES, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
        assertEquals(MAX_TRANSIENT_HTTP_RETRIES, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MANIFEST))
    }

    @Test
    fun `a server's Retry-After raises the delay, within a cap, and bad values are ignored`() {
        fun first(vararg value: String) = policy.getRetryDelayMsFor(
            info(http(429, if (value.isEmpty()) emptyMap() else mapOf("retry-after" to value.toList())), 1)
        )

        assertEquals("no header: plain backoff", 1_000L, first())
        assertEquals("3 s asked for", 3_000L, first("3"))
        assertEquals("surrounding spaces", 3_000L, first(" 3 "))
        assertEquals("never longer than the cap", 10_000L, first("600"))
        assertEquals("zero", 1_000L, first("0"))
        assertEquals("negative", 1_000L, first("-5"))
        assertEquals("an HTTP date is not honoured", 1_000L, first("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals("a smaller value never shortens the backoff", 4_000L, policy.getRetryDelayMsFor(
            info(http(503, mapOf("Retry-After" to listOf("1"))), 3)
        ))
    }

    @Test
    fun `permanent HTTP statuses keep the stock behaviour - three attempts, no more`() {
        for (status in listOf(400, 401, 403, 404, 410, 416, 500)) {
            val error = http(status)
            for (count in 1..3) {
                assertEquals("status $status attempt $count", stock.getRetryDelayMsFor(info(error, count)), policy.getRetryDelayMsFor(info(error, count)))
            }
            assertEquals("status $status stops after three", unset, policy.getRetryDelayMsFor(info(error, 4)))
        }
    }

    @Test
    fun `connection failures and timeouts keep the stock behaviour so recovery 19 still applies`() {
        for (error in listOf<IOException>(IOException("reset"), SocketTimeoutException("read timed out"))) {
            for (count in 1..3) {
                assertEquals(stock.getRetryDelayMsFor(info(error, count)), policy.getRetryDelayMsFor(info(error, count)))
            }
            assertEquals(unset, policy.getRetryDelayMsFor(info(error, 4)))
        }
    }

    @Test
    fun `errors that can never succeed are still not retried`() {
        val parser = ParserException.createForMalformedContainer("bad", null)
        assertEquals(unset, policy.getRetryDelayMsFor(info(parser, 1)))
        assertEquals(unset, policy.getRetryDelayMsFor(info(java.io.FileNotFoundException("gone"), 1)))
    }

    @Test
    fun `the backoff is pure and capped`() {
        assertEquals(1_000L, transientHttpRetryDelayMs(1, null))
        assertEquals(8_000L, transientHttpRetryDelayMs(4, null))
        assertEquals(8_000L, transientHttpRetryDelayMs(40, null))
        assertEquals(8_000L, transientHttpRetryDelayMs(Int.MAX_VALUE, null))
        assertTrue((1..10).map { transientHttpRetryDelayMs(it, null) }.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `the local player is built with this policy`() {
        // The policy only helps if the media source factory really uses it (PlaybackService needs a device).
        val source = File("src/main/kotlin/org/moire/ultrasonic/service/PlaybackService.kt").readText()
        assertTrue(source.contains("DefaultMediaSourceFactory(cacheDataSourceFactory)"))
        assertTrue(source.contains(".setLoadErrorHandlingPolicy(PlaybackLoadErrorPolicy())"))
    }
}
