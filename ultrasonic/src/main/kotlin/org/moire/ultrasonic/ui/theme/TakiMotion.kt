/*
 * TakiMotion.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Taki's motion spec for Compose. Motion is functional and quiet: no bounce, no pulse on
 * normal content, no press scale beyond ~1.02, and playback response is never delayed to
 * wait for an animation.
 *
 * There is no XML equivalent to mirror - these come straight from
 * docs/design/TAKI_DESIGN_SYSTEM_V2.md section 17. Durations are milliseconds.
 */
@Immutable
@Suppress("MagicNumber") // literal transcription of TAKI_DESIGN_SYSTEM_V2.md section 17
data class TakiMotion(
    /** Press / selection feedback. */
    val pressFeedbackMillis: Int = 120,
    /** Icon / state crossfade, artwork crossfade. */
    val stateCrossfadeMillis: Int = 180,
    /** Content insertion / removal. */
    val contentChangeMillis: Int = 220,
    /** Navigation transition. */
    val navigationMillis: Int = 240,
    /** Mini-player to Now Playing continuity. */
    val playerContinuityMillis: Int = 300,
    /** Standard easing - most transitions. */
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
    /** Exit easing. */
    val exitEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f),
    /** Emphasized / decelerate easing - navigation and player continuity. */
    val emphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f),
)

/**
 * Ambient [TakiMotion]. Read through `TakiTheme.motion`.
 */
val LocalTakiMotion = staticCompositionLocalOf { TakiMotion() }
