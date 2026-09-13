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
 * The acceptance rule for issue #10 Album Detail: every `isAlbum` mode of
 * `TrackCollectionFragment` - id3, folder (phase 4D), and the offline `DownloadedAlbumFragment`
 * (phase 4D) - switches to Compose. Only playlists and the non-album modes (Liked Songs, All
 * Songs, genre / random / video / daily-mix / folder collections) stay on the legacy View path,
 * plus a generic `allow = false` escape hatch for any future Fragment that needs to opt out.
 */
class AlbumDetailModeSelectionTest {

    @Test
    fun `the id3 album mode uses the Compose screen`() {
        assertTrue(
            shouldUseComposeAlbumDetail(allow = true, isAlbum = true, hasPlaylistId = false),
        )
    }

    @Test
    fun `folder-mode albums also use the Compose screen (phase 4D)`() {
        // Folder-mode "album" directories can, rarely, contain nested sub-folders (e.g. a
        // disc-per-folder layout); AlbumDetailViewModel shows only the tracks directly in that
        // folder, a disclosed, narrow limitation - see the phase 4D report. usesId3 is no
        // longer a parameter of this predicate at all: AlbumDetailViewModel picks
        // getAlbumAsDir vs getMusicDirectory itself from AlbumDetailArgs.isId3.
        assertTrue(
            shouldUseComposeAlbumDetail(allow = true, isAlbum = true, hasPlaylistId = false),
        )
    }

    @Test
    fun `the offline DownloadedAlbumFragment also uses the Compose screen (phase 4D)`() {
        // DownloadedAlbumFragment no longer overrides allowComposeAlbumDetail - it routes to
        // AlbumDetailViewModel's local-database loader via isDownloadedAlbumSource instead,
        // which this predicate doesn't need to know about.
        assertTrue(
            shouldUseComposeAlbumDetail(allow = true, isAlbum = true, hasPlaylistId = false),
        )
    }

    @Test
    fun `a playlist never uses the Compose album screen`() {
        assertFalse(
            shouldUseComposeAlbumDetail(allow = true, isAlbum = true, hasPlaylistId = true),
        )
    }

    @Test
    fun `non-album track collections stay on the View path`() {
        // Liked Songs / All Songs / genre / random / daily mix all arrive with isAlbum = false.
        assertFalse(
            shouldUseComposeAlbumDetail(allow = true, isAlbum = false, hasPlaylistId = false),
        )
    }

    @Test
    fun `a fragment that opts out via allow = false stays on the View path`() {
        // The generic escape hatch (allowComposeAlbumDetail) - no current subclass sets it
        // false, but the predicate still honours it if one ever needs to.
        assertFalse(
            shouldUseComposeAlbumDetail(allow = false, isAlbum = true, hasPlaylistId = false),
        )
    }
}
