/*
 * ShareCardSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiBackHandler
import org.moire.ultrasonic.ui.components.TakiPrimaryButton
import org.moire.ultrasonic.ui.components.TakiSecondaryButton
import org.moire.ultrasonic.ui.components.takiSheetPanelTapSwallow
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the sheet and its parts. */
const val SHARE_SHEET_TEST_TAG = "share_sheet"
const val SHARE_SHEET_SCRIM_TEST_TAG = "share_sheet_scrim"
const val SHARE_PREVIEW_TEST_TAG = "share_preview"
const val SHARE_PREVIEW_LOADING_TEST_TAG = "share_preview_loading"
const val SHARE_ACTION_TEST_TAG = "share_action"
const val SHARE_ERROR_TEST_TAG = "share_error"
const val SHARE_RETRY_TEST_TAG = "share_retry"

private const val SHEET_ANIMATION_MS = 200
private const val SCRIM_ALPHA = 0.6f
private const val DRAG_HANDLE_ALPHA = 0.4f
private const val PREVIEW_HEIGHT_FRACTION = 0.5f
private const val SHEET_MAX_HEIGHT_FRACTION = 0.92f
private val SHEET_CORNER_RADIUS = 20.dp // taki-raw-ok: radius_lg's value, top corners only
private val PREVIEW_CORNER_RADIUS = 12.dp // taki-raw-ok: radius_md's value, preview thumbnail only
private const val CARD_ASPECT = ShareCardSpec.WIDTH.toFloat() / ShareCardSpec.HEIGHT

/** What the sheet can ask of its host - the host (`PlayerFragment`) owns the actual launch. */
data class ShareCardActions(
    val onShare: () -> Unit,
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        val Noop = ShareCardActions(onShare = {}, onRetry = {}, onDismiss = {})
    }
}

/**
 * The share preview (issue #20): a Taki bottom sheet showing the real generated card at 9:16, one
 * clear Share action, and a quiet loading / error state. The preview image is the very bitmap
 * that gets exported, so what the user inspects is what the receiving app gets. Dismissed by the
 * scrim, the Back button or the host; it never launches anything itself.
 */
@Composable
fun BoxScope.ShareCardSheet(
    state: ShareCardUiState,
    actions: ShareCardActions,
    modifier: Modifier = Modifier,
) {
    val visible = state !is ShareCardUiState.Hidden
    TakiBackHandler(enabled = visible, onBack = actions.onDismiss)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(SHEET_ANIMATION_MS)),
        exit = fadeOut(tween(SHEET_ANIMATION_MS)),
    ) {
        Box(Modifier.fillMaxSize()) {
            val dismissInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(SHARE_SHEET_SCRIM_TEST_TAG)
                    .background(TakiTheme.colors.black.copy(alpha = SCRIM_ALPHA))
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        onClickLabel = stringResource(R.string.common_navigate_back),
                        role = Role.Button,
                        onClick = actions.onDismiss,
                    ),
            )
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(SHEET_ANIMATION_MS)) { it },
                exit = slideOutVertically(tween(SHEET_ANIMATION_MS)) { it },
            ) {
                ShareCardSheetContent(state, actions)
            }
        }
    }
}

@Composable
private fun ShareCardSheetContent(state: ShareCardUiState, actions: ShareCardActions) {
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = SHEET_CORNER_RADIUS, topEnd = SHEET_CORNER_RADIUS))
            .background(TakiTheme.colors.surface)
            .testTag(SHARE_SHEET_TEST_TAG)
            .takiSheetPanelTapSwallow()
            .navigationBarsPadding()
            .padding(horizontal = TakiTheme.spacing.xl)
            .padding(top = TakiTheme.spacing.sm, bottom = TakiTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShareCardDragHandle()
        // Scrolls instead of clipping when a large system font pushes the button off screen.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * SHEET_MAX_HEIGHT_FRACTION)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TakiTheme.spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.share_card_title),
                style = TakiTheme.type.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TakiTheme.spacing.sm)
                    .semantics { heading() },
            )
            ShareCardPreview(state, screenHeight)
            ShareCardActionArea(state, actions)
            Spacer(Modifier.height(TakiTheme.spacing.xs))
        }
    }
}

/** Purely decorative: the scrim tap and system Back already dismiss the sheet. */
@Composable
private fun ShareCardDragHandle() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = TakiTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(TakiTheme.spacing.xxl)
                .height(TakiTheme.spacing.xs)
                .clip(TakiTheme.shapes.xs)
                .background(TakiTheme.colors.gray.copy(alpha = DRAG_HANDLE_ALPHA)),
        )
    }
}

/** The one primary Share action, or - when the card failed - the message and a retry. */
@Composable
private fun ShareCardActionArea(state: ShareCardUiState, actions: ShareCardActions) {
    if (state is ShareCardUiState.Error) {
        Text(
            text = stringResource(R.string.share_card_error),
            style = TakiTheme.type.body,
            color = TakiTheme.colors.error,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag(SHARE_ERROR_TEST_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        TakiSecondaryButton(
            text = stringResource(R.string.share_card_retry),
            onClick = actions.onRetry,
            modifier = Modifier.fillMaxWidth().testTag(SHARE_RETRY_TEST_TAG),
        )
    } else {
        TakiPrimaryButton(
            text = stringResource(R.string.share_card_action),
            onClick = actions.onShare,
            enabled = state is ShareCardUiState.Ready,
            modifier = Modifier.fillMaxWidth().testTag(SHARE_ACTION_TEST_TAG),
        )
    }
}

@Composable
private fun ShareCardPreview(state: ShareCardUiState, screenHeight: Dp) {
    val height = screenHeight * PREVIEW_HEIGHT_FRACTION
    val shape = RoundedCornerShape(PREVIEW_CORNER_RADIUS)
    val frame = Modifier.height(height).aspectRatio(CARD_ASPECT).clip(shape)
    when (state) {
        is ShareCardUiState.Ready -> {
            val description = stringResource(R.string.share_card_preview_description, state.request.content.describe())
            val image = remember(state.preview) { state.preview.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = frame.testTag(SHARE_PREVIEW_TEST_TAG),
            )
        }
        else -> {
            val preparing = stringResource(R.string.share_card_preparing)
            Box(
                frame
                    .background(TakiTheme.colors.surfaceHigh)
                    .testTag(SHARE_PREVIEW_LOADING_TEST_TAG)
                    .semantics { if (state is ShareCardUiState.Generating) contentDescription = preparing },
                contentAlignment = Alignment.Center,
            ) {
                if (state is ShareCardUiState.Generating) {
                    Text(text = preparing, style = TakiTheme.type.caption, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
