/*
 * GenreAdapter.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.view

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

class GenreAdapter(
    private val onItemClick: (Genre) -> Unit,
    private val onCoverNeeded: (Genre) -> Unit
) : RecyclerView.Adapter<GenreAdapter.ViewHolder>(),
    KoinComponent {

    private var genres: List<Genre> = emptyList()
    private val coverTracks = mutableMapOf<String, Track?>()
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    @SuppressLint("NotifyDataSetChanged")
    fun submitGenres(newGenres: List<Genre>, clearCovers: Boolean) {
        genres = newGenres
        if (clearCovers) coverTracks.clear()
        notifyDataSetChanged()
    }

    fun setCover(genreName: String, track: Track?) {
        coverTracks[genreName] = track
        val position = genres.indexOfFirst { it.name == genreName }
        if (position >= 0) notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.genre_card_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val genre = genres[position]
        holder.boundGenre = genre.name
        holder.name.text = genre.name
        holder.itemView.setOnClickListener { onItemClick(genre) }

        val coverTrack = coverTracks[genre.name]
        holder.cover.setImageResource(R.drawable.genre_placeholder)

        if (coverTrack != null) {
            imageLoaderProvider.executeOn { imageLoader ->
                if (holder.boundGenre == genre.name) {
                    imageLoader.loadImage(
                        holder.cover,
                        coverTrack,
                        false,
                        0,
                        R.drawable.genre_placeholder
                    )
                }
            }
        } else if (!coverTracks.containsKey(genre.name)) {
            onCoverNeeded(genre)
        }
    }

    override fun getItemCount(): Int = genres.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.genre_name)
        val cover: ImageView = view.findViewById(R.id.genre_cover)
        var boundGenre: String? = null
    }
}
