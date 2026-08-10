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
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/** A compact, playback-first song row used by the Media Library Songs tab. */
class LibraryTrackBinder(
    private val onItemClick: (Track) -> Unit,
    private val onContextMenuClick: (MenuItem, Track) -> Boolean,
    private val showHeart: Boolean = false,
    private val onHeartClick: (Track) -> Unit = {}
) : ItemViewBinder<Identifiable, LibraryTrackBinder.ViewHolder>(), KoinComponent {

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

        holder.container.setOnClickListener { onItemClick(track) }
        holder.container.setOnLongClickListener {
            showMenu(holder.container, track)
            true
        }
        holder.menu.setOnClickListener { showMenu(holder.menu, track) }
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

    private fun showMenu(anchor: View, track: Track) {
        PopupMenu(anchor.context, anchor).apply {
            menuInflater.inflate(R.menu.context_menu_track_collection, menu)
            setOnMenuItemClickListener { onContextMenuClick(it, track) }
            show()
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.library_track_container)
        val cover: ImageView = view.findViewById(R.id.library_track_cover)
        val title: TextView = view.findViewById(R.id.library_track_title)
        val subtitle: TextView = view.findViewById(R.id.library_track_subtitle)
        val heart: ImageButton = view.findViewById(R.id.library_track_heart)
        val menu: ImageButton = view.findViewById(R.id.library_track_menu)
    }

    companion object {
        private const val SUBTITLE_SEPARATOR = " · "
    }
}
