/*
 * PlaybackErrorRecovery.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/** How [MediaPlayerManager.onPlayerError] treats a Media3 [PlaybackException] (issues #18, #19, #30). */
internal enum class PlaybackErrorKind {
    /** Wi-Fi/mobile handoff or brief drop: retried in place (#19); only surfaced once the budget is spent. */
    TRANSIENT_NETWORK,

    /** The id no longer resolves / the server did not return audio: specialised message + album cache invalidation (#18). */
    TRACK_UNAVAILABLE,

    /** Anything else (decoder, unexpected source failure): generic "couldn't play" message. */
    GENERIC
}

internal fun classifyPlaybackError(errorCode: Int): PlaybackErrorKind = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> PlaybackErrorKind.TRACK_UNAVAILABLE

    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackErrorKind.TRANSIENT_NETWORK

    else -> PlaybackErrorKind.GENERIC
}

/**
 * Issue #30. After a non-recoverable source error Media3 sits in `STATE_IDLE` with
 * `playerError != null` (there is no `STATE_ERROR`; `playWhenReady` is kept). A manual
 * Next/Previous/queue tap (a `SEEK` transition) then only moves the current index: nothing is
 * loaded until `prepare()` is called again. A healthy player has no `playerError`, so a normal
 * Next never matches and gets no extra command.
 */
internal fun shouldPrepareAfterTransition(
    transitionReason: Int,
    hasPlayerError: Boolean,
    playbackState: Int
): Boolean = hasPlayerError &&
    playbackState == Player.STATE_IDLE &&
    transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK

/**
 * Lets one failed attempt produce one user-visible message even if Media3 raises several adjacent
 * callbacks. Cleared as soon as the player leaves the failed state or changes item, so the next
 * independent failure (including a retry of the same item) is reported again.
 */
internal class PlaybackErrorReportGate {
    private var reportedFor: String? = null
    private var reported = false

    fun shouldReport(mediaId: String?): Boolean {
        if (reported && reportedFor == mediaId) return false
        reported = true
        reportedFor = mediaId
        return true
    }

    fun clear() {
        reported = false
        reportedFor = null
    }
}
