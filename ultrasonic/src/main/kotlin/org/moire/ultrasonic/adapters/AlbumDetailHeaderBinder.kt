/*
 * AlbumDetailHeaderBinder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.content.Context
import android.content.res.ColorStateList
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
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Util.themeColor

/**
 * Binds an [AlbumHeader] into the Spotify-style hero used by the Album and Playlist detail
 * screens (`TrackCollectionFragment` with `navArgs.isAlbum == true` or `navArgs.playlistId !=
 * null`). Kept separate from the generic [HeaderViewBinder]/`list_header_album.xml` so
 * genre/artist-songs, which share the same [AlbumHeader] item type but don't get a hero, are
 * unaffected. The trailing action button is parameterized (download for albums, an overflow
 * menu for playlists) instead of duplicating this whole binder for playlists.
 *
 * The Information action is album-only - [onInfoAction] is null for playlists, which keeps
 * the button gone and leaves playlist behavior untouched.
 */
class AlbumDetailHeaderBinder(
    context: Context,
    private val onPlay: () -> Unit,
    private val onShuffle: () -> Unit,
    private val trailingActionIcon: Int,
    private val trailingActionDescription: Int,
    private val onTrailingAction: () -> Unit,
    private val onInfoAction: ((AlbumHeader) -> Unit)? = null,
    // Album-only (issue #15). Null for playlists, which keeps the heart gone and leaves
    // playlist behavior untouched, exactly like [onInfoAction]. Called with the new intended
    // state after the icon has already been flipped optimistically.
    private val onToggleStar: ((Boolean) -> Unit)? = null,
    // Album-only (issue #16). [onArtistClick] makes the hero's artist line a direct
    // navigation target when the album has exactly one artist with a known id; [onMoreClick]
    // shows the contextual overflow, anchored on the view it is passed.
    private val onArtistClick: ((artistId: String, artistName: String) -> Unit)? = null,
    private val onMoreClick: ((anchor: View) -> Unit)? = null
) : ItemViewBinder<AlbumHeader, AlbumDetailHeaderBinder.ViewHolder>(),
    KoinComponent {

    private val weakContext: WeakReference<Context> = WeakReference(context)
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder =
        ViewHolder(inflater.inflate(R.layout.album_detail_header_item, parent, false))

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val art: ImageView = itemView.findViewById(R.id.album_detail_art)
        val title: TextView = itemView.findViewById(R.id.album_detail_title)
        val artist: TextView = itemView.findViewById(R.id.album_detail_artist)
        val subtitle: TextView = itemView.findViewById(R.id.album_detail_subtitle)
        val play: View = itemView.findViewById(R.id.album_detail_play)
        val shuffle: View = itemView.findViewById(R.id.album_detail_shuffle)
        val download: MaterialButton = itemView.findViewById(R.id.album_detail_download)
        val info: MaterialButton = itemView.findViewById(R.id.album_detail_info)
        val star: MaterialButton = itemView.findViewById(R.id.album_detail_star)
        val more: MaterialButton = itemView.findViewById(R.id.album_detail_more)
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

        val singleArtist = item.artists.singleOrNull() ?: item.grandParents.singleOrNull()
        val artistLabel = singleArtist ?: context.resources.getString(
            R.string.common_various_artists
        )
        val year = item.years.singleOrNull()?.toString()
        val songs = context.resources.getQuantityString(
            R.plurals.n_songs,
            item.childCount,
            item.childCount
        )
        holder.artist.text = artistLabel
        holder.subtitle.text = listOfNotNull(year, songs).joinToString(" · ")

        holder.play.setOnClickListener { onPlay() }
        holder.shuffle.setOnClickListener { onShuffle() }
        holder.download.setOnClickListener { onTrailingAction() }

        bindArtistNavigation(holder, item, singleArtist)
        bindMore(holder)
        bindStar(holder, item, context)

        // Only shown once album notes have actually been fetched and turned out non-empty -
        // see loadAlbumInfo()/updateInfoButtonVisibility() in TrackCollectionFragment. Playlists
        // never pass onInfoAction, so this stays gone there regardless of item.notes.
        holder.info.isVisible = onInfoAction != null && !item.notes.isNullOrEmpty()
        holder.info.setOnClickListener { onInfoAction?.invoke(item) }
    }

    private fun bindArtistNavigation(
        holder: ViewHolder,
        item: AlbumHeader,
        singleArtistName: String?
    ) {
        // One artist, one id: a real destination. A various-artists album, or a server that
        // didn't give the tracks an artistId, stays plain non-interactive text.
        val artistId = item.entries.filterIsInstance<Track>()
            .mapNotNull { it.artistId?.takeIf { id -> id.isNotBlank() } }
            .distinct()
            .singleOrNull()
        val navigable = onArtistClick != null && artistId != null && singleArtistName != null

        holder.artist.isClickable = navigable
        holder.artist.isFocusable = navigable
        holder.artist.background = if (navigable) {
            val a = holder.artist.context.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            )
            a.getDrawable(0).also { a.recycle() }
        } else {
            null
        }
        holder.artist.setOnClickListener(
            if (navigable) {
                View.OnClickListener { onArtistClick?.invoke(artistId!!, singleArtistName!!) }
            } else {
                null
            }
        )
    }

    private fun bindMore(holder: ViewHolder) {
        holder.more.isVisible = onMoreClick != null
        holder.more.setOnClickListener { onMoreClick?.invoke(holder.more) }
    }

    private fun bindStar(holder: ViewHolder, item: AlbumHeader, context: Context) {
        val toggle = onToggleStar
        holder.star.isVisible = toggle != null
        if (toggle == null) return

        renderStar(holder, item, context)
        holder.star.setOnClickListener {
            val newState = !item.starred
            item.starred = newState
            renderStar(holder, item, context)
            toggle(newState)
        }
    }

    private fun renderStar(holder: ViewHolder, item: AlbumHeader, context: Context) {
        holder.star.setIconResource(
            if (item.starred) R.drawable.rating_heart_full else R.drawable.rating_heart_hollow
        )
        // Green only carries the "favourite" meaning here (visual guide section 2); neutral
        // otherwise so the row of icon actions stays calm.
        holder.star.iconTint = ColorStateList.valueOf(
            context.themeColor(
                if (item.starred) {
                    androidx.appcompat.R.attr.colorPrimary
                } else {
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                }
            )
        )
        holder.star.contentDescription = context.getString(
            if (item.starred) R.string.album_unstar_description else R.string.album_star_description
        )
    }
}
