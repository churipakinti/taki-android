/*
 * AlbumDetailUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * The immutable, presentation-ready state of the Compose Album Detail screen (issue #10
 * phase 4A). A projection of what `TrackCollectionModel` already loads for
 * `TrackCollectionFragment`'s `isAlbum == true` id3 mode - no service / domain object reaches
 * Compose. Built once in [org.moire.ultrasonic.model.AlbumDetailViewModel] and never mutated
 * in the UI.
 */
@Immutable
data class AlbumDetailUiState(
    val isLoading: Boolean = true,
    /** The track list came back empty or the load failed - the screen shows the same
     *  "No media found" state the legacy `emptyView` shows. */
    val loadFailed: Boolean = false,
    val albumId: String? = null,
    val title: String = "",
    /** Single album artist, or the "Various Artists" label - always shown. */
    val artist: String = "",
    /** Non-null only when the album has exactly one artist with a known id: then the artist
     *  line is a navigation target (issue #16). Null = plain text. */
    val artistId: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val songCount: Int = 0,
    /** Pre-formatted total running time (`Util.formatTotalDuration`), or null when unknown. */
    val totalDuration: String? = null,
    /** Coil model for the hero cover, or null for the neutral placeholder. */
    val artworkModel: CoverArtRequest? = null,
    val isStarred: Boolean = false,
    /** The album heart is shown for every album mode (id3, folder, offline/downloaded) -
     *  `AlbumDetailHeaderBinder` registers `onToggleStar` unconditionally for `isAlbum`, with no
     *  id3 gate (issue #15). */
    val starVisible: Boolean = false,
    /** Album notes/review, once the slower `getAlbumInfo` call resolved to something non-empty.
     *  Drives the "Information" action's visibility, as in the legacy header. */
    val notes: String? = null,
    val hasMultipleDiscs: Boolean = false,
    /** Whether the "Start radio" overflow item is offered (hidden offline, like the legacy
     *  `song_menu_start_radio` visibility). */
    val radioAvailable: Boolean = true,
    val rows: ImmutableList<AlbumDetailRow> = persistentListOf(),
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()

    val infoAvailable: Boolean get() = !notes.isNullOrEmpty()
}

/**
 * One row in the Album Detail track list. A [Disc] marker only appears when
 * [AlbumDetailUiState.hasMultipleDiscs]; otherwise the list is just [Track]s in server order
 * (disc + track number, path tie-break) - identical to the legacy `buildDisplayList`.
 */
@Immutable
sealed interface AlbumDetailRow {

    @Immutable
    data class Disc(val number: Int) : AlbumDetailRow

    @Immutable
    data class Track(
        val id: String,
        /** Display track number, or null to hide the number column (matches
         *  `Settings.SHOULD_SHOW_TRACK_NUMBER` + a real positive number). */
        val number: String?,
        val title: String,
        /** The per-track artist, shown only when the album has multiple artists
         *  (`albumShowArtist` in the legacy binder). Null otherwise. */
        val artist: String?,
        val duration: String?,
        val isVideo: Boolean,
    ) : AlbumDetailRow
}

/**
 * What the host needs to open the Album Detail load. Interpreted from the unchanged
 * `trackCollectionFragment` nav arguments by `TrackCollectionFragment`.
 */
@Immutable
data class AlbumDetailArgs(
    val id: String?,
    val name: String?,
    /** `ActiveServerProvider.shouldUseId3Tags()` at open time - picks `getAlbumAsDir` (id3)
     *  vs `getMusicDirectory` (folder), exactly like the legacy `getLiveData`. */
    val isId3: Boolean,
    /** `!ActiveServerProvider.isOffline()` at open time - whether "Start radio" is offered
     *  (computed by the host so the ViewModel stays free of the server provider). */
    val radioAvailable: Boolean = true,
    val refresh: Boolean = false,
    /** True only for `DownloadedAlbumFragment` (issue #10 phase 4D): tracks load from the local
     *  offline database, exactly like the legacy `TrackCollectionModel.getDownloadedAlbumTracks`
     *  - no network call at all, and no notes/starred fold-in (the legacy screen never called
     *  `loadAlbumInfo`/`loadAlbumStarred` either). */
    val isDownloadedAlbum: Boolean = false,
)

/** The four secondary actions on the Album Detail overflow (issue #16 parity). */
enum class AlbumOverflowItem { GO_TO_ARTIST, PLAY_NEXT, PLAY_LAST, START_RADIO }

/**
 * The per-track long-press actions, one-for-one with the legacy
 * `R.menu.context_menu_track_collection` (album mode). Every one is dispatched back to the
 * host, which invokes the unchanged `ContextMenuUtil` / `DownloadUtil` / add-to-playlist
 * paths - no playback / queue / radio logic is re-implemented in Compose.
 */
enum class TrackContextAction {
    PLAY_NOW,
    PLAY_NEXT,
    PLAY_LAST,
    PLAY_FROM_HERE,
    START_RADIO,
    ADD_TO_PLAYLIST,
    DOWNLOAD,
    DELETE,
}

/**
 * Which of the three conditional track-context items to show, resolved once when the menu
 * opens (a point read of the download state + offline flag, exactly like the legacy
 * `Utils.createPopupMenu`). Ephemeral - never part of [AlbumDetailUiState].
 */
@Immutable
data class TrackContextMenuState(
    val canAddToPlaylist: Boolean = true,
    val canDownload: Boolean = true,
    val canDelete: Boolean = false,
)
