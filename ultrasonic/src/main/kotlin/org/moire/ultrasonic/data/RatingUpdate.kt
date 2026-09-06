/*
 * RatingUpdate.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.data

import androidx.media3.common.Rating

/**
 * A favourite/rating change to submit to the server.
 *
 * [isAlbum] switches the target from a track to an album: the OpenSubsonic/Navidrome
 * `star`/`unstar` calls take the id in the `albumId` parameter instead of `id`, and the
 * local metadata write-through targets the album row rather than the track row. Everything
 * else (the RxBus submit/publish round-trip, the optimistic-then-reconcile UI pattern) is
 * shared with track ratings.
 */
data class RatingUpdate(
    val id: String,
    val rating: Rating,
    val success: Boolean? = null,
    val isAlbum: Boolean = false
)
