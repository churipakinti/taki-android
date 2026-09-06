/*
 * PlaybackStallWatchdog.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

// A silent decoder stall (see issue #17: hi-res FLAC on the platform c2.android.flac.decoder
// stops emitting samples mid-frame without raising onPlayerError) leaves the player reporting
// "playing" while the position never advances. These thresholds describe that state so a
// checkpoint tick can spot it and act.

// How far the position must move between two ticks to count as "still playing". A hair above
// zero so clock jitter / rounding doesn't read as a stall, far below one tick's worth of real
// playback.
internal const val STALL_MIN_ADVANCE_MS = 250L

// A real decoder stall has plenty of demuxed data buffered ahead that simply isn't being
// consumed. Plain network starvation looks different - the buffer is drained down to the play
// head - so requiring a healthy read-ahead here keeps the watchdog from firing on a slow
// connection that would recover on its own.
internal const val STALL_MIN_BUFFER_AHEAD_MS = 3_000L

// Don't treat "position not moving" as a stall when we're basically at the end of the track;
// that's the natural STATE_ENDED transition, not a hang.
internal const val STALL_END_GUARD_MS = 1_000L

// The one recovery attempt nudges the play head just past the frame the decoder choked on,
// rather than re-seeking exactly in place (which tends to re-hit the same bad frame).
internal const val STALL_RECOVERY_SEEK_SKIP_MS = 1_000L

/**
 * What the caller should do about the current tick.
 */
internal sealed interface StallAction {
    /** Playback looks healthy (or isn't running); nothing to do. */
    object None : StallAction

    /** First stall seen: seek to [seekTargetMs] and re-prepare once, then wait a tick. */
    data class Recover(val seekTargetMs: Long) : StallAction

    /** Still stalled after the recovery attempt: surface an error and skip/stop. */
    object Escalate : StallAction
}

/**
 * Pure state machine for the playback-stall watchdog (issue #17, FIX B). It holds no Android or
 * Media3 references so it can be unit tested directly; [MediaPlayerManager] feeds it a snapshot
 * of the player once per checkpoint tick and executes whatever [StallAction] it returns.
 *
 * Escalation ladder, matching the issue's acceptance criteria:
 *  1. First tick where the position is frozen while data is buffered ahead -> [StallAction.Recover].
 *  2. Next tick still frozen (the nudge didn't help) -> [StallAction.Escalate] exactly once.
 *  3. Any tick where the position advanced -> back to [StallAction.None]; the ladder resets.
 */
internal class PlaybackStallWatchdog(
    private val minAdvanceMs: Long = STALL_MIN_ADVANCE_MS,
    private val minBufferAheadMs: Long = STALL_MIN_BUFFER_AHEAD_MS,
    private val endGuardMs: Long = STALL_END_GUARD_MS,
    private val recoverySkipMs: Long = STALL_RECOVERY_SEEK_SKIP_MS
) {
    /** Position seen on the previous qualifying tick, or null when there's no baseline yet. */
    private var baselinePositionMs: Long? = null

    /** True after a [StallAction.Recover] was issued and we're waiting to see if it worked. */
    private var recoveryPending = false

    val isRecoveryPending: Boolean
        get() = recoveryPending

    /** Drop all state - call on track transitions and user seeks. */
    fun reset() {
        baselinePositionMs = null
        recoveryPending = false
    }

    /**
     * @param positionMs current play position
     * @param playWhenReady the player's intent to play (stays true through the READY/BUFFERING
     *   flap a stall produces, unlike isPlaying)
     * @param isReadyOrBuffering playbackState is STATE_READY or STATE_BUFFERING
     * @param bufferedPositionMs how far ahead data is buffered
     * @param durationMs track duration, or null if not known yet
     */
    fun onTick(
        positionMs: Long,
        playWhenReady: Boolean,
        isReadyOrBuffering: Boolean,
        bufferedPositionMs: Long,
        durationMs: Long?
    ): StallAction {
        if (!playWhenReady || !isReadyOrBuffering) {
            reset()
            return StallAction.None
        }

        val baseline = baselinePositionMs
        if (baseline == null) {
            baselinePositionMs = positionMs
            return StallAction.None
        }

        val advanced = positionMs - baseline >= minAdvanceMs
        val bufferedAheadMs = bufferedPositionMs - positionMs
        val nearEnd = durationMs != null &&
            durationMs > 0 &&
            positionMs >= durationMs - endGuardMs
        val stalled = !advanced && bufferedAheadMs >= minBufferAheadMs && !nearEnd

        if (!stalled) {
            recoveryPending = false
            baselinePositionMs = positionMs
            return StallAction.None
        }

        return if (!recoveryPending) {
            recoveryPending = true
            val target = positionMs + recoverySkipMs
            // Compare the next tick against where the nudge should have landed, so a stall that
            // simply moves to the new spot still reads as "not advanced" and escalates.
            baselinePositionMs = target
            StallAction.Recover(target)
        } else {
            reset()
            StallAction.Escalate
        }
    }
}
