/*
 * DownloadsScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiEntryRow
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the scrollable list regardless of loading/empty state. */
const val DOWNLOADS_CONTENT_TEST_TAG = "downloads_content"

/**
 * The Compose Downloads screen (issue #10 phase 4G3): the "Downloads" title, a list of
 * downloaded albums (cover, title, artist, local song count) each with a trash button that
 * removes its downloaded tracks, pull-to-refresh and the empty state - a 1:1 port of the legacy
 * `DownloadsFragment`. Draws **no back button**: `NavigationActivity` already hides the shared
 * Material toolbar for `downloadsFragment` and shows its shared `content_navigation_header`
 * back-only bar instead (unchanged by this phase). The title is drawn here once, as the legacy
 * in-layout headline was.
 */
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    actions: DownloadsActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    val isRefreshing = state.isLoading && state.hasContent
    val firstLoad = state.isLoading && !state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs)) {
                if (firstLoad) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
                        color = TakiTheme.colors.accent,
                        trackColor = TakiTheme.colors.surfaceLow,
                    )
                }
            }

            Text(
                text = stringResource(R.string.menu_downloads),
                style = TakiTheme.type.hero,
                modifier = Modifier.padding(
                    start = TakiTheme.spacing.md,
                    end = TakiTheme.spacing.md,
                    top = TakiTheme.spacing.md,
                    bottom = TakiTheme.spacing.sm,
                ),
            )

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
                if (state.showEmpty) {
                    EmptyState(
                        icon = painterResource(R.drawable.ic_menu_download),
                        title = stringResource(R.string.download_empty),
                        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().testTag(DOWNLOADS_CONTENT_TEST_TAG),
                        contentPadding = PaddingValues(bottom = bottomContentInset),
                    ) {
                        items(state.rows, key = { it.id }) { row ->
                            DownloadedAlbumItem(row, actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedAlbumItem(row: DownloadedAlbumRow, actions: DownloadsActions) {
    TakiEntryRow(
        title = row.title,
        artworkModel = row.artworkModel,
        subtitle = row.artist,
        caption = pluralStringResource(R.plurals.n_songs, row.songCount, row.songCount),
        onClick = { actions.onAlbumClick(row) },
        trailing = {
            TakiIconButton(
                onClick = { actions.onRemoveClick(row) },
                painter = painterResource(R.drawable.ic_menu_remove_all),
                contentDescription = stringResource(R.string.album_remove_download_description),
                iconSize = TakiTheme.dimensions.iconSm,
            )
        },
    )
}
