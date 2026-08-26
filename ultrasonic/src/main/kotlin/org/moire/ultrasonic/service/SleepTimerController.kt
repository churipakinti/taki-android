/*
 * SleepTimerController.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Immutable Sleep Timer state. Deliberately does not carry a ticking "remaining time"
 * field - callers recompute it on demand from
 * [Duration.deadlineElapsedRealtime] against the current clock, which is all the UI needs (the
 * menu title and the picker dialog only ever read it when opened, not continuously).
 */
sealed class SleepTimerState {
    object Off : SleepTimerState()

    data class Duration(
        val deadlineElapsedRealtime: Long,
        /** The preset (15/30/45/60) minutes value this was armed with, kept only so the picker
         *  dialog can re-check the right row when reopened - not used for any timing decision. */
        val presetMinutes: Int
    ) : SleepTimerState() {
        fun remainingMs(nowElapsedRealtime: Long): Long =
            (deadlineElapsedRealtime - nowElapsedRealtime).coerceAtLeast(0)
    }

    object EndOfTrack : SleepTimerState()
}

/**
 * Headless Sleep Timer state machine. Owned by [MediaPlayerManager], which is the
 * singleton that survives Activity/Fragment destruction,
 * rotation, backgrounding and screen-off - this class holds no reference to Activity, Fragment,
 * View or Context, and must not, or the timer would stop counting exactly when it matters most
 * (screen off, app backgrounded).
 *
 * Two independent arm modes, mutually exclusive - activating either one replaces whatever was
 * armed before:
 *  - [SleepTimerState.Duration]: expires once after a fixed number of minutes, measured with
 *    [elapsedRealtimeMs] (monotonic, immune to wall-clock/timezone changes) via a single
 *    cancelable `delay()` [Job]. Pausing playback does not touch this Job - it keeps counting
 *    regardless of playback state, per spec.
 *  - [SleepTimerState.EndOfTrack]: no Job/deadline - stays armed indefinitely until the owner
 *    reports a natural end-of-track event via [onTrackFinishedNaturally]. A manual skip must
 *    NOT call that function (see its doc), so jumping to another track leaves the mode armed for
 *    the new track instead of firing early.
 */
class SleepTimerController(
    private val scope: CoroutineScope,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
    private val onExpire: () -> Unit
) {
    var state: SleepTimerState = SleepTimerState.Off
        private set

    private var job: Job? = null

    fun setDuration(minutes: Int) {
        arm(
            SleepTimerState.Duration(
                deadlineElapsedRealtime = elapsedRealtimeMs() + minutes * MILLIS_PER_MINUTE,
                presetMinutes = minutes
            )
        )
    }

    fun setEndOfTrack() {
        arm(SleepTimerState.EndOfTrack)
    }

    /** Cancels whatever is armed, if anything. Never invokes [onExpire]. */
    fun cancel() {
        arm(SleepTimerState.Off)
    }

    private fun arm(newState: SleepTimerState) {
        job?.cancel()
        job = null
        state = newState
        if (newState is SleepTimerState.Duration) {
            val remaining = newState.remainingMs(elapsedRealtimeMs())
            job = scope.launch {
                delay(remaining)
                expire()
            }
        }
    }

    /**
     * Report a natural end-of-track event: an automatic transition to the next item
     * ([androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO] or
     * [androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT]) or the queue reaching
     * [androidx.media3.common.Player.STATE_ENDED]. No-op unless currently armed for
     * [SleepTimerState.EndOfTrack] - a [SleepTimerState.Duration] timer ignores this entirely,
     * and calling it while [SleepTimerState.Off] is harmless.
     *
     * Must NOT be called for a manual skip/seek transition
     * ([androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK]) - that would fire the
     * timer on the very next tap of Next instead of leaving it armed for the new track.
     */
    fun onTrackFinishedNaturally() {
        if (state is SleepTimerState.EndOfTrack) expire()
    }

    private fun expire() {
        job?.cancel()
        job = null
        state = SleepTimerState.Off
        onExpire()
    }

    private companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
