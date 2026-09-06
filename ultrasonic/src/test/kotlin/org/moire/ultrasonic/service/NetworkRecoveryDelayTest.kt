/*
 * NetworkRecoveryDelayTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract for [networkRecoveryDelayMs] - the issue #19 decision on whether a playback error is
 * a transient network problem worth re-preparing in place, and with how much backoff.
 *
 * ```
 * network error + user wants playback + budget left -> retry, backoff 2s / 4s / 8s
 * budget spent                                       -> null (surface the error, stop)
 * user paused                                        -> null
 * a permanently unavailable track                    -> null
 * a non-network error (e.g. decoder)                 -> null
 * ```
 */
class NetworkRecoveryDelayTest {

    private val networkFailed = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    private val networkTimeout = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

    @Test
    fun `network error retries with exponential backoff until the budget is spent`() {
        assertEquals(2_000L, delay(networkFailed, attemptsSoFar = 0))
        assertEquals(4_000L, delay(networkFailed, attemptsSoFar = 1))
        assertEquals(8_000L, delay(networkTimeout, attemptsSoFar = 2))
        assertNull(delay(networkFailed, attemptsSoFar = 3))
        assertNull(delay(networkFailed, attemptsSoFar = 4))
    }

    @Test
    fun `no retry when the user is not asking for playback`() {
        assertNull(delay(networkFailed, attemptsSoFar = 0, playWhenReady = false))
    }

    @Test
    fun `no retry for a permanently unavailable track`() {
        assertNull(delay(networkFailed, attemptsSoFar = 0, trackUnavailable = true))
    }

    @Test
    fun `no retry for a non-network error`() {
        assertNull(
            networkRecoveryDelayMs(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
                trackUnavailable = false,
                playWhenReady = true,
                attemptsSoFar = 0
            )
        )
        assertNull(
            networkRecoveryDelayMs(
                errorCode = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                trackUnavailable = false,
                playWhenReady = true,
                attemptsSoFar = 0
            )
        )
    }

    private fun delay(
        errorCode: Int,
        attemptsSoFar: Int,
        playWhenReady: Boolean = true,
        trackUnavailable: Boolean = false
    ): Long? = networkRecoveryDelayMs(errorCode, trackUnavailable, playWhenReady, attemptsSoFar)
}
