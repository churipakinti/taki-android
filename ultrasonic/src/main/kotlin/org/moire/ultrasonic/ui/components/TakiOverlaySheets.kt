/*
 * TakiOverlaySheets.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.ui.theme.TakiTheme


fun takiPickerOptionTestTag(key: String) = "taki_picker_option_$key"

const val TAKI_SHEET_PRIMARY_ACTION_TEST_TAG = "taki_sheet_primary_action"
const val TAKI_SHEET_DISMISS_ACTION_TEST_TAG = "taki_sheet_dismiss_action"
const val TAKI_PICKER_LIST_TEST_TAG = "taki_picker_list"
const val TAKI_SHEET_EMPTY_TEXT_TEST_TAG = "taki_sheet_empty_text"

/**
 * A scrollable list of plain, labelled rows - the Compose replacement for the legacy
 * `ItemSelectionDialogFragment` (`MaterialAlertDialogBuilder.setItems`). Selecting a row reports
 * its [TakiPickerOption.key] exactly once ([onSelect] cannot fire twice from a double tap) and
 * dismissing (scrim/Back) reports nothing. Rows are at least 48dp tall.
 */
@Composable
fun TakiPickerSheet(
    title: String,
    options: List<TakiPickerOption>,
    onSelect: (key: String) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String,
    bottomContentInset: Dp,
    sheetTestTag: String,
    modifier: Modifier = Modifier,
) {
    var consumed by remember { mutableStateOf(false) }
    TakiSheet(
        title = title,
        dismissLabel = dismissLabel,
        onDismiss = onDismiss,
        bottomContentInset = bottomContentInset,
        sheetTestTag = sheetTestTag,
        modifier = modifier,
    ) { maxContentHeight ->
        LazyColumn(Modifier.heightIn(max = maxContentHeight).testTag(TAKI_PICKER_LIST_TEST_TAG)) {
            items(options, key = { it.key }) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TakiTheme.dimensions.touchTargetMin)
                        .testTag(takiPickerOptionTestTag(option.key))
                        .clickable(role = Role.Button) {
                            if (!consumed) {
                                consumed = true
                                onSelect(option.key)
                            }
                        }
                        .padding(vertical = TakiTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.label,
                        style = TakiTheme.type.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A two-button confirmation: [onConfirm] fires at most once, dismissing (Cancel, scrim, Back)
 * never confirms. The confirm button is the accent button, labelled explicitly by the caller
 * ("Delete", "OK", ...) so a destructive action is never an anonymous "Yes".
 */
@Composable
fun TakiConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
    sheetTestTag: String,
    modifier: Modifier = Modifier,
) {
    var consumed by remember { mutableStateOf(false) }
    TakiSheet(
        title = title,
        dismissLabel = dismissLabel,
        onDismiss = onDismiss,
        bottomContentInset = bottomContentInset,
        sheetTestTag = sheetTestTag,
        modifier = modifier,
    ) { maxContentHeight ->
        Text(
            text = message,
            style = TakiTheme.type.body,
            modifier = Modifier
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        TakiSheetActions(
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onConfirm = {
                if (!consumed) {
                    consumed = true
                    onConfirm()
                }
            },
            onDismiss = onDismiss,
        )
    }
}

/**
 * A read-only message with a single acknowledge button (the legacy `InfoDialog`/`ErrorDialog`
 * shape). [message] may carry link annotations (see `linkifyWebUrls`). Long text scrolls.
 */
@Composable
fun TakiMessageSheet(
    title: String,
    message: AnnotatedString,
    okLabel: String,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
    sheetTestTag: String,
    modifier: Modifier = Modifier,
) {
    TakiSheet(
        title = title,
        dismissLabel = okLabel,
        onDismiss = onDismiss,
        bottomContentInset = bottomContentInset,
        sheetTestTag = sheetTestTag,
        modifier = modifier,
    ) { maxContentHeight ->
        Text(
            text = message,
            style = TakiTheme.type.body,
            modifier = Modifier
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .testTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG)
                    .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TakiTheme.colors.accent,
                    contentColor = TakiTheme.colors.onAccent,
                ),
            ) {
                Text(text = okLabel, style = TakiTheme.type.titleSmall)
            }
        }
    }
}

/** The shared Cancel + accent primary button row of the confirm/form sheets. */
@Composable
fun TakiSheetActions(
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .testTag(TAKI_SHEET_DISMISS_ACTION_TEST_TAG)
                .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
        ) {
            Text(text = dismissLabel, style = TakiTheme.type.titleSmall)
        }
        Spacer(Modifier.width(TakiTheme.spacing.sm))
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .testTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG)
                .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
            colors = ButtonDefaults.buttonColors(
                containerColor = TakiTheme.colors.accent,
                contentColor = TakiTheme.colors.onAccent,
            ),
        ) {
            Text(text = confirmLabel, style = TakiTheme.type.titleSmall)
        }
    }
}
