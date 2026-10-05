/*
 * AlbumInfoSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiSheet
import org.moire.ultrasonic.ui.theme.TakiTheme

const val ALBUM_INFO_SHEET_TEST_TAG = "album_info_sheet"
const val ALBUM_INFO_NAME_TEST_TAG = "album_info_name"
const val ALBUM_INFO_ARTIST_TEST_TAG = "album_info_artist"
const val ALBUM_INFO_SECONDARY_TEST_TAG = "album_info_secondary"
const val ALBUM_INFO_DESCRIPTION_TEST_TAG = "album_info_description"

private const val NAME_MAX_LINES = 2
private const val SECONDARY_SEPARATOR = " · "

/** Room reserved for the header block so header + description never exceed the sheet's cap. */
private val HEADER_ALLOWANCE = 120.dp // taki-raw-ok: one-off sheet sizing, not a spacing token

/**
 * What the Information sheet shows - already resolved by the host when the action was tapped, so
 * the sheet never loads or fails (the legacy `AlbumInfoBottomSheetFragment` contract, kept 1:1).
 */
data class AlbumInfoUiState(
    val albumName: String,
    val artist: String?,
    val year: String?,
    val songCount: Int,
    val discCount: Int,
    val description: String?,
    val artworkModel: Any?,
)

/**
 * The Compose replacement for `AlbumInfoBottomSheetFragment` (issue #10 phase 5A6): a sticky
 * header (artwork, album name, artist, "year · songs · discs") above a divider and a scrollable
 * description. Every legacy field and its hide-when-empty rule is preserved - no extra metadata,
 * links or actions were invented.
 */
@Composable
fun AlbumInfoSheet(
    info: AlbumInfoUiState,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    TakiSheet(
        title = stringResource(R.string.album_info_title),
        dismissLabel = stringResource(R.string.common_cancel),
        onDismiss = onDismiss,
        bottomContentInset = bottomContentInset,
        sheetTestTag = ALBUM_INFO_SHEET_TEST_TAG,
    ) { maxContentHeight ->
        AlbumInfoHeader(info)
        Spacer(Modifier.height(TakiTheme.spacing.md))
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(TakiTheme.dimensions.borderThin)
                .background(TakiTheme.colors.divider),
        )
        Text(
            text = info.description.orEmpty(),
            style = TakiTheme.type.body,
            color = TakiTheme.colors.gray,
            modifier = Modifier
                .testTag(ALBUM_INFO_DESCRIPTION_TEST_TAG)
                .fillMaxWidth()
                // The description is the only part that scrolls; the header above stays put.
                .heightIn(
                    max = (maxContentHeight - HEADER_ALLOWANCE)
                        .coerceAtLeast(TakiTheme.dimensions.touchTargetMin),
                )
                .verticalScroll(rememberScrollState())
                .padding(vertical = TakiTheme.spacing.lg),
        )
    }
}

/** "year · N songs · N discs" - the year if known, songs if > 0, discs only if more than one. */
@Composable
private fun albumInfoSecondaryLine(info: AlbumInfoUiState): String {
    val parts = listOfNotNull(
        info.year,
        if (info.songCount > 0) {
            pluralStringResource(R.plurals.n_songs, info.songCount, info.songCount)
        } else {
            null
        },
        if (info.discCount > 1) {
            pluralStringResource(R.plurals.n_discs, info.discCount, info.discCount)
        } else {
            null
        },
    )
    return parts.joinToString(SECONDARY_SEPARATOR)
}

/** The sticky header: artwork beside the album name, artist and "year · songs · discs". */
@Composable
private fun AlbumInfoHeader(info: AlbumInfoUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TakiArtwork(
            model = info.artworkModel,
            contentDescription = stringResource(R.string.album_artwork_description),
            size = TakiTheme.dimensions.artworkThumb,
        )
        Spacer(Modifier.width(TakiTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = info.albumName,
                style = TakiTheme.type.titleSmall,
                maxLines = NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(ALBUM_INFO_NAME_TEST_TAG),
            )
            if (!info.artist.isNullOrEmpty()) {
                Text(
                    text = info.artist,
                    style = TakiTheme.type.body,
                    color = TakiTheme.colors.gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = TakiTheme.spacing.xxs)
                        .testTag(ALBUM_INFO_ARTIST_TEST_TAG),
                )
            }
            val secondary = albumInfoSecondaryLine(info)
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary,
                    style = TakiTheme.type.caption,
                    color = TakiTheme.colors.gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = TakiTheme.spacing.xxs)
                        .testTag(ALBUM_INFO_SECONDARY_TEST_TAG),
                )
            }
        }
    }
}
