package org.moire.ultrasonic.adapters

import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Identifiable

object Utils {
    @JvmStatic
    fun createPopupMenu(view: View, layout: Int = R.menu.context_menu_artist): PopupMenu {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(layout, popup.menu)

        val downloadMenuItem = popup.menu.findItem(R.id.menu_download)
        downloadMenuItem?.isVisible = !ActiveServerProvider.isOffline()

        val addPlaylistMenuItem = popup.menu.findItem(R.id.song_menu_add_playlist)
        addPlaylistMenuItem?.isVisible = !ActiveServerProvider.isOffline()

        popup.show()
        return popup
    }

    interface SectionedBinder {
        fun getSectionName(item: Identifiable): String
    }
}
