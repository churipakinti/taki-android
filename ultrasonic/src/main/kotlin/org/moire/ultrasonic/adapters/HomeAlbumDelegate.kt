/*
 * HomeAlbumDelegate.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewDelegate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/**
 * Renders an Album as a fixed-width card, used in the horizontal
 * "carousel" RecyclerViews of the Home screen.
 */
class HomeAlbumDelegate(
    private val onItemClick: (Album) -> Unit
) : ItemViewDelegate<Album, HomeAlbumDelegate.ViewHolder>(),
    KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateViewHolder(context: Context, parent: ViewGroup): ViewHolder =
        ViewHolder(
            LayoutInflater.from(context).inflate(R.layout.home_carousel_item, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, item: Album) {
        holder.title.text = item.title
        holder.artist.text = item.artist
        holder.container.setOnClickListener { onItemClick(item) }

        imageLoaderProvider.executeOn {
            it.loadImage(
                holder.coverArt,
                item,
                false,
                0,
                R.drawable.unknown_album
            )
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.home_item_container)
        val coverArt: ImageView = view.findViewById(R.id.cover_art)
        val title: TextView = view.findViewById(R.id.album_title)
        val artist: TextView = view.findViewById(R.id.album_artist)
    }
}
