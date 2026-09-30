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
 * One server row, including the always-present, non-editable Offline entry (position 0). [position]
 * is the row's index within this same combined [Offline, ...real servers] list - the exact value
 * the legacy `ServerRowAdapter` fed into `EditServerFragment`'s `index` nav argument (itself a
 * lookup against `ServerSetting.index`, not `id` - see the phase 5A2 audit). Preserved unchanged
 * here since fixing that contract is `EditServerFragment`'s scope, not this phase's.
 */
@Immutable
data class ServerSelectorRow(
    val id: Int,
    val position: Int,
    val name: String,
    /** Null/not shown for the Offline row, matching the legacy `holder.description.isGone`. */
    val description: String?,
    val color: Int?,
    val isOffline: Boolean,
    val isActive: Boolean,
)
