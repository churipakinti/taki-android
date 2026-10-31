/*
 * PlaybackTaskRemovalPolicy.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.common.Player

/**
 * Issue #36 follow-up: what to do when the user removes Taki from the Recents screen.
 *
 * Swiping the app away is not an order to stop the music. Playback continues whenever the user still
 * wants it: `playWhenReady` is true and there is something that can play or be recovered.
 *
 * - `READY` (including a transient audio-focus suppression, which keeps `playWhenReady`) and
 *   `BUFFERING` keep going: a buffering player is recovering, it has not been paused.
 * - `IDLE` keeps going only while the bounded #19 network recovery is still pending; an idle player
 *   with nothing pending has nothing to continue.
 * - A paused player (`playWhenReady == false`), an ended queue or an empty queue finish cleanly.
 *
 * This deliberately does not use Media3's default, which only keeps the service when
 * `Player.isPlaying()` is true. That is false while buffering or focus-suppressed, and the default then
 * also calls `setPlayWhenReady(false)` on every player, which would drop the user's play intent.
 */
internal fun shouldContinuePlaybackAfterTaskRemoved(
    playWhenReady: Boolean,
    playbackState: Int,
    mediaItemCount: Int,
    networkRecoveryPending: Boolean
): Boolean {
    if (!playWhenReady || mediaItemCount == 0) return false
    return when (playbackState) {
        Player.STATE_READY, Player.STATE_BUFFERING -> true
        Player.STATE_IDLE -> networkRecoveryPending
        else -> false
    }
}
