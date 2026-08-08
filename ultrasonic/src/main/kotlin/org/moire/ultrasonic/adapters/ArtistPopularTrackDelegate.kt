/*
 * ArtistPopularTrackDelegate.kt
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
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Util

/** Compact, artwork-backed row for the artist screen's short song preview. */
class ArtistPopularTrackDelegate(private val onItemClick: (Track) -> Unit) :
    ItemViewDelegate<Track, ArtistPopularTrackDelegate.ViewHolder>(),
    KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateViewHolder(context: Context, parent: ViewGroup): ViewHolder = ViewHolder(
        LayoutInflater.from(context).inflate(
            R.layout.artist_popular_track_item,
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, item: Track) {
        holder.rank.text = (adapter.items.indexOf(item) + 1).toString()
        holder.title.text = item.title ?: item.name
        holder.album.text = item.album
        holder.duration.text = Util.formatTotalDuration(item.duration?.toLong())
        holder.container.setOnClickListener { onItemClick(item) }

        imageLoaderProvider.executeOn {
            it.loadImage(holder.cover, item, false, 0, R.drawable.unknown_album)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.artist_track_container)
        val rank: TextView = view.findViewById(R.id.artist_track_rank)
        val cover: ImageView = view.findViewById(R.id.artist_track_cover)
        val title: TextView = view.findViewById(R.id.artist_track_title)
        val album: TextView = view.findViewById(R.id.artist_track_album)
        val duration: TextView = view.findViewById(R.id.artist_track_duration)
    }
}
