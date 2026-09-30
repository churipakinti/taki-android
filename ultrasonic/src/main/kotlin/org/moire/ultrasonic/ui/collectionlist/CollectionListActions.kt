/*
 * CollectionListActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collectionlist

/** Callbacks [CollectionListScreen] needs from its host. */
data class CollectionListActions(
    val onBack: () -> Unit,
    val onCollectionClick: (CollectionListRow) -> Unit,
    val onRefresh: () -> Unit,
) {
    companion object {
        val Noop = CollectionListActions(onBack = {}, onCollectionClick = {}, onRefresh = {})
    }
}
