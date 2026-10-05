/*
 * AppErrorSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R

const val APP_ERROR_SHEET_TEST_TAG = "app_error_sheet"

/**
 * The Compose replacement for the app-owned `ErrorDialog` (`MaterialAlertDialogBuilder`) that
 * `CommunicationError.handleError` showed for a failed server/network action (issue #10 phase
 * 5A6): the localized "Error" title, the localized failure message, one OK button. Renders the
 * oldest pending message of [messages] (see `ErrorMessageChannel`), if any; [onDismiss] removes
 * that one message, so a second pending error then appears - the legacy stacked dialogs, one at a
 * time. Hosted by `NavigationActivity` above the nav host, not by any single screen, because the
 * errors it reports come from playback commands and shortcuts as well as from screens.
 */
@Composable
fun AppErrorSheet(
    messages: List<String>,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    val message = messages.firstOrNull() ?: return
    TakiMessageSheet(
        title = stringResource(R.string.error_label),
        message = AnnotatedString(message),
        okLabel = stringResource(R.string.common_ok),
        onDismiss = onDismiss,
        bottomContentInset = bottomContentInset,
        sheetTestTag = APP_ERROR_SHEET_TEST_TAG,
    )
}
