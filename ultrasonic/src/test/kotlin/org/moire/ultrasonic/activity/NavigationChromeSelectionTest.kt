/*
 * NavigationChromeSelectionTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.robolectric.RobolectricTestRunner

/**
 * Locks [NavigationActivity.hidesSupportActionBar] - the rule that decides whether the shared
 * Material toolbar is hidden because the destination draws its own top chrome. Regression
 * guard for issue #10: the Box Sets list (`collectionListFragment`, phase 4B),
 * `collectionDetailFragment` (phase 4B) and `artistDetailFragment` (phase 4C) must each be in
 * this set (each renders a lightweight Compose top row), so the legacy olive toolbar never
 * appears across the Library -> Box Sets -> Collection Detail -> Album Detail /
 * Artist Detail flow. Also locks the shell-continuity fix for `trackCollectionFragment`'s
 * Genre and Daily Mix modes (`isLightweightHeaderTrackCollection`): unlike
 * `isAlbumDetail`/`isLibraryTrackCollection`, these draw a Fragment-owned Compose
 * `TakiScreenHeader` rather than the shared `content_navigation_header`, but they must hide the
 * Material toolbar the same way. And [NavigationActivity.isAlbumDetailDestination]: the offline
 * `downloadedAlbumFragment` destination renders the exact same Compose Album Detail screen as
 * `trackCollectionFragment` (issue #10 phase 4D) and must get identical chrome - a
 * destination-id-only check originally missed this, leaving the shared olive toolbar visible
 * over the downloaded album screen.
 *
 * Also locks Playlist Detail (`isPlaylistDetail`, issue #10 phase 4F3): it gets the exact same
 * treatment as `isAlbumDetail` - the shared `content_navigation_header` back bar, no
 * Fragment-owned header - since its hero already carries the playlist's own title.
 *
 * This can only assert the *selection*; that the Activity then actually calls
 * `supportActionBar?.hide()` is Activity-runtime behaviour, validated on the Pixel 7.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationChromeSelectionTest {

    private fun hides(
        id: Int,
        libraryTrackCollection: Boolean = false,
        albumDetail: Boolean = false,
        lightweightHeaderTrackCollection: Boolean = false,
        playlistDetail: Boolean = false,
        folderBrowser: Boolean = false,
    ) = NavigationActivity.hidesSupportActionBar(
        id,
        libraryTrackCollection,
        albumDetail,
        lightweightHeaderTrackCollection,
        playlistDetail,
        folderBrowser,
    )

    private fun showsBackBar(
        id: Int,
        libraryTrackCollection: Boolean = false,
        albumDetail: Boolean = false,
        playlistDetail: Boolean = false,
    ) = NavigationActivity.showsContentBackButton(id, libraryTrackCollection, albumDetail, playlistDetail)

    @Test
    fun `collection detail hides the shared toolbar - it draws its own Compose top row`() {
        assertTrue(hides(R.id.collectionDetailFragment))
    }

    @Test
    fun `the other Compose screens also hide the shared toolbar`() {
        assertTrue(hides(R.id.homeFragment))
        assertTrue(hides(R.id.mainFragment))
        assertTrue(hides(R.id.searchFragment))
    }

    @Test
    fun `album detail hides the toolbar only via the isAlbumDetail flag, not its id`() {
        assertFalse(hides(R.id.trackCollectionFragment))
        assertTrue(hides(R.id.trackCollectionFragment, albumDetail = true))
        assertTrue(hides(R.id.trackCollectionFragment, libraryTrackCollection = true))
    }

    @Test
    fun `the box-sets list hides the shared toolbar - it draws its own Compose header`() {
        assertTrue(hides(R.id.collectionListFragment))
    }

    @Test
    fun `the whole box-sets flow hides the shared toolbar`() {
        assertTrue(hides(R.id.collectionListFragment))
        assertTrue(hides(R.id.collectionDetailFragment))
        assertTrue(hides(R.id.trackCollectionFragment, albumDetail = true))
    }

    @Test
    fun `artist detail hides the shared toolbar - it draws its own Compose header (phase 4C)`() {
        assertTrue(hides(R.id.artistDetailFragment))
    }

    @Test
    fun `the whole browse-to-detail flow hides the shared toolbar`() {
        assertTrue(hides(R.id.collectionListFragment))
        assertTrue(hides(R.id.collectionDetailFragment))
        assertTrue(hides(R.id.artistDetailFragment))
        assertTrue(hides(R.id.trackCollectionFragment, albumDetail = true))
    }

    @Test
    fun `a plain browsing track-collection mode keeps the shared toolbar`() {
        assertFalse(hides(R.id.trackCollectionFragment))
    }

    @Test
    fun `genre and daily mix hide the shared toolbar - they draw their own lightweight header`() {
        assertTrue(hides(R.id.trackCollectionFragment, lightweightHeaderTrackCollection = true))
    }

    // --- isFolderBrowser (issue #10 phase 4M1) ------------------------------------------------

    @Test
    fun `folder browsing hides the shared toolbar - it draws its own lightweight header`() {
        assertFalse(hides(R.id.trackCollectionFragment))
        assertTrue(hides(R.id.trackCollectionFragment, folderBrowser = true))
    }

    @Test
    fun `a destination that doesn't already hide the toolbar isn't affected by an unset flag`() {
        // createPlaylistFragment isn't in the base set and none of the trackCollection-only
        // flags apply to it - it must keep its existing (shown) toolbar.
        assertFalse(hides(R.id.createPlaylistFragment))
    }

    @Test
    fun `liked songs is unaffected by the lightweight header flag - it already hid the toolbar`() {
        assertFalse(hides(R.id.trackCollectionFragment))
        // Liked Songs already hid the toolbar via isLibraryTrackCollection before this fix.
        assertTrue(hides(R.id.trackCollectionFragment, libraryTrackCollection = true))
    }

    // --- isPlaylistDetail (issue #10 phase 4F3) -----------------------------------------------

    @Test
    fun `playlist detail hides the toolbar only via the isPlaylistDetail flag, not its id`() {
        assertFalse(hides(R.id.trackCollectionFragment))
        assertTrue(hides(R.id.trackCollectionFragment, playlistDetail = true))
    }

    @Test
    fun `playlist detail gets the same treatment as album detail - the shared back bar, no Fragment header`() {
        assertTrue(hides(R.id.trackCollectionFragment, playlistDetail = true))
        assertTrue(hides(R.id.trackCollectionFragment, albumDetail = true))
    }

    // --- Playlists List (issue #10 phase 4G1) -------------------------------------------------

    @Test
    fun `playlists list hides the shared toolbar - it was already in the base set before this phase`() {
        // No NavigationActivity change was needed for phase 4G1 - playlistsFragment was already
        // in hidesSupportActionBar's base id set (and showsContentBackButton's) before the
        // legacy PlaylistsFragment was replaced with the Compose PlaylistListFragment. This test
        // only closes a pre-existing gap: nothing in this suite asserted it explicitly before.
        assertTrue(hides(R.id.playlistsFragment))
    }

    // --- Genres List (issue #10 phase 4G2) ----------------------------------------------------

    @Test
    fun `genres list hides the shared toolbar - it was already in the base set before this phase`() {
        // No NavigationActivity change was needed for phase 4G2 either - selectGenreFragment was
        // already in hidesSupportActionBar's base id set (and showsContentBackButton's) before
        // the legacy SelectGenreFragment was replaced with the Compose GenreListFragment.
        assertTrue(hides(R.id.selectGenreFragment))
    }

    // --- Downloads (issue #10 phase 4G3) ------------------------------------------------------

    @Test
    fun `downloads hides the shared toolbar - it was already in the base set before this phase`() {
        // No NavigationActivity change was needed for phase 4G3 either - downloadsFragment was
        // already in hidesSupportActionBar's base id set (and showsContentBackButton's) before
        // the legacy DownloadsFragment was replaced with the Compose one, which draws its own
        // "Downloads" title under the shared content_navigation_header back bar.
        assertTrue(hides(R.id.downloadsFragment))
    }

    // --- isAlbumDetailDestination (issue #10 phase 4D shell-continuity fix) ---------------

    @Test
    fun `downloadedAlbumFragment is recognised as an album-detail destination, same as trackCollectionFragment`() {
        assertTrue(
            NavigationActivity.isAlbumDetailDestination(R.id.downloadedAlbumFragment, isAlbumArg = true),
        )
        assertTrue(
            NavigationActivity.isAlbumDetailDestination(R.id.trackCollectionFragment, isAlbumArg = true),
        )
    }

    @Test
    fun `isAlbumDetailDestination still requires the isAlbum arg`() {
        assertFalse(
            NavigationActivity.isAlbumDetailDestination(R.id.downloadedAlbumFragment, isAlbumArg = false),
        )
        assertFalse(
            NavigationActivity.isAlbumDetailDestination(R.id.trackCollectionFragment, isAlbumArg = false),
        )
    }

    @Test
    fun `isAlbumDetailDestination does not widen to unrelated destinations`() {
        // Regression guard: only the two Fragments that actually render Compose Album Detail
        // qualify - a plain Downloads list (which merely opens downloadedAlbumFragment) must not.
        assertFalse(
            NavigationActivity.isAlbumDetailDestination(R.id.downloadsFragment, isAlbumArg = true),
        )
    }

    @Test
    fun `the downloaded album screen hides the shared toolbar exactly like online album detail`() {
        val downloaded = NavigationActivity.isAlbumDetailDestination(
            R.id.downloadedAlbumFragment,
            isAlbumArg = true,
        )
        val online = NavigationActivity.isAlbumDetailDestination(
            R.id.trackCollectionFragment,
            isAlbumArg = true,
        )
        assertTrue(hides(R.id.downloadedAlbumFragment, albumDetail = downloaded))
        assertTrue(hides(R.id.trackCollectionFragment, albumDetail = online))
    }

    // --- About (post-issue-#10 residual migration, phase 5A1) ---------------------------------

    @Test
    fun `about still hides the shared toolbar - unchanged by phase 5A1`() {
        assertTrue(hides(R.id.aboutFragment))
    }

    @Test
    fun `about no longer shows the shared back bar - it draws its own TakiScreenHeader`() {
        assertFalse(showsBackBar(R.id.aboutFragment))
    }

    @Test
    fun `about is the only destination phase 5A1 removed from the shared back bar`() {
        // Regression guard: every other destination that showed the shared back bar before
        // phase 5A1 must still show it - only aboutFragment's entry was removed there.
        // serverSelectorFragment/editServerFragment are asserted separately below (removed in
        // phases 5A2/5A3, not 5A1).
        assertTrue(showsBackBar(R.id.playlistsFragment))
        assertTrue(showsBackBar(R.id.artistListFragment))
        assertTrue(showsBackBar(R.id.albumListFragment))
        assertTrue(showsBackBar(R.id.selectGenreFragment))
        assertTrue(showsBackBar(R.id.downloadsFragment))
        assertTrue(showsBackBar(R.id.settingsFragment))
        assertTrue(showsBackBar(R.id.equalizerFragment))
        assertTrue(showsBackBar(R.id.trackCollectionFragment, libraryTrackCollection = true))
        assertTrue(showsBackBar(R.id.trackCollectionFragment, albumDetail = true))
        assertTrue(showsBackBar(R.id.trackCollectionFragment, playlistDetail = true))
    }

    // --- Server Selector (post-issue-#10 residual migration, phase 5A2) -----------------------

    @Test
    fun `server selector still hides the shared toolbar - unchanged by phase 5A2`() {
        assertTrue(hides(R.id.serverSelectorFragment))
    }

    @Test
    fun `server selector no longer shows the shared back bar - it draws its own TakiScreenHeader`() {
        assertFalse(showsBackBar(R.id.serverSelectorFragment))
    }

    // --- Edit Server (post-issue-#10 residual migration, phase 5A3) ---------------------------

    @Test
    fun `edit server still hides the shared toolbar - unchanged by phase 5A3`() {
        assertTrue(hides(R.id.editServerFragment))
    }

    @Test
    fun `edit server no longer shows the shared back bar - it draws its own TakiScreenHeader`() {
        // Its own header's back action now routes through the same dirty-check as system Back,
        // fixing the gap where the shared bar's back arrow bypassed it entirely (phase 5A3 audit).
        assertFalse(showsBackBar(R.id.editServerFragment))
    }

    @Test
    fun `box sets list still draws its own header, not the shared back bar - unaffected by phase 5A1`() {
        assertFalse(showsBackBar(R.id.collectionListFragment))
        assertTrue(hides(R.id.collectionListFragment))
    }
}
