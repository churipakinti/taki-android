/*
 * RatingUpdate.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.data

import androidx.media3.common.Rating

data class RatingUpdate(val id: String, val rating: Rating, val success: Boolean? = null)
