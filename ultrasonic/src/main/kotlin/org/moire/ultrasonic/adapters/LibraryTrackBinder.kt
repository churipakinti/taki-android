/*
 * LibraryTrackBinder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.DownloadState
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/** A compact, playback-first song row used by the Media Library Songs tab. */
class LibraryTrackBinder(
    private val onItemClick: (Track) -> Unit,
    private val onContextMenuClick: (MenuItem, Track) -> Boolean,
    private val showHeart: Boolean = false,
    private val onHeartClick: (Track) -> Unit = {}
) : ItemViewBinder<Identifiable, LibraryTrackBinder.ViewHolder>(),
    KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder =
        ViewHolder(inflater.inflate(R.layout.list_item_library_track, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, item: Identifiable) {
        val track = item as? Track ?: return
        holder.title.text = track.title ?: track.name
        holder.subtitle.text = listOfNotNull(
            track.artist?.takeIf { it.isNotBlank() },
            track.album?.takeIf { it.isNotBlank() }
        ).joinToString(SUBTITLE_SEPARATOR)

        // Resolved async (Storage.isPathExists() is a disk read) and cached on the holder, same
        // pattern as TrackViewHolder.cachedStatus -- read by showMenu() so Pin/Unpin/Download/
        // Delete only show for the state they actually apply to. See
        // TAKI_BETA_COMPLETION_PLAN.md P1.
        holder.downloadState = DownloadState.UNKNOWN
        holder.scope.launch {
            holder.downloadState = DownloadService.getDownloadState(track)
        }

        holder.container.setOnClickListener { onItemClick(track) }
        holder.container.setOnLongClickListener {
            showMenu(holder.container, track, holder.downloadState)
            true
        }
        holder.menu.setOnClickListener { showMenu(holder.menu, track, holder.downloadState) }
        holder.heart.isVisible = showHeart
        if (showHeart) {
            updateHeart(holder, track)
            holder.heart.setOnClickListener {
                onHeartClick(track)
                updateHeart(holder, track)
            }
        } else {
            holder.heart.setOnClickListener(null)
        }

        imageLoaderProvider.executeOn {
            it.loadImage(holder.cover, track, false, 0, R.drawable.unknown_album)
        }
    }

    private fun updateHeart(holder: ViewHolder, track: Track) {
        holder.heart.setImageResource(
            if (track.starred) R.drawable.rating_heart_full else R.drawable.rating_heart_hollow
        )
    }

    private fun showMenu(anchor: View, track: Track, downloadState: DownloadState) {
        val popup = Utils.createPopupMenu(anchor, R.menu.context_menu_track_collection, downloadState)
        popup.setOnMenuItemClickListener { onContextMenuClick(it, track) }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.dispose()
        super.onViewRecycled(holder)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.library_track_container)
        val cover: ImageView = view.findViewById(R.id.library_track_cover)
        val title: TextView = view.findViewById(R.id.library_track_title)
        val subtitle: TextView = view.findViewById(R.id.library_track_subtitle)
        val heart: ImageButton = view.findViewById(R.id.library_track_heart)
        val menu: ImageButton = view.findViewById(R.id.library_track_menu)

        var downloadState: DownloadState = DownloadState.UNKNOWN

        // Cancelled and recreated on every recycle (TrackViewHolder does the same) -- a
        // ViewHolder is reused for different tracks as the list scrolls, so a lookup started for
        // song A must not land on song B's row after it's recycled.
        var scope = CoroutineScope(Dispatchers.IO)

        fun dispose() {
            scope.cancel()
            scope = CoroutineScope(Dispatchers.IO)
            downloadState = DownloadState.UNKNOWN
        }
    }

    companion object {
        private const val SUBTITLE_SEPARATOR = " · "
    }
}
