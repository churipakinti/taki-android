/*
 * CollectionDiscAdapter.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.LayoutType

/**
 * A Collection/Box Set's disc rows (docs/TAKI_COLLECTIONS_BOXSETS_IMPLEMENTATION.md section 10):
 * position, own artwork, own track count (already on the cached [Album] row - never a reason to
 * fetch a disc's tracks just to show it in this list). Navigation-only - tapping a row opens the
 * disc via the existing unmodified TrackCollection (Album Detail) screen, where Play/queue
 * actions already live. Supports the same list/cover-grid toggle as AlbumRowDelegate/
 * AlbumGridDelegate for consistency with the rest of the app's browse screens.
 */
class CollectionDiscAdapter(
    private val onOpen: (Album) -> Unit
) : ListAdapter<Album, CollectionDiscAdapter.ViewHolder>(DiffCallback()),
    KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()
    var layoutType = LayoutType.LIST

    override fun getItemViewType(position: Int): Int = when (layoutType) {
        LayoutType.LIST -> VIEW_TYPE_LIST
        LayoutType.COVER -> VIEW_TYPE_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GRID) {
            ViewHolder(inflater.inflate(R.layout.grid_item_collection_disc, parent, false), true)
        } else {
            ViewHolder(inflater.inflate(R.layout.list_item_collection_disc, parent, false), false)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val discNumber = item.discNumber
        val hasPosition = discNumber != null && discNumber > 0
        holder.position.isVisible = hasPosition
        if (hasPosition) {
            holder.position.text = if (holder.isGrid) {
                holder.itemView.resources.getString(R.string.album_disc_header, discNumber)
            } else {
                discNumber.toString()
            }
        }
        holder.title.text = item.title ?: item.album
        val songCount = item.songCount
        holder.trackCount.isVisible = songCount != null && songCount > 0
        if (songCount != null) {
            holder.trackCount.text = holder.itemView.resources.getString(
                R.string.collection_disc_track_count,
                songCount
            )
        }
        holder.itemView.setOnClickListener { onOpen(item) }
        imageLoaderProvider.executeOn {
            it.loadImage(holder.coverArt, item, false, 0, R.drawable.unknown_album)
        }
    }

    class ViewHolder(view: View, val isGrid: Boolean) : RecyclerView.ViewHolder(view) {
        val position: TextView = view.findViewById(R.id.disc_position)
        val coverArt: ImageView = view.findViewById(R.id.cover_art)
        val title: TextView = view.findViewById(R.id.disc_title)
        val trackCount: TextView = view.findViewById(R.id.disc_track_count)
    }

    private class DiffCallback : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Album, newItem: Album) = oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }
}
