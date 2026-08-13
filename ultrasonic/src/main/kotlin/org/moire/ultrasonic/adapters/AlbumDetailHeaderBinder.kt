/*
 * AlbumDetailHeaderBinder.kt
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
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import com.google.android.material.button.MaterialButton
import java.lang.ref.WeakReference
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/**
 * Binds an [AlbumHeader] into the Spotify-style hero used by the Album and Playlist detail
 * screens (`TrackCollectionFragment` with `navArgs.isAlbum == true` or `navArgs.playlistId !=
 * null`). Kept separate from the generic [HeaderViewBinder]/`list_header_album.xml` so
 * genre/artist-songs, which share the same [AlbumHeader] item type but don't get a hero, are
 * unaffected. The trailing action button is parameterized (download for albums, an overflow
 * menu for playlists) instead of duplicating this whole binder for playlists - see
 * docs/TAKI_PLAYLIST_UX_REDESIGN.md.
 *
 * The Information action (docs/TAKI_ALBUM_INFO_MUSIC_FIRST.md) is album-only - [onInfoAction] is
 * null for playlists, which keeps the button gone and leaves playlist behavior untouched.
 */
class AlbumDetailHeaderBinder(
    context: Context,
    private val onPlay: () -> Unit,
    private val onShuffle: () -> Unit,
    private val trailingActionIcon: Int,
    private val trailingActionDescription: Int,
    private val onTrailingAction: () -> Unit,
    private val onInfoAction: ((AlbumHeader) -> Unit)? = null
) : ItemViewBinder<AlbumHeader, AlbumDetailHeaderBinder.ViewHolder>(),
    KoinComponent {

    private val weakContext: WeakReference<Context> = WeakReference(context)
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder =
        ViewHolder(inflater.inflate(R.layout.album_detail_header_item, parent, false))

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val art: ImageView = itemView.findViewById(R.id.album_detail_art)
        val title: TextView = itemView.findViewById(R.id.album_detail_title)
        val subtitle: TextView = itemView.findViewById(R.id.album_detail_subtitle)
        val play: View = itemView.findViewById(R.id.album_detail_play)
        val shuffle: View = itemView.findViewById(R.id.album_detail_shuffle)
        val download: MaterialButton = itemView.findViewById(R.id.album_detail_download)
        val info: MaterialButton = itemView.findViewById(R.id.album_detail_info)
    }

    override fun onBindViewHolder(holder: ViewHolder, item: AlbumHeader) {
        val context = weakContext.get() ?: return

        holder.download.setIconResource(trailingActionIcon)
        holder.download.contentDescription = context.getString(trailingActionDescription)

        // Deterministic cover pick (first entry), unlike HeaderViewBinder's random selection --
        // a large 300dp hero re-rolling on every rebind/scroll-recycle would look glitchy.
        val coverEntry = item.entries.firstOrNull()
        imageLoaderProvider.executeOn {
            it.loadImage(holder.art, coverEntry, true, 0)
        }

        holder.title.isVisible = item.name != null
        holder.title.text = item.name.orEmpty()

        val artist = when {
            item.artists.size == 1 -> item.artists.iterator().next()
            item.grandParents.size == 1 -> item.grandParents.iterator().next()
            else -> context.resources.getString(R.string.common_various_artists)
        }
        val year = item.years.singleOrNull()?.toString()
        val songs = context.resources.getQuantityString(
            R.plurals.n_songs,
            item.childCount,
            item.childCount
        )
        holder.subtitle.text = listOfNotNull(artist, year, songs).joinToString(" · ")

        holder.play.setOnClickListener { onPlay() }
        holder.shuffle.setOnClickListener { onShuffle() }
        holder.download.setOnClickListener { onTrailingAction() }

        // Only shown once album notes have actually been fetched and turned out non-empty -
        // see loadAlbumInfo()/updateInfoButtonVisibility() in TrackCollectionFragment. Playlists
        // never pass onInfoAction, so this stays gone there regardless of item.notes.
        holder.info.isVisible = onInfoAction != null && !item.notes.isNullOrEmpty()
        holder.info.setOnClickListener { onInfoAction?.invoke(item) }
    }
}
