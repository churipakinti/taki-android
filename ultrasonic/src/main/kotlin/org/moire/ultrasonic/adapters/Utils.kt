package org.moire.ultrasonic.adapters

import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.service.DownloadState

object Utils {
    @JvmStatic
    fun createPopupMenu(
        view: View,
        layout: Int = R.menu.context_menu_artist,
        downloadState: DownloadState = DownloadState.UNKNOWN
    ): PopupMenu {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(layout, popup.menu)

        val downloadMenuItem = popup.menu.findItem(R.id.menu_download)
        downloadMenuItem?.isVisible = !ActiveServerProvider.isOffline()

        val addPlaylistMenuItem = popup.menu.findItem(R.id.song_menu_add_playlist)
        addPlaylistMenuItem?.isVisible = !ActiveServerProvider.isOffline()

        val removeFromPlaylistMenuItem = popup.menu.findItem(R.id.song_menu_remove_from_playlist)
        removeFromPlaylistMenuItem?.isVisible = !ActiveServerProvider.isOffline()

        // Pin/Unpin/Download/Delete only make sense for the states they actually apply to --
        // e.g. "Delete" (deletes the local downloaded copy) showed up even for a track that was
        // never downloaded. UNKNOWN (state not resolved yet, or a menu with no per-track state
        // to give) hides all four rather than guessing. See TAKI_BETA_COMPLETION_PLAN.md P1.
        val isPinned = downloadState == DownloadState.PINNED
        val isDownloadedOrPinned = downloadState == DownloadState.DONE || isPinned
        val canDownload = downloadState == DownloadState.IDLE ||
            downloadState == DownloadState.FAILED ||
            downloadState == DownloadState.CANCELLED

        popup.menu.findItem(R.id.song_menu_download)?.isVisible =
            canDownload && !ActiveServerProvider.isOffline()
        popup.menu.findItem(R.id.song_menu_pin)?.isVisible =
            !isPinned && downloadState != DownloadState.UNKNOWN && !ActiveServerProvider.isOffline()
        popup.menu.findItem(R.id.song_menu_unpin)?.isVisible = isPinned
        popup.menu.findItem(R.id.song_menu_delete)?.isVisible = isDownloadedOrPinned

        popup.show()
        return popup
    }

    interface SectionedBinder {
        fun getSectionName(item: Identifiable): String
    }
}
