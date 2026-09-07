/*
 * HomeUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * Everything the Home Compose screen renders, as one immutable snapshot. Built by
 * `HomeViewModel` from the domain entities; the composables never see a Room entity.
 *
 * Behaviour parity with the old XML Home: shelves appear only when non-empty, the "all
 * empty" message replaces the content, and a cold load shows a placeholder skeleton while
 * the swipe spinner runs.
 */
@Immutable
data class HomeUiState(
    val greeting: HomeGreeting = HomeGreeting.MORNING,
    /** Cold load with nothing to show yet - render the skeleton. */
    val isLoading: Boolean = true,
    /** A fetch (initial or pull-to-refresh) is in flight - run the refresh indicator. */
    val isRefreshing: Boolean = false,
    val featuredMix: FeaturedMixUi? = null,
    val recentlyPlayed: ImmutableList<HomeAlbumUi> = persistentListOf(),
    val shelves: ImmutableList<HomeShelfUi> = persistentListOf(),
) {
    /** True once loaded and every source came back empty (folds the old `updateEmptyState`). */
    val isEmpty: Boolean
        get() = !isLoading &&
            featuredMix == null &&
            recentlyPlayed.isEmpty() &&
            shelves.all { it.albums.isEmpty() }

    val hasContent: Boolean
        get() = featuredMix != null ||
            recentlyPlayed.isNotEmpty() ||
            shelves.any { it.albums.isNotEmpty() }
}

enum class HomeGreeting { MORNING, AFTERNOON, EVENING }

/**
 * The daily mix, promoted from the old small row to the canonical featured card.
 * [artworkModel] is the first track's cover; opaque to the composable.
 */
@Immutable
data class FeaturedMixUi(
    val trackCount: Int,
    val artworkModel: CoverArtRequest?,
)

@Immutable
data class HomeAlbumUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkModel: CoverArtRequest?,
    val isDirectory: Boolean,
    val parentId: String?,
)

/** Which canonical shelf this is - drives the section header and artwork weight. */
enum class HomeShelfKind { LIKED, NEWEST, DISCOVER, FREQUENT }

@Immutable
data class HomeShelfUi(
    val kind: HomeShelfKind,
    val albums: ImmutableList<HomeAlbumUi>,
)

/** Time-of-day greeting bucket. Boundaries match the old `HomeFragment` constants. */
internal fun greetingForHour(hourOfDay: Int): HomeGreeting = when {
    hourOfDay < MORNING_ENDS_AT_HOUR -> HomeGreeting.MORNING
    hourOfDay < AFTERNOON_ENDS_AT_HOUR -> HomeGreeting.AFTERNOON
    else -> HomeGreeting.EVENING
}

private const val MORNING_ENDS_AT_HOUR = 12
private const val AFTERNOON_ENDS_AT_HOUR = 18
