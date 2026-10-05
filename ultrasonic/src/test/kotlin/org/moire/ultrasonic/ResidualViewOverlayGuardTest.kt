/*
 * ResidualViewOverlayGuardTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Keeps the inventory of app-owned View overlay APIs (issue #10 phase 5A6 final residual audit)
 * honest. After 5A6 no *reachable* Taki interaction uses an app-owned `AlertDialog`,
 * `DialogFragment`, `BottomSheetDialogFragment` or a *shown* `PopupMenu`; the only code lines left
 * that mention those APIs are the documented files below. A new file showing up here means a new
 * View overlay was introduced - add a Compose sheet instead, or classify it in
 * `docs/technical/TAKI_COMPOSE_COVERAGE_AUDIT.md` and extend the list on purpose.
 *
 * The list is an inventory of *known, classified* leftovers, not of live UI:
 *  - never-shown `PopupMenu`s used only to inflate a menu resource so `ContextMenuUtil` can
 *    resolve a real `MenuItem` (INTERNAL_NON_UI): TrackCollectionFragment, AlbumListFragment,
 *    ArtistListFragment;
 *  - the legacy RecyclerView binders/helpers reachable only from the unreachable legacy
 *    TrackCollection path, and the legacy filter bar + `ItemSelectionDialogFragment` used only
 *    there (UNREACHABLE / DEFERRED_DEAD_CODE, tracked with #23);
 *  - the third-party `skydoves` color picker in Edit Server (SYSTEM_OR_THIRD_PARTY).
 */
class ResidualViewOverlayGuardTest {

    private val root = File("src/main/kotlin/org/moire/ultrasonic")

    private val overlayApi = Regex(
        "PopupMenu|ListPopupWindow|DialogFragment|AlertDialog|MaterialAlertDialogBuilder|" +
            "BottomSheetDialog|ColorPickerDialog|\\b(InfoDialog|ErrorDialog|ConfirmationDialog)\\b",
    )

    private val knownLeftovers = setOf(
        // never-shown PopupMenu used as a menu-item factory (INTERNAL_NON_UI)
        "fragment/TrackCollectionFragment.kt",
        "fragment/AlbumListFragment.kt",
        "fragment/ArtistListFragment.kt",
        // legacy binders + helper, instantiated only by the unreachable legacy path
        "adapters/AlbumRowDelegate.kt",
        "adapters/ArtistRowBinder.kt",
        "adapters/LibraryTrackBinder.kt",
        "adapters/TrackViewBinder.kt",
        "adapters/Utils.kt",
        // legacy filter bar + list dialog, only reachable from that same legacy path
        "view/FilterButtonBar.kt",
        "fragment/ItemSelectionDialogFragment.kt",
        // third-party colour picker (platform boundary)
        "fragment/EditServerFragment.kt",
    )

    private fun codeLines(file: File) = file.readLines().filterNot {
        val line = it.trimStart()
        line.startsWith("*") || line.startsWith("//") || line.startsWith("/*") || line.startsWith("import ")
    }

    @Test
    fun `only the documented, classified files still reference View overlay APIs`() {
        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> codeLines(file).any(overlayApi::containsMatchIn) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSortedSet()
        assertEquals(
            "View overlay APIs appear in code outside the classified inventory",
            knownLeftovers.toSortedSet(),
            hits,
        )
    }

    @Test
    fun `no reachable screen host opens an ItemSelectionDialogFragment any more`() {
        val live = listOf(
            "fragment/AlbumListFragment.kt",
            "fragment/CreatePlaylistFragment.kt",
            "fragment/PlaylistListFragment.kt",
            "fragment/HomeFragment.kt",
            "fragment/MainFragment.kt",
        )
        live.forEach { path ->
            val hits = codeLines(File(root, path)).filter { it.contains("ItemSelectionDialogFragment") }
            assertEquals("$path still uses the legacy list dialog: $hits", emptyList<String>(), hits)
        }
    }

    @Test
    fun `the legacy list dialog is never shown by the Compose TrackCollection hosts`() {
        val shown = codeLines(File(root, "fragment/TrackCollectionFragment.kt"))
            .filter { it.contains("ItemSelectionDialogFragment.create(") }
        // The only remaining creators are the unreachable legacy filter-bar functions
        // (showSelectionDialog), none of them called from a Compose host.
        assertEquals(1, shown.size)
    }
}
