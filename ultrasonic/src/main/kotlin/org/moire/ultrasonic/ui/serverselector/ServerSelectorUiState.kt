/*
 * ServerSelectorUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The immutable, presentation-ready state of the Compose Server Selector screen (post-issue-#10
 * residual migration, phase 5A2). A straight projection of what the legacy `ServerSelectorFragment`
 * /`ServerRowAdapter` already loaded/derived: [org.moire.ultrasonic.model.ServerSettingsModel
 * .getServerList] plus the always-synthesized Offline row
 * ([org.moire.ultrasonic.data.ActiveServerProvider.OFFLINE_DB]) - never a network fetch, never a
 * second server repository.
 */
@Immutable
data class ServerSelectorUiState(
    val rows: ImmutableList<ServerSelectorRow> = persistentListOf(),
    /** The row a delete was requested for, or null when no confirmation is showing. Host-owned
     *  (survives recomposition/rotation), matching every other migrated transient sheet's
     *  "Compose reads, ViewModel owns the value" contract. */
    val pendingDelete: ServerSelectorRow? = null,
)

/**
 * One server row, including the always-present, non-editable Offline entry. [id] is
 * [org.moire.ultrasonic.data.ServerSetting.id] (or
 * [org.moire.ultrasonic.data.ActiveServerProvider.OFFLINE_DB_ID] for the Offline row) - a stable
 * identity, used directly for navigation to Edit Server (issue #10 phase 5A3; this row used to
 * also carry a `position` field fed into a fragile `ServerSetting.index`-based nav argument,
 * removed once the editor started resolving by `id` instead - see the phase 5A3 audit).
 */
@Immutable
data class ServerSelectorRow(
    val id: Int,
    val name: String,
    /** Null/not shown for the Offline row, matching the legacy `holder.description.isGone`. */
    val description: String?,
    val color: Int?,
    val isOffline: Boolean,
    val isActive: Boolean,
)
