/*
 * SimilarArtistDelegate.kt
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
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.FileUtil

/** Renders a compact artist portrait in the similar-artists carousel. */
class SimilarArtistDelegate(
    private val onItemClick: (Artist) -> Unit
) : ItemViewDelegate<Artist, SimilarArtistDelegate.ViewHolder>(),
    KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateViewHolder(context: Context, parent: ViewGroup): ViewHolder =
        ViewHolder(
            LayoutInflater.from(context).inflate(R.layout.artist_similar_item, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, item: Artist) {
        holder.name.text = item.name
        holder.container.setOnClickListener { onItemClick(item) }

        imageLoaderProvider.executeOn {
            it.loadImage(
                view = holder.coverArt,
                id = item.coverArt,
                key = FileUtil.getArtistArtKey(item.name, false),
                large = false,
                size = 0,
                defaultResourceId = R.drawable.artist_placeholder
            )
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.similar_artist_container)
        val coverArt: ImageView = view.findViewById(R.id.similar_artist_art)
        val name: TextView = view.findViewById(R.id.similar_artist_name)
    }
}
