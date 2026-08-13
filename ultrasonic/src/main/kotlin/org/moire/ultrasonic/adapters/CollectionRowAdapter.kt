/*
 * CollectionRowAdapter.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/**
 * Rows for the Collections/Box Sets list (docs/TAKI_COLLECTIONS_BOXSETS_IMPLEMENTATION.md section
 * 9): visually distinct from a normal album row (BOX SET label + disc count instead of artist),
 * plain [ListAdapter] rather than the app's multitype delegates since this list is always
 * homogeneous - no need for the polymorphic machinery TrackCollectionFragment's list needs.
 */
class CollectionRowAdapter(
    private val onClick: (MusicCollection) -> Unit
) : ListAdapter<MusicCollection, CollectionRowAdapter.ViewHolder>(DiffCallback()),
    KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.list_item_collection, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.subtitle.text = holder.itemView.resources.getString(
            R.string.collection_box_set_subtitle,
            item.albumCount
        )
        holder.itemView.setOnClickListener { onClick(item) }
        imageLoaderProvider.executeOn {
            it.loadImage(holder.coverArt, item.artworkAlbum, false, 0, R.drawable.unknown_album)
        }
    }

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val coverArt: ImageView = view.findViewById(R.id.cover_art)
        val title: TextView = view.findViewById(R.id.collection_title)
        val subtitle: TextView = view.findViewById(R.id.collection_subtitle)
    }

    private class DiffCallback : DiffUtil.ItemCallback<MusicCollection>() {
        override fun areItemsTheSame(oldItem: MusicCollection, newItem: MusicCollection) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MusicCollection, newItem: MusicCollection) =
            oldItem == newItem
    }
}
