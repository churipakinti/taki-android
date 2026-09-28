/*
 * TakiTextField.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A single-line, labelled Compose text field in Taki's own palette (issue #10 phase 4M3) - a
 * Material3 [OutlinedTextField] with every color remapped to Taki tokens (ivory text/border-on-
 * focus, gray label/border-at-rest, the shared error color) rather than Material's defaults, so it
 * reads as part of the same dark surface every other Taki sheet/screen uses. Used by
 * [org.moire.ultrasonic.ui.player.SavePlaylistSheet] and
 * [org.moire.ultrasonic.ui.playlistlist.CreatePlaylistNameSheet] - both need a labelled field with
 * an inline error, which [org.moire.ultrasonic.ui.components.TakiSearchField] (hint-only, no
 * label/error, Search-specific styling) doesn't offer.
 */
@Composable
fun TakiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = errorMessage?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onGo = { onImeAction() }),
        textStyle = TakiTheme.type.body.copy(color = TakiTheme.colors.ivory),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TakiTheme.colors.ivory,
            unfocusedTextColor = TakiTheme.colors.ivory,
            focusedBorderColor = TakiTheme.colors.accent,
            unfocusedBorderColor = TakiTheme.colors.gray,
            focusedLabelColor = TakiTheme.colors.accent,
            unfocusedLabelColor = TakiTheme.colors.gray,
            cursorColor = TakiTheme.colors.accent,
            errorBorderColor = TakiTheme.colors.error,
            errorLabelColor = TakiTheme.colors.error,
            errorSupportingTextColor = TakiTheme.colors.error,
            errorCursorColor = TakiTheme.colors.error,
        ),
    )
}
