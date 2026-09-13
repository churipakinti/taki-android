/*
 * ArtistDetailScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.moire.ultrasonic.ui.components.AlbumShelfItem
import org.moire.ultrasonic.ui.components.DetailPrimaryPlayButton
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.components.TakiSectionHeader
import org.moire.ultrasonic.ui.components.TakiTrackRow
import org.moire.ultrasonic.ui.theme.TakiTheme

const val ARTIST_DETAIL_LIST_TEST_TAG = "artist_detail_list"
const val ARTIST_HERO_NAME_TEST_TAG = "artist_hero_name"

private const val BIOGRAPHY_COLLAPSED_LINES = 5

/**
 * Artist Detail (issue #10 phase 4C) - a sibling of Album Detail in Taki's detail language, not
 * a copy. A lightweight back row on the dark canvas, a centred artist-identity hero
 * (`artist_hero_artwork` 220dp, `radius_md`), the artist name once, one obviously-primary Play
 * with Radio + Download receding, then the artist's own content: a "Popular" preview (up to
 * [ARTIST_POPULAR_TRACK_COUNT] tracks), the album shelf, an "About" biography, and a
 * similar-artists shelf. One [LazyColumn]; no collapsing toolbar.
 *
 * [bottomContentInset] is the live floating-chrome reserve, same as the other detail screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    state: ArtistDetailUiState,
    actions: ArtistDetailActions,
    modifier: Modifier = Modifier,
    bottomContentInset: Dp = TakiTheme.dimensions.contentInsetFloatingChrome,
) {
    val isRefreshing = state.isLoading && state.hasContent
    val firstLoad = state.isLoading && !state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TakiScreenHeader(onBack = actions.onBack)

            Box(
                modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
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
                    modifier = Modifier.fillMaxSize().testTag(ARTIST_DETAIL_LIST_TEST_TAG),
                    contentPadding = PaddingValues(bottom = bottomContentInset),
                ) {
                    item(key = "hero") { ArtistHero(state) }
                    item(key = "actions") { ArtistActionRow(actions) }

                    if (state.showEmpty) {
                        item(key = "empty") {
                            EmptyState(
                                icon = painterResource(R.drawable.ic_artist),
                                title = stringResource(R.string.artist_empty),
                                modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                            )
                        }
                    }

                    popularSection(state, actions)
                    albumsSection(state, actions)
                    aboutSection(state)
                    similarSection(state, actions)
                }
            }
        }
    }
}

@Composable
private fun ArtistHero(state: ArtistDetailUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        TakiArtwork(
            model = state.artworkModel,
            contentDescription = stringResource(R.string.artist_artwork_description),
            size = TakiTheme.dimensions.artistHeroArtwork,
            shape = TakiTheme.shapes.md,
            placeholder = painterResource(R.drawable.artist_placeholder_icon),
        )
        Spacer(Modifier.height(TakiTheme.spacing.xl))
        Text(
            text = state.artistName,
            style = TakiTheme.type.hero,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TakiTheme.spacing.screenHorizontal)
                .testTag(ARTIST_HERO_NAME_TEST_TAG)
                .semantics { heading() },
        )
        if (state.albumCount > 0) {
            Spacer(Modifier.height(TakiTheme.spacing.xxs))
            Text(
                text = pluralStringResource(
                    R.plurals.artist_album_count,
                    state.albumCount,
                    state.albumCount,
                ),
                style = TakiTheme.type.caption,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** Primary Play left, Radio + Download receding right - the Album Detail action pattern. */
@Composable
private fun ArtistActionRow(actions: ArtistDetailActions) {
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
            contentDescription = stringResource(R.string.artist_play_description),
        )
        Spacer(Modifier.weight(1f))
        TakiIconButton(
            onClick = actions.onRadio,
            painter = painterResource(R.drawable.ic_radio),
            contentDescription = stringResource(R.string.artist_radio_description),
            iconSize = TakiTheme.dimensions.iconSm,
        )
        TakiIconButton(
            onClick = actions.onDownload,
            painter = painterResource(R.drawable.ic_menu_download),
            contentDescription = stringResource(R.string.artist_download_description),
            iconSize = TakiTheme.dimensions.iconSm,
        )
    }
}

@Composable
private fun ArtistSectionHeader(title: String) {
    TakiSectionHeader(
        title = title,
        modifier = Modifier.padding(
            start = TakiTheme.spacing.screenHorizontal,
            end = TakiTheme.spacing.screenHorizontal,
            top = TakiTheme.spacing.sectionGap,
            bottom = TakiTheme.spacing.sm,
        ),
    )
}

private fun LazyListScope.popularSection(
    state: ArtistDetailUiState,
    actions: ArtistDetailActions,
) {
    if (state.popularTracks.isEmpty()) return
    item(key = "popular_header") {
        ArtistSectionHeader(stringResource(R.string.artist_popular))
    }
    items(state.popularTracks, key = { "track_${it.id}" }) { track ->
        TakiTrackRow(
            title = track.title,
            onClick = { actions.onTrackClick(track.id) },
            number = track.rank,
            artist = track.subtitle,
            duration = track.duration,
        )
    }
}

private fun LazyListScope.albumsSection(
    state: ArtistDetailUiState,
    actions: ArtistDetailActions,
) {
    if (state.albums.isEmpty()) return
    item(key = "albums_header") {
        ArtistSectionHeader(stringResource(R.string.artist_albums))
    }
    item(key = "albums_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = TakiTheme.spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md),
        ) {
            items(state.albums, key = { it.id }) { album ->
                AlbumShelfItem(
                    title = album.title,
                    subtitle = album.subtitle,
                    artworkModel = album.artworkModel,
                    onClick = { actions.onAlbumClick(album) },
                )
            }
        }
    }
}

private fun LazyListScope.aboutSection(state: ArtistDetailUiState) {
    val biography = state.biography
    if (biography.isNullOrEmpty()) return
    item(key = "about_header") {
        ArtistSectionHeader(stringResource(R.string.artist_about))
    }
    item(key = "about_body") {
        BiographyBlock(text = biography, collapsible = state.biographyCollapsible)
    }
}

private fun LazyListScope.similarSection(
    state: ArtistDetailUiState,
    actions: ArtistDetailActions,
) {
    if (state.similarArtists.isEmpty()) return
    item(key = "similar_header") {
        ArtistSectionHeader(stringResource(R.string.artist_similar))
    }
    item(key = "similar_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = TakiTheme.spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md),
        ) {
            items(state.similarArtists, key = { it.id }) { similar ->
                SimilarArtistItem(
                    name = similar.name,
                    artworkModel = similar.artworkModel,
                    onClick = { actions.onSimilarArtistClick(similar) },
                )
            }
        }
    }
}

@Composable
private fun BiographyBlock(text: String, collapsible: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = TakiTheme.spacing.screenHorizontal)) {
        Text(
            text = text,
            style = TakiTheme.type.body,
            color = TakiTheme.colors.gray,
            maxLines = if (expanded) Int.MAX_VALUE else BIOGRAPHY_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        if (collapsible) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(vertical = TakiTheme.spacing.xs),
            ) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.artist_show_less else R.string.artist_show_more,
                    ),
                    style = TakiTheme.type.titleSmall,
                    color = TakiTheme.colors.ivory,
                )
            }
        }
    }
}

@Composable
private fun SimilarArtistItem(name: String, artworkModel: Any?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(TakiTheme.dimensions.artworkShelfCompact)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TakiArtwork(
            model = artworkModel,
            contentDescription = null,
            size = TakiTheme.dimensions.artworkShelfCompact,
            shape = TakiTheme.shapes.circle,
            placeholder = painterResource(R.drawable.artist_placeholder_icon),
        )
        Spacer(Modifier.height(TakiTheme.spacing.sm))
        Text(
            text = name,
            style = TakiTheme.type.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun ArtistDetailScreenPreview() {
    TakiTheme {
        ArtistDetailScreen(
            state = ArtistDetailUiState(
                isLoading = false,
                artistName = "Héroes del Silencio",
                albumCount = 3,
                biography = "A Spanish rock band formed in Zaragoza in 1984.",
                albums = persistentListOf(
                    ArtistAlbumUi("1", null, "El Espíritu del Vino", "Héroes del Silencio", null),
                    ArtistAlbumUi("2", null, "Senderos de Traición", "Héroes del Silencio", null),
                ),
                popularTracks = persistentListOf(
                    ArtistTrackUi("t1", "1", "Entre dos Tierras", "Senderos de Traición", "5:36", false),
                    ArtistTrackUi("t2", "2", "Maldito Duende", "Senderos de Traición", "4:53", false),
                ),
            ),
            actions = ArtistDetailActions.Noop,
        )
    }
}
