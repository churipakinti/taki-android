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
 * guard for issue #10 phase 4B: the Box Sets list (`collectionListFragment`) and
 * `collectionDetailFragment` must both be in this set (each renders a lightweight Compose top
 * row), so the legacy olive toolbar never appears across the Library -> Box Sets -> Collection
 * Detail -> Album Detail flow. Artist Detail is deliberately left on its legacy behaviour
 * until its own migration.
 *
 * This can only assert the *selection*; that the Activity then actually calls
 * `supportActionBar?.hide()` is Activity-runtime behaviour, validated on the Pixel 7.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationChromeSelectionTest {

    private fun hides(id: Int, libraryTrackCollection: Boolean = false, albumDetail: Boolean = false) =
        NavigationActivity.hidesSupportActionBar(id, libraryTrackCollection, albumDetail)

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
    fun `artist detail keeps its legacy toolbar until its own migration`() {
        assertFalse(hides(R.id.artistDetailFragment))
    }

    @Test
    fun `a plain browsing destination keeps the shared toolbar`() {
        assertFalse(hides(R.id.trackCollectionFragment))
    }
}
