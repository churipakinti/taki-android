/*
 * PlaylistDetailScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.album.TrackContextAction
import org.moire.ultrasonic.ui.album.TrackContextMenuState
import org.moire.ultrasonic.ui.components.DetailPrimaryPlayButton
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiTrackRow
import org.moire.ultrasonic.ui.theme.TakiTheme

const val PLAYLIST_DETAIL_LIST_TEST_TAG = "playlist_detail_list"

/**
 * Playlist Detail (issue #10 phase 4F3) - a specialized sibling of
 * [org.moire.ultrasonic.ui.album.AlbumDetailScreen], not a mode bolted onto
 * [org.moire.ultrasonic.ui.tracklist.TrackListScreen]: the legacy screen already reused
 * `AlbumDetailHeaderBinder`'s hero for playlists (same artwork-first shape, Play + Shuffle, a
 * single trailing overflow), so this screen mirrors Album Detail's layout closely, minus
 * everything a playlist never had - see [PlaylistDetailUiState]'s kdoc. [onShowHeaderMenu] opens
 * the legacy `ItemSelectionDialogFragment` picker (Download / Rename / Delete) rather than a
 * Compose `DropdownMenu`, reusing the host's existing dialog + result-routing verbatim (issue #10
 * phase 4F3 report, "shared primitives reused").
 *
 * [currentTrackId] is the id of the track playback is on (from `PlaybackUiStateHolder`), same as
 * Album Detail. [bottomContentInset] is the live floating-chrome reserve, same as every other
 * screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    actions: PlaylistDetailActions,
    currentTrackId: String?,
    modifier: Modifier = Modifier,
    bottomContentInset: Dp = TakiTheme.dimensions.contentInsetFloatingChrome,
) {
    val isRefreshing = state.isLoading && state.hasContent
    val firstLoad = state.isLoading && !state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Reserved strip so the first load never shifts the hero.
            Box(modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs)) {
                if (firstLoad) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
                        color = TakiTheme.colors.accent,
                        trackColor = TakiTheme.colors.surfaceLow,
                    )
                }
            }

            val listState = rememberLazyListState()
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = actions.onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = TakiTheme.colors.surface,
                        color = TakiTheme.colors.gray,
                    )
                },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag(PLAYLIST_DETAIL_LIST_TEST_TAG),
                    contentPadding = PaddingValues(bottom = bottomContentInset),
                ) {
                    item(key = "hero") { PlaylistDetailHero(state) }
                    item(key = "actions") { DetailActionRow(actions) }

                    if (state.showEmpty) {
                        item(key = "empty") {
                            EmptyState(
                                icon = painterResource(R.drawable.ic_empty),
                                title = stringResource(R.string.playlist_empty),
                                modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                            )
                        }
                    } else {
                        items(items = state.rows, key = { "track_${it.id}" }) { row ->
                            PlaylistTrackItem(row = row, isCurrent = row.id == currentTrackId, actions = actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailHero(state: PlaylistDetailUiState) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val artSize = (maxWidth - TakiTheme.spacing.screenHorizontal * 2)
            .coerceIn(
                TakiTheme.dimensions.albumHeroArtworkMin,
                TakiTheme.dimensions.albumHeroArtworkMax,
            )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(TakiTheme.spacing.lg))
            TakiArtwork(
                model = state.artworkModel,
                // The legacy album_detail_header_item.xml hardcodes this same "Album artwork"/
                // "Play this album"/"Shuffle this album" wording for playlists too -
                // AlbumDetailHeaderBinder is reused as-is there, with no playlist-specific
                // strings for these three (only the header's trailing action has one, "More
                // options" - see PlaylistDetailActions' kdoc). Preserved verbatim rather than
                // "fixed" - see the phase 4F3 report's disclosed pre-existing issues.
                contentDescription = stringResource(R.string.album_artwork_description),
                size = artSize,
                shape = TakiTheme.shapes.md,
            )
            Spacer(Modifier.height(TakiTheme.spacing.xl))
            Text(
                text = state.title,
                style = TakiTheme.type.hero,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TakiTheme.spacing.screenHorizontal)
                    .semantics { heading() },
            )
            HeroArtistAndMetadata(state)
        }
    }
}

/** The hero's artist line (plain text - never a navigation target, unlike Album Detail's own
 *  clickable one) and metadata line, extracted only to keep [PlaylistDetailHero] under detekt's
 *  `LongMethod` limit. */
@Composable
private fun HeroArtistAndMetadata(state: PlaylistDetailUiState) {
    if (state.artist.isNotEmpty()) {
        Spacer(Modifier.height(TakiTheme.spacing.xxs))
        Text(
            text = state.artist,
            style = TakiTheme.type.body,
            color = TakiTheme.colors.gray,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TakiTheme.spacing.screenHorizontal),
        )
    }
    val metadata = metadataLine(state)
    if (metadata.isNotEmpty()) {
        Spacer(Modifier.height(TakiTheme.spacing.xxs))
        Text(
            text = metadata,
            style = TakiTheme.type.caption,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TakiTheme.spacing.screenHorizontal),
        )
    }
}

@Composable
private fun DetailActionRow(actions: PlaylistDetailActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TakiTheme.spacing.screenHorizontal,
                end = TakiTheme.spacing.sm,
                top = TakiTheme.spacing.lg,
                bottom = TakiTheme.spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailPrimaryPlayButton(
            onClick = actions.onPlay,
            contentDescription = stringResource(R.string.album_play_description),
        )
        Spacer(Modifier.weight(1f))
        TakiIconButton(
            onClick = actions.onShuffle,
            painter = painterResource(R.drawable.media_shuffle),
            contentDescription = stringResource(R.string.album_shuffle_description),
        )
        TakiIconButton(
            onClick = actions.onShowHeaderMenu,
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.playlist_menu_description),
            iconSize = TakiTheme.dimensions.iconSm,
        )
    }
}

@Composable
private fun PlaylistTrackItem(
    row: PlaylistDetailRow,
    isCurrent: Boolean,
    actions: PlaylistDetailActions,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var menuState by remember { mutableStateOf(TrackContextMenuState()) }
    Box {
        TakiTrackRow(
            title = row.title,
            onClick = { actions.onTrackClick(row.id) },
            number = row.number,
            artist = row.artist,
            duration = row.duration,
            isCurrent = isCurrent,
            onLongClick = {
                menuState = actions.trackContextMenuState(row.id)
                menuExpanded = true
            },
        )
        TrackContextMenu(
            expanded = menuExpanded,
            menuState = menuState,
            onDismiss = { menuExpanded = false },
            onAction = { action ->
                menuExpanded = false
                actions.onTrackContextAction(row.id, action)
            },
        )
    }
}

/** One-for-one with the legacy `R.menu.context_menu_track_collection_playlist`: the base track
 *  menu plus "Remove from playlist", in the same order. */
@Composable
private fun TrackContextMenu(
    expanded: Boolean,
    menuState: TrackContextMenuState,
    onDismiss: () -> Unit,
    onAction: (TrackContextAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ContextMenuItem(R.string.common_play_now) { onAction(TrackContextAction.PLAY_NOW) }
        ContextMenuItem(R.string.common_play_next) { onAction(TrackContextAction.PLAY_NEXT) }
        ContextMenuItem(R.string.common_play_last) { onAction(TrackContextAction.PLAY_LAST) }
        ContextMenuItem(R.string.common_play_from_here) {
            onAction(TrackContextAction.PLAY_FROM_HERE)
        }
        ContextMenuItem(R.string.song_start_radio) { onAction(TrackContextAction.START_RADIO) }
        if (menuState.canAddToPlaylist) {
            ContextMenuItem(R.string.playlist_add_to_title) {
                onAction(TrackContextAction.ADD_TO_PLAYLIST)
            }
        }
        if (menuState.canDownload) {
            ContextMenuItem(R.string.common_download) { onAction(TrackContextAction.DOWNLOAD) }
        }
        if (menuState.canRemoveFromPlaylist) {
            ContextMenuItem(R.string.playlist_remove_from_playlist) {
                onAction(TrackContextAction.REMOVE_FROM_PLAYLIST)
            }
        }
        if (menuState.canDelete) {
            ContextMenuItem(R.string.common_delete) { onAction(TrackContextAction.DELETE) }
        }
    }
}

@Composable
private fun ContextMenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

@Composable
private fun metadataLine(state: PlaylistDetailUiState): String {
    val parts = buildList {
        state.year?.let { add(it) }
        if (state.songCount > 0) {
            add(pluralStringResource(R.plurals.n_songs, state.songCount, state.songCount))
        }
        state.totalDuration?.let { add(it) }
    }
    return parts.joinToString("  ·  ")
}

@Preview
@Composable
private fun PlaylistDetailScreenPreview() {
    TakiTheme {
        PlaylistDetailScreen(
            state = PlaylistDetailUiState(
                isLoading = false,
                title = "Road Trip",
                artist = "Various Artists",
                songCount = 2,
                totalDuration = "7:55",
                rows = persistentListOf(
                    PlaylistDetailRow("1", "1.", "So What", "Miles Davis", "4:03", false),
                    PlaylistDetailRow("2", "2.", "Take Five", "Dave Brubeck", "3:52", false),
                ),
            ),
            actions = PlaylistDetailActions.Noop,
            currentTrackId = "1",
        )
    }
}
