/*
 * RatingManagerStarTargetTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.common.HeartRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.moire.ultrasonic.data.RatingUpdate

/**
 * Locks the album/track split for the OpenSubsonic/Navidrome `star`/`unstar` call (issue #15).
 *
 * ```
 * RatingUpdate(id, isAlbum = false) -> star(id = <id>,  albumId = null)
 * RatingUpdate(id, isAlbum = true)  -> star(id = null,  albumId = <id>)
 * ```
 *
 * The album id must not leak into the plain `id` parameter: the server would then look for a
 * *song* with that id and the favourite would silently not take.
 */
class RatingManagerStarTargetTest {

    @Test
    fun `track rating keeps the plain id form`() {
        val target = RatingManager.starTargetFor(
            RatingUpdate("track-1", HeartRating(true), isAlbum = false)
        )

        assertEquals("track-1", target.trackId)
        assertNull(target.albumId)
    }

    @Test
    fun `album rating moves the id into albumId`() {
        val target = RatingManager.starTargetFor(
            RatingUpdate("album-9", HeartRating(true), isAlbum = true)
        )

        assertNull(target.trackId)
        assertEquals("album-9", target.albumId)
    }

    @Test
    fun `unstar routing matches star routing`() {
        val album = RatingManager.starTargetFor(
            RatingUpdate("album-9", HeartRating(false), isAlbum = true)
        )
        val track = RatingManager.starTargetFor(
            RatingUpdate("track-1", HeartRating(false), isAlbum = false)
        )

        assertEquals(RatingManager.StarTarget(trackId = null, albumId = "album-9"), album)
        assertEquals(RatingManager.StarTarget(trackId = "track-1", albumId = null), track)
    }
}
