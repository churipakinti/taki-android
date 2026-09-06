/*
 * MediaItemConverterStarredTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage for issue #1 -- the liked/heart state not surviving a readback.
 *
 * Root cause: [MediaItem.toTrack] read `starred` from the `"starred"` extra, then
 * unconditionally overrode it from `mediaMetadata.userRating` (a [HeartRating] that
 * [buildMediaItem] always sets). [MediaItem.setStarred] -- the like toggle -- can only patch
 * the mutable extras Bundle, not the immutable [androidx.media3.common.MediaMetadata], so the
 * override kept resetting a freshly-liked track to its pre-like state on every surface that
 * derives from [MediaItem.toTrack] (Now Playing heart, current track model,
 * MediaSession/notification heart button).
 *
 * A real player track always carries the `"starred"` extra (written by `Track.toMediaItem()`);
 * these build the equivalent item via [buildMediaItem] plus that extra, avoiding
 * `toMediaItem()`'s `Settings`/`Storage` dependencies.
 */
@RunWith(RobolectricTestRunner::class)
class MediaItemConverterStarredTest {

    @Before
    @After
    fun clearConverterCaches() {
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    /** A playable track MediaItem shaped like `Track.toMediaItem()`'s output. */
    private fun playerTrack(id: String, starred: Boolean): MediaItem {
        val item = buildMediaItem(
            title = "Title $id",
            mediaId = id,
            isPlayable = true,
            artist = "Artist $id",
            starred = starred
        )
        item.mediaMetadata.extras!!.putBoolean("starred", starred)
        return item
    }

    @Test
    fun `toTrack reads the starred extra for a liked track`() {
        assertTrue(playerTrack("1", starred = true).toTrack().starred)
        assertFalse(playerTrack("2", starred = false).toTrack().starred)
    }

    @Test
    fun `liking via setStarred survives the next toTrack`() {
        val item = playerTrack("track-a", starred = false)

        item.setStarred(true)

        assertTrue(
            "A freshly liked track must read back as starred (issue #1)",
            item.toTrack().starred
        )
    }

    @Test
    fun `unliking via setStarred survives the next toTrack`() {
        val item = playerTrack("track-b", starred = true)

        item.setStarred(false)

        assertFalse(item.toTrack().starred)
    }

    @Test
    fun `repeated toTrack calls keep the toggled state (cache stays coherent)`() {
        val item = playerTrack("track-c", starred = false)
        item.setStarred(true)

        assertTrue(item.toTrack().starred)
        // Second call hits the converter's Track cache.
        assertTrue(item.toTrack().starred)

        item.setStarred(false)
        assertFalse(item.toTrack().starred)
        assertFalse(item.toTrack().starred)
    }

    @Test
    fun `a MediaItem without the starred extra still falls back to the HeartRating`() {
        // Folder / browse nodes are built straight through buildMediaItem() and carry no
        // "starred" extra; the userRating HeartRating is the only signal there.
        val noExtra = buildMediaItem(
            title = "Browse node",
            mediaId = "node-1",
            isPlayable = true,
            starred = true
        )
        assertFalse(noExtra.mediaMetadata.extras!!.containsKey("starred"))

        assertTrue(noExtra.toTrack().starred)
    }

    @Test
    fun `the starred extra wins over a stale HeartRating`() {
        // userRating says not-liked (build-time value), extra says liked (post-toggle value).
        val item = buildMediaItem(
            title = "Title d",
            mediaId = "track-d",
            isPlayable = true,
            starred = false
        )
        assertEquals(HeartRating(false), item.mediaMetadata.userRating)
        item.mediaMetadata.extras!!.putBoolean("starred", true)

        assertTrue(item.toTrack().starred)
    }
}
