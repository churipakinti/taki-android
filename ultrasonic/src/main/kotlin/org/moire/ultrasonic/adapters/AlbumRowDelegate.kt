/*
 * AlbumRowBinder.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.HeartRating
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewDelegate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.LayoutType

/**
 * Creates a Row in a RecyclerView which contains the details of an Album
 */
open class AlbumRowDelegate(
    open val onItemClick: (Album) -> Unit,
    open val onContextMenuClick: (MenuItem, Album) -> Boolean
) : ItemViewDelegate<Album, AlbumRowDelegate.ListViewHolder>(), KoinComponent {

    private val starDrawable: Int = R.drawable.rating_star_full
    private val starHollowDrawable: Int = R.drawable.rating_star_hollow

    open var layoutType = LayoutType.LIST

    override fun onBindViewHolder(holder: ListViewHolder, item: Album) {
        holder.album.text = item.title
        holder.artist.text = item.artist
        holder.details.setOnClickListener { onItemClick(item) }
        holder.coverArt.setOnClickListener { onItemClick(item) }
        holder.details.setOnLongClickListener {
            val popup = Utils.createPopupMenu(holder.itemView)

            popup.setOnMenuItemClickListener { menuItem ->
                onContextMenuClick(menuItem, item)
            }

            true
        }
        holder.coverArtId = item.coverArt
        holder.star.setImageResource(if (item.starred) starDrawable else starHollowDrawable)
        holder.star.setOnClickListener { onStarClick(item, holder.star) }

        val imageLoaderProvider: ImageLoaderProvider by inject()
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

    /**
     * Holds the view properties of an Item row
     */
    open class ListViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        var album: TextView
        var artist: TextView
        var details: LinearLayout
        var coverArt: ImageView
        var star: ImageView
        var coverArtId: String? = null

        constructor(parent: ViewGroup, inflater: LayoutInflater) : this(
            inflater.inflate(R.layout.list_item_album, parent, false)
        )

        init {
            album = view.findViewById(R.id.album_title)
            artist = view.findViewById(R.id.album_artist)
            details = view.findViewById(R.id.row_album_details)
            coverArt = view.findViewById(R.id.cover_art)
            star = view.findViewById(R.id.album_star)
            coverArtId = null
        }
    }

    /**
     * Holds the view properties of an Item row
     */
    class CoverViewHolder(
        view: View
    ) : ListViewHolder(view) {
        constructor(parent: ViewGroup, inflater: LayoutInflater) : this(
            inflater.inflate(R.layout.grid_item_album, parent, false)
        )
    }

    /**
     * Handles the star / unstar action for an album
     */
    private fun onStarClick(entry: Album, star: ImageView) {
        entry.starred = !entry.starred
        star.setImageResource(if (entry.starred) starDrawable else starHollowDrawable)

        RxBus.ratingSubmitter.onNext(
            RatingUpdate(
                entry.id,
                HeartRating(entry.starred)
            )
        )
    }

    override fun onCreateViewHolder(context: Context, parent: ViewGroup): ListViewHolder {
        return when (layoutType) {
            LayoutType.LIST -> ListViewHolder(
                parent,
                LayoutInflater.from(context)
            )
            LayoutType.COVER -> CoverViewHolder(
                parent,
                LayoutInflater.from(context)
            )
        }
    }
}

class AlbumGridDelegate(
    onItemClick: (Album) -> Unit,
    onContextMenuClick: (MenuItem, Album) -> Boolean
) : AlbumRowDelegate(onItemClick, onContextMenuClick) {
    override var layoutType = LayoutType.COVER
}
