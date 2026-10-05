/*
 * TakiPickerOption.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

/** One selectable row of a [TakiPickerSheet]. [key] is what the host gets back on selection. */
data class TakiPickerOption(val key: String, val label: String)
