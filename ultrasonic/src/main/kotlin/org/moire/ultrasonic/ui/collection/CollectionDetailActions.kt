/*
 * CollectionDetailActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collection

/**
 * The callbacks the Compose Collection Detail screen fires. Every one is implemented in
 * `CollectionDetailFragment` (the host / navigation boundary); the screen itself owns no
 * `NavController` and no ViewModel. Mirrors `AlbumDetailActions` / `HomeActions` etc.
 *
 * There is no Play / Shuffle / download / context-menu callback here: the legacy screen had
 * none, and phase 4B does not add actions for symmetry with Album Detail.
 */
class CollectionDetailActions(
    /** Back out of the collection (the top row's own affordance - this screen hides the
     *  Activity chrome, unlike Album Detail). Wired to `NavController.navigateUp()`. */
    val onBack: () -> Unit,
    /** Toggle the member grid <-> list layout (legacy `menu_toggle_collection_layout`). */
    val onToggleLayout: () -> Unit,
    /** Open one member release - navigates to the existing `trackCollectionFragment`
     *  (Compose Album Detail) with `isAlbum = true`, exactly like the legacy `openDisc`. */
    val onOpenMember: (CollectionMember) -> Unit,
    /** Pull-to-refresh: re-resolve the collection through the same load path. */
    val onRefresh: () -> Unit,
    /** The "Find missing discs" action (legacy `menu_discover_more_discs` / `discoverMore`). */
    val onDiscoverMore: () -> Unit,
) {
    companion object {
        val Noop = CollectionDetailActions(
            onBack = {},
            onToggleLayout = {},
            onOpenMember = {},
            onRefresh = {},
            onDiscoverMore = {},
        )
    }
}
