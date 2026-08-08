/*
 * DownloadedAlbumRowBinder.kt
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
import com.google.android.material.button.MaterialButton
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/**
 * Renders a downloaded [Album] as a card (Playlist-card visual language) in the Downloads
 * screen. Every album shown here is already fully local, so the row's only action is removing
 * its downloaded tracks -- there is no download button to toggle.
 */
class DownloadedAlbumRowBinder(
    private val onItemClick: (Album) -> Unit,
    private val onRemoveDownload: (Album) -> Unit
) : ItemViewDelegate<Album, DownloadedAlbumRowBinder.ViewHolder>(),
    KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateViewHolder(context: Context, parent: ViewGroup): ViewHolder =
        ViewHolder(
            LayoutInflater.from(context).inflate(R.layout.list_item_downloaded_album, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, item: Album) {
        holder.title.text = item.title
        holder.artist.text = item.artist
        val trackCount = (item.songCount ?: 0).toInt()
        holder.trackCount.text = holder.itemView.resources.getQuantityString(
            R.plurals.n_songs,
            trackCount,
            trackCount
        )

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.removeButton.setOnClickListener { onRemoveDownload(item) }

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
        val title: TextView = view.findViewById(R.id.album_title)
        val artist: TextView = view.findViewById(R.id.album_artist)
        val trackCount: TextView = view.findViewById(R.id.album_track_count)
        val coverArt: ImageView = view.findViewById(R.id.cover_art)
        val removeButton: MaterialButton = view.findViewById(R.id.album_remove_download)
    }
}
