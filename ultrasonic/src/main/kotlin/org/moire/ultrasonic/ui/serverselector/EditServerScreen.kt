/*
 * EditServerScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.components.TakiTextField
import org.moire.ultrasonic.ui.theme.TakiTheme

private const val ADVANCED_CHEVRON_EXPANDED_ROTATION = 180f
private const val ADVANCED_ANIMATION_MS = 180

/**
 * Edit Server (issue #10 phase 5A3): a single screen that renders either the simple
 * first-connection onboarding form ([EditServerMode.New]) or the full existing-server editor
 * ([EditServerMode.Existing]), mirroring the legacy `EditServerFragment`'s own single-layout,
 * mode-branched approach rather than two separate screens - the shared validation/connection/
 * color-swatch pieces are identical either way. Draws its own [TakiScreenHeader]; both it and
 * system Back route through [EditServerActions.onBack] (`EditServerViewModel.requestBack()`),
 * fixing the legacy screen's gap where only system Back triggered the leave-confirmation.
 */
@Composable
fun EditServerScreen(
    state: EditServerUiState,
    actions: EditServerActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    TakiScaffold(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TakiScreenHeader(
                    onBack = actions.onBack,
                    title = stringResource(
                        if (state.isNewMode) R.string.server_editor_new_label else R.string.server_editor_label,
                    ),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = TakiTheme.spacing.lg),
                ) {
                    Spacer(Modifier.height(TakiTheme.spacing.md))
                    if (!state.isLoading) {
                        if (!state.isNewMode) {
                            LibrarySection(state, actions)
                            Spacer(Modifier.height(TakiTheme.spacing.xl))
                            AppearanceSection(state, actions)
                            Spacer(Modifier.height(TakiTheme.spacing.xl))
                            AdvancedSection(state, actions)
                        } else {
                            OnboardingFields(state, actions)
                        }
                        Spacer(Modifier.height(TakiTheme.spacing.md))
                        ConnectionStatusRow(state)
                        Spacer(Modifier.height(TakiTheme.spacing.lg))
                        ActionRow(state, actions, bottomContentInset)
                    }
                }
            }

            DiscardServerChangesSheet(
                visible = state.pendingDiscard,
                onDiscard = actions.onDiscardConfirm,
                onCancel = actions.onDiscardCancel,
                bottomContentInset = bottomContentInset,
            )
        }
    }
}

@Composable
private fun OnboardingFields(state: EditServerUiState, actions: EditServerActions) {
    AddressField(state, actions)
    Spacer(Modifier.height(TakiTheme.spacing.md))
    UsernameField(state, actions)
    Spacer(Modifier.height(TakiTheme.spacing.md))
    PasswordField(state, actions, onImeAction = actions.onConnectOrSave)
}

@Composable
private fun LibrarySection(state: EditServerUiState, actions: EditServerActions) {
    TakiTextField(
        value = state.name,
        onValueChange = actions.onNameChange,
        label = stringResource(R.string.settings_server_name),
        imeAction = ImeAction.Next,
    )
    Spacer(Modifier.height(TakiTheme.spacing.md))
    AddressField(state, actions)
    Spacer(Modifier.height(TakiTheme.spacing.lg))
    SectionHeader(stringResource(R.string.server_editor_authentication))
    UsernameField(state, actions)
    Spacer(Modifier.height(TakiTheme.spacing.md))
    PasswordField(state, actions)
}

@Composable
private fun AddressField(state: EditServerUiState, actions: EditServerActions) {
    // onFocusChanged reports the field's initial (unfocused) state once on composition, not just
    // real focus transitions - without this guard, onAddressFocusLost() would fire the moment the
    // screen opens, trimming the untouched "http://" seed down to "http:" and marking the form
    // dirty before the user has touched anything.
    var wasFocused by remember { mutableStateOf(false) }
    TakiTextField(
        value = state.address,
        onValueChange = actions.onAddressChange,
        label = stringResource(R.string.settings_server_address),
        isError = state.addressError != null,
        errorMessage = state.addressError?.let { stringResource(it.toStringRes()) },
        imeAction = ImeAction.Next,
        keyboardType = KeyboardType.Uri,
        onFocusChanged = { focusState ->
            if (wasFocused && !focusState.isFocused) actions.onAddressFocusLost()
            wasFocused = focusState.isFocused
        },
    )
}

@Composable
private fun UsernameField(state: EditServerUiState, actions: EditServerActions) {
    TakiTextField(
        value = state.username,
        onValueChange = actions.onUsernameChange,
        label = stringResource(R.string.settings_server_username),
        isError = state.usernameError != null,
        errorMessage = state.usernameError?.let { stringResource(it.toStringRes()) },
        imeAction = ImeAction.Next,
    )
}

@Composable
private fun PasswordField(
    state: EditServerUiState,
    actions: EditServerActions,
    onImeAction: () -> Unit = {},
) {
    TakiTextField(
        value = state.password,
        onValueChange = actions.onPasswordChange,
        label = stringResource(R.string.settings_server_password),
        imeAction = ImeAction.Done,
        onImeAction = onImeAction,
        visualTransformation = PasswordVisualTransformation(),
    )
}

@Composable
private fun EditServerFieldError.toStringRes(): Int = when (this) {
    EditServerFieldError.REQUIRED -> R.string.server_editor_required
    EditServerFieldError.INVALID_URL -> R.string.settings_invalid_url
}

@Composable
private fun AppearanceSection(state: EditServerUiState, actions: EditServerActions) {
    val (background, _) = serverSwatchColors(state.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = actions.onPickColor)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TakiTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(TakiTheme.dimensions.artworkMini)
                .clip(CircleShape)
                .background(background),
        )
        Spacer(Modifier.width(TakiTheme.spacing.md))
        Text(text = stringResource(R.string.settings_server_color), style = TakiTheme.type.body)
    }
}

@Composable
private fun AdvancedSection(state: EditServerUiState, actions: EditServerActions) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (state.advancedExpanded) ADVANCED_CHEVRON_EXPANDED_ROTATION else 0f,
        animationSpec = tween(ADVANCED_ANIMATION_MS),
        label = "advancedChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = actions.onToggleAdvanced)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(stringResource(R.string.server_editor_advanced), modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = TakiTheme.colors.gray,
            modifier = Modifier
                .size(TakiTheme.dimensions.iconMd)
                .rotate(chevronRotation),
        )
    }
    AnimatedVisibility(visible = state.advancedExpanded) {
        Column {
            ToggleRow(
                title = stringResource(R.string.settings_title_allow_self_signed_certificate),
                description = stringResource(R.string.settings_summary_allow_self_signed_certificate),
                checked = state.allowSelfSignedCertificate,
                onCheckedChange = actions.onSelfSignedChange,
            )
            Spacer(Modifier.height(TakiTheme.spacing.md))
            ToggleRow(
                title = stringResource(R.string.settings_title_force_plain_text_password),
                description = stringResource(R.string.settings_summary_force_plain_text_password),
                checked = state.forcePlainTextPassword,
                onCheckedChange = actions.onPlaintextChange,
            )
            Spacer(Modifier.height(TakiTheme.spacing.md))
            ToggleRow(
                title = stringResource(R.string.jukebox_is_default),
                description = stringResource(
                    if (state.jukeboxSupported == false) {
                        R.string.jukebox_unsupported
                    } else {
                        R.string.jukebox_summary_is_default
                    },
                ),
                checked = state.jukeboxByDefault,
                onCheckedChange = actions.onJukeboxChange,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = TakiTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = TakiTheme.type.body)
            Text(text = description, style = TakiTheme.type.caption)
        }
        Spacer(Modifier.width(TakiTheme.spacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TakiTheme.colors.onAccent,
                checkedTrackColor = TakiTheme.colors.accent,
            ),
        )
    }
}

@Composable
private fun ConnectionStatusRow(state: EditServerUiState) {
    AnimatedVisibility(visible = state.connectionTestState != ConnectionTestState.IDLE) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state.connectionTestState) {
                ConnectionTestState.TESTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(TakiTheme.dimensions.iconSm),
                        color = TakiTheme.colors.gray,
                        strokeWidth = 2.dp, // taki-raw-ok: spinner stroke weight, not a spacing/size token
                    )
                    Spacer(Modifier.width(TakiTheme.spacing.sm))
                    Text(
                        text = stringResource(R.string.server_editor_connection_checking),
                        style = TakiTheme.type.caption,
                    )
                }
                ConnectionTestState.SUCCESS -> {
                    Text(
                        text = stringResource(R.string.server_editor_connection_success),
                        style = TakiTheme.type.caption,
                        color = TakiTheme.colors.accent,
                    )
                }
                ConnectionTestState.FAILED -> {
                    Text(
                        text = state.connectionErrorMessage
                            ?: stringResource(R.string.server_editor_connection_failed),
                        style = TakiTheme.type.caption,
                        color = TakiTheme.colors.error,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ConnectionTestState.IDLE -> Unit
            }
        }
    }
}

@Composable
private fun ActionRow(state: EditServerUiState, actions: EditServerActions, bottomContentInset: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TakiTheme.spacing.lg + bottomContentInset),
        horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.sm),
    ) {
        if (!state.isNewMode) {
            OutlinedButton(
                onClick = actions.onTestConnection,
                enabled = !state.isTesting,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.settings_test_connection_title))
            }
        }
        Button(
            onClick = actions.onConnectOrSave,
            enabled = !state.isTesting,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = TakiTheme.colors.accent,
                contentColor = TakiTheme.colors.onAccent,
            ),
        ) {
            Text(stringResource(if (state.isNewMode) R.string.server_editor_connect else R.string.common_save))
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = TakiTheme.type.sectionHeader,
        modifier = modifier.semantics { heading() },
    )
}
