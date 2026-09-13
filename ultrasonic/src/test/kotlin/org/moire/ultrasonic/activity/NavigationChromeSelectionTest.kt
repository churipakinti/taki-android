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
    ) = NavigationActivity.hidesSupportActionBar(
        id,
        libraryTrackCollection,
        albumDetail,
        lightweightHeaderTrackCollection,
    )

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

    @Test
    fun `a destination that doesn't already hide the toolbar isn't affected by an unset flag`() {
        // createPlaylistFragment isn't in the base set and none of the trackCollection-only
        // flags apply to it - it must keep its existing (shown) toolbar.
        assertFalse(hides(R.id.createPlaylistFragment))
    }

    @Test
    fun `playlist detail and liked songs are unaffected by the lightweight header flag`() {
        // Playlist detail: none of the three flags apply to it, it keeps its existing behaviour.
        assertFalse(hides(R.id.trackCollectionFragment))
        // Liked Songs already hid the toolbar via isLibraryTrackCollection before this fix.
        assertTrue(hides(R.id.trackCollectionFragment, libraryTrackCollection = true))
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
}
