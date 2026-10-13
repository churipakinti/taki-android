/*
 * PlaybackLoadControl.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.exoplayer.DefaultLoadControl

// Issue #35. Taki's media-item URIs look like "<id>|<bitrate>|<path>": they have no URI scheme,
// and Media3's DefaultLoadControl treats a scheme-less URI as *local playback* (it only
// recognises file/content/data/asset/...). Every track - streamed or downloaded - therefore gets
// the local-playback thresholds, whose default minimum buffer is 1 s. The loader paused at ~50 s
// ahead and did not resume until the buffer had drained to ~1 s, so a network interruption near
// the bottom of that 1-50 s sawtooth had almost no audio reserve.
//
// The URI cannot cheaply be changed (it is the contract with PlaybackService's resolver,
// CachedDataSource and persisted queues), so the local-playback thresholds are configured
// instead. Downloaded files are read from disk and never hit the network, so a higher floor only
// makes the loader top up earlier; memory stays bounded by the unchanged maximum.

/** Keep at least this much audio ahead; the loader tops up whenever the buffer falls below it. */
internal const val PLAYBACK_MIN_BUFFER_MS = 30_000

/** Upper bound of the forward buffer. Unchanged from the Media3 default, so memory is unchanged. */
internal const val PLAYBACK_MAX_BUFFER_MS = 50_000

/** Audio needed before playback starts: kept at the Media3 default so Play/Seek stay immediate. */
internal const val PLAYBACK_START_BUFFER_MS = 1_000

/** Audio needed to resume after a rebuffer: kept at the Media3 default for prompt recovery. */
internal const val PLAYBACK_REBUFFER_RESUME_MS = 1_000

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createPlaybackLoadControl(): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMsForLocalPlayback(
            PLAYBACK_MIN_BUFFER_MS,
            PLAYBACK_MAX_BUFFER_MS,
            PLAYBACK_START_BUFFER_MS,
            PLAYBACK_REBUFFER_RESUME_MS
        )
        .build()
