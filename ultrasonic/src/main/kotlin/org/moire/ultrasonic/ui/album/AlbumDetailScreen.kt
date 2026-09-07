/*
 * AlbumDetailScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiDiscHeader
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiTrackRow
import org.moire.ultrasonic.ui.theme.TakiTheme

const val ALBUM_DETAIL_LIST_TEST_TAG = "album_detail_list"

/**
 * Album Detail (issue #10 phase 4A) - the first Compose implementation of Taki's reusable
 * detail-screen language (TAKI_DESIGN_SYSTEM_V2.md §1 hierarchy, §7 artwork, §12 for the hero
 * proportions). Artwork-first and calm: a centred responsive hero cover, the album title, a
 * tappable artist line (issue #16), a single quiet metadata line, then one obviously-primary
 * Play action with the rest receding, and a compact transparent track list underneath. One
 * [LazyColumn]; no collapsing toolbar. The Activity chrome supplies the back affordance, as it
 * already does for the legacy screen.
 *
 * [currentTrackId] is the id of the track playback is on (from `PlaybackUiStateHolder`), used
 * only to mark one row - the list is keyed by track id so a change recomposes just the two
 * affected rows. [bottomContentInset] is the live floating-chrome reserve, same as the other
 * screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    state: AlbumDetailUiState,
    actions: AlbumDetailActions,
    currentTrackId: String?,
    modifier: Modifier = Modifier,
    bottomContentInset: Dp = TakiTheme.dimensions.contentInsetFloatingChrome,
) {
    // "Loading" while the hero already has content is a pull-to-refresh; the very first load
    // uses the 2dp strip instead, so the two indicators never overlap.
    val isRefreshing = state.isLoading && state.hasContent
    val firstLoad = state.isLoading && !state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Reserved strip so the first load never shifts the hero.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TakiTheme.spacing.xxs),
            ) {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(ALBUM_DETAIL_LIST_TEST_TAG),
                    contentPadding = PaddingValues(bottom = bottomContentInset),
                ) {
                    item(key = "hero") { AlbumDetailHero(state, actions) }
                    item(key = "actions") { DetailActionRow(state, actions) }

                    if (state.showEmpty) {
                        item(key = "empty") {
                            EmptyState(
                                icon = painterResource(R.drawable.ic_menu_search),
                                title = stringResource(R.string.select_album_empty),
                                modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                            )
                        }
                    } else {
                        trackRows(state, actions, currentTrackId)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailHero(state: AlbumDetailUiState, actions: AlbumDetailActions) {
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
            if (state.artist.isNotEmpty()) {
                Spacer(Modifier.height(TakiTheme.spacing.xxs))
                ArtistLine(state, actions)
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
    }
}

@Composable
private fun ArtistLine(state: AlbumDetailUiState, actions: AlbumDetailActions) {
    val base = Modifier
        .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin)
        .wrapContentHeight(Alignment.CenterVertically)
    Text(
        text = state.artist,
        style = TakiTheme.type.body,
        color = if (state.artistId != null) TakiTheme.colors.ivory else TakiTheme.colors.gray,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (state.artistId != null) {
            base.clickable(role = Role.Button, onClick = actions.onArtistClick)
                .padding(horizontal = TakiTheme.spacing.screenHorizontal)
        } else {
            base.padding(horizontal = TakiTheme.spacing.screenHorizontal)
        },
    )
}

@Composable
private fun DetailActionRow(state: AlbumDetailUiState, actions: AlbumDetailActions) {
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
        PrimaryPlayButton(onClick = actions.onPlay)
        Spacer(Modifier.weight(1f))
        TakiIconButton(
            onClick = actions.onShuffle,
            painter = painterResource(R.drawable.media_shuffle),
            contentDescription = stringResource(R.string.album_shuffle_description),
        )
        if (state.starVisible) {
            StarAction(isStarred = state.isStarred, onToggle = actions.onToggleStar)
        }
        TakiIconButton(
            onClick = actions.onDownload,
            painter = painterResource(R.drawable.ic_menu_download),
            contentDescription = stringResource(R.string.album_download_description),
            iconSize = TakiTheme.dimensions.iconSm,
        )
        if (state.infoAvailable) {
            TakiIconButton(
                onClick = actions.onShowInfo,
                painter = painterResource(R.drawable.ic_info_outline),
                contentDescription = stringResource(R.string.album_info_description),
                iconSize = TakiTheme.dimensions.iconSm,
            )
        }
        OverflowAction(state, actions)
    }
}

/** V2 §2.3 / §6: an ivory circle with a dark glyph - not a promotional green fill. */
@Composable
private fun PrimaryPlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(TakiTheme.dimensions.detailPrimaryAction)
            .background(TakiTheme.colors.ivory, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.media_start),
            contentDescription = stringResource(R.string.album_play_description),
            tint = TakiTheme.colors.black,
            modifier = Modifier.size(TakiTheme.dimensions.iconLg),
        )
    }
}

/** The album heart (issue #15): rose when starred, neutral gray otherwise. */
@Composable
private fun StarAction(isStarred: Boolean, onToggle: (Boolean) -> Unit) {
    TakiIconButton(
        onClick = { onToggle(!isStarred) },
        painter = painterResource(
            if (isStarred) R.drawable.rating_heart_full else R.drawable.rating_heart_hollow,
        ),
        contentDescription = stringResource(
            if (isStarred) R.string.album_unstar_description else R.string.album_star_description,
        ),
        iconSize = TakiTheme.dimensions.iconSm,
        selected = isStarred,
        tint = if (isStarred) TakiTheme.colors.liked else TakiTheme.colors.gray,
    )
}

@Composable
private fun OverflowAction(state: AlbumDetailUiState, actions: AlbumDetailActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TakiIconButton(
            onClick = { expanded = true },
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.album_more_description),
            iconSize = TakiTheme.dimensions.iconSm,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (state.artistId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.album_go_to_artist)) },
                    onClick = {
                        expanded = false
                        actions.onOverflowItem(AlbumOverflowItem.GO_TO_ARTIST)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_play_next)) },
                onClick = {
                    expanded = false
                    actions.onOverflowItem(AlbumOverflowItem.PLAY_NEXT)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_play_last)) },
                onClick = {
                    expanded = false
                    actions.onOverflowItem(AlbumOverflowItem.PLAY_LAST)
                },
            )
            if (state.radioAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.song_start_radio)) },
                    onClick = {
                        expanded = false
                        actions.onOverflowItem(AlbumOverflowItem.START_RADIO)
                    },
                )
            }
        }
    }
}

private fun LazyListScope.trackRows(
    state: AlbumDetailUiState,
    actions: AlbumDetailActions,
    currentTrackId: String?,
) {
    items(
        items = state.rows,
        key = { row ->
            when (row) {
                is AlbumDetailRow.Disc -> "disc_${row.number}"
                is AlbumDetailRow.Track -> "track_${row.id}"
            }
        },
    ) { row ->
        when (row) {
            is AlbumDetailRow.Disc -> TakiDiscHeader(
                label = stringResource(R.string.album_disc_header, row.number),
                onPlay = { actions.onDiscPlay(row.number) },
                onDownload = { actions.onDiscDownload(row.number) },
                playContentDescription = stringResource(R.string.album_play_disc_description),
                downloadContentDescription =
                stringResource(R.string.album_download_disc_description),
            )

            is AlbumDetailRow.Track -> AlbumTrackItem(
                row = row,
                isCurrent = row.id == currentTrackId,
                actions = actions,
            )
        }
    }
}

/**
 * One track row plus its ephemeral long-press context menu. The menu's open/close and the
 * resolved [TrackContextMenuState] are per-row `remember` (interaction state, never
 * [AlbumDetailUiState]); every item routes back through [AlbumDetailActions] to the
 * unchanged `ContextMenuUtil` / add-to-playlist paths.
 */
@Composable
private fun AlbumTrackItem(
    row: AlbumDetailRow.Track,
    isCurrent: Boolean,
    actions: AlbumDetailActions,
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
private fun metadataLine(state: AlbumDetailUiState): String {
    val parts = buildList {
        state.year?.let { add(it) }
        state.genre?.let { add(it) }
        if (state.songCount > 0) {
            add(pluralStringResource(R.plurals.n_songs, state.songCount, state.songCount))
        }
        state.totalDuration?.let { add(it) }
    }
    return parts.joinToString("  ·  ")
}

@Preview
@Composable
private fun AlbumDetailScreenPreview() {
    TakiTheme {
        AlbumDetailScreen(
            state = AlbumDetailUiState(
                isLoading = false,
                title = "Goldberg Variations, BWV 988",
                artist = "Johann Sebastian Bach",
                artistId = "ar1",
                year = "1981",
                genre = "Classical",
                songCount = 3,
                totalDuration = "51:15",
                starVisible = true,
                isStarred = true,
                rows = persistentListOf(
                    AlbumDetailRow.Track("1", "1", "Aria", null, "4:03", false),
                    AlbumDetailRow.Track("2", "2", "Variatio 1 a 1 Clav.", null, "1:52", false),
                    AlbumDetailRow.Track("3", "3", "Variatio 2 a 1 Clav.", null, "1:29", false),
                ),
            ),
            actions = AlbumDetailActions.Noop,
            currentTrackId = "2",
        )
    }
}
