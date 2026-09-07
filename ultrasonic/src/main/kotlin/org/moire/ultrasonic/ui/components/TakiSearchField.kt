/*
 * TakiSearchField.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * Taki's search input (V2 section 11 / north star): a full-width, ~52dp, 12dp-radius quiet
 * `surfaceLow` container - not a Material `TextField`, so no bright outline, no shadow, no
 * accent border. A receding leading magnifier, ivory input on a gray hint, and a trailing
 * clear control that only appears while the query is non-empty. The keyboard shows a Search
 * action that calls [onSubmit] and drops focus.
 *
 * Focus is the caller's/OS's job - this does not auto-request focus (matches the legacy
 * screen, which opens unfocused on the recent-searches list).
 */
@Composable
fun TakiSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = stringResource(R.string.search_field_hint),
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.searchFieldHeight)
            .clip(TakiTheme.shapes.md)
            .background(TakiTheme.colors.surfaceLow),
        textStyle = TakiTheme.type.body.copy(color = TakiTheme.colors.ivory),
        singleLine = true,
        cursorBrush = SolidColor(TakiTheme.colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSubmit()
                focusManager.clearFocus()
            },
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TakiTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu_search),
                    contentDescription = null,
                    tint = TakiTheme.colors.gray,
                    modifier = Modifier.size(TakiTheme.dimensions.iconMd),
                )
                Spacer(Modifier.width(TakiTheme.spacing.sm))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = hint,
                            style = TakiTheme.type.body,
                            color = TakiTheme.colors.gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    TakiIconButton(
                        onClick = onClear,
                        painter = painterResource(R.drawable.ic_menu_close),
                        contentDescription = stringResource(R.string.search_clear_field),
                        iconSize = TakiTheme.dimensions.iconSm,
                    )
                }
            }
        },
    )
}
