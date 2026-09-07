/*
 * AlbumDetailModeSelectionTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.moire.ultrasonic.fragment.TrackCollectionFragment.Companion.shouldUseComposeAlbumDetail

/**
 * The hard acceptance rule for issue #10 phase 4A: **only** the id3 album-detail mode of
 * `TrackCollectionFragment` switches to Compose. Every other mode - Liked Songs, All Songs,
 * genre / random / video / daily-mix / folder collections, playlists, and the local-only
 * `DownloadedAlbumFragment` - must still select the legacy View path.
 */
class AlbumDetailModeSelectionTest {

    @Test
    fun `the id3 album mode uses the Compose screen`() {
        assertTrue(
            shouldUseComposeAlbumDetail(
                allow = true, isAlbum = true, hasPlaylistId = false, usesId3 = true,
            ),
        )
    }

    @Test
    fun `a playlist never uses the Compose album screen`() {
        assertFalse(
            shouldUseComposeAlbumDetail(
                allow = true, isAlbum = true, hasPlaylistId = true, usesId3 = true,
            ),
        )
    }

    @Test
    fun `non-album track collections stay on the View path`() {
        // Liked Songs / All Songs / genre / random / daily mix all arrive with isAlbum = false.
        assertFalse(
            shouldUseComposeAlbumDetail(
                allow = true, isAlbum = false, hasPlaylistId = false, usesId3 = true,
            ),
        )
    }

    @Test
    fun `folder mode albums stay on the View path`() {
        // !shouldUseId3Tags() -> getMusicDirectory can return sub-folders; the legacy screen
        // handles those, the Compose one deliberately does not.
        assertFalse(
            shouldUseComposeAlbumDetail(
                allow = true, isAlbum = true, hasPlaylistId = false, usesId3 = false,
            ),
        )
    }

    @Test
    fun `a Fragment that opts out (DownloadedAlbumFragment) stays on the View path`() {
        assertFalse(
            shouldUseComposeAlbumDetail(
                allow = false, isAlbum = true, hasPlaylistId = false, usesId3 = true,
            ),
        )
    }
}
