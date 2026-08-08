/*
 * DiscHeader.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track

/** A small divider row marking the start of a disc's tracks in a multi-disc Album Detail. */
data class DiscHeader(val discNumber: Int, val tracks: List<Track>) : Identifiable {
    override val id: String get() = "DISC_$discNumber"
}
