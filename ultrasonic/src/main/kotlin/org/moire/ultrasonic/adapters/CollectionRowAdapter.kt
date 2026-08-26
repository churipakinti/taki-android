/*
 * CollectionRowAdapter.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.bindStackedArtwork

/**
 * Rows for the Collections/Box Sets grid: artwork-first 2-column grid using the
 * stacked-cover effect (see [bindStackedArtwork]) instead
 * of a flat text row. Plain [ListAdapter] rather than the app's multitype delegates since this
 * list is always homogeneous - no need for the polymorphic machinery TrackCollectionFragment's
 * list needs.
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
        val discsText = holder.itemView.resources.getQuantityString(
            R.plurals.n_discs,
            item.albumCount,
            item.albumCount
        )
        holder.title.text = item.title
        holder.subtitle.text = discsText
        holder.itemView.contentDescription = "${item.title}, $discsText"
        holder.itemView.setOnClickListener { onClick(item) }
        bindStackedArtwork(holder.stackedArtwork, item.stackArtwork, imageLoaderProvider)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stackedArtwork: View = view.findViewById(R.id.stacked_artwork)
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
