/*
 * AlbumDetailHeaderBinder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import java.lang.ref.WeakReference
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/**
 * Binds an [AlbumHeader] into the Spotify-style hero used only by the Album detail screen
 * (`TrackCollectionFragment` with `navArgs.isAlbum == true`). Kept separate from the generic
 * [HeaderViewBinder]/`list_header_album.xml` so playlists/genre/artist-songs, which share the
 * same [AlbumHeader] item type, are unaffected.
 */
class AlbumDetailHeaderBinder(
    context: Context,
    private val onPlay: () -> Unit,
    private val onShuffle: () -> Unit,
    private val onDownload: () -> Unit
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
        val download: View = itemView.findViewById(R.id.album_detail_download)
        val aboutSection: View = itemView.findViewById(R.id.album_detail_about_section)
        val notes: TextView = itemView.findViewById(R.id.album_detail_notes)
        val notesToggle: TextView = itemView.findViewById(R.id.album_detail_notes_toggle)
        var notesExpanded = false
    }

    override fun onBindViewHolder(holder: ViewHolder, item: AlbumHeader) {
        val context = weakContext.get() ?: return

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
        holder.download.setOnClickListener { onDownload() }

        val notes = item.notes
        holder.aboutSection.isVisible = !notes.isNullOrEmpty()
        if (!notes.isNullOrEmpty()) {
            holder.notes.text = notes
            holder.notesExpanded = false
            holder.notesToggle.isVisible = notes.length > NOTES_COLLAPSE_LENGTH
            applyNotesExpansion(holder)
            holder.notesToggle.setOnClickListener {
                holder.notesExpanded = !holder.notesExpanded
                applyNotesExpansion(holder)
            }
        }
    }

    private fun applyNotesExpansion(holder: ViewHolder) {
        holder.notes.maxLines = if (holder.notesExpanded) {
            Int.MAX_VALUE
        } else {
            NOTES_COLLAPSED_LINES
        }
        holder.notes.ellipsize = if (holder.notesExpanded) null else TextUtils.TruncateAt.END
        holder.notesToggle.setText(
            if (holder.notesExpanded) R.string.artist_show_less else R.string.artist_show_more
        )
    }

    companion object {
        private const val NOTES_COLLAPSE_LENGTH = 320
        private const val NOTES_COLLAPSED_LINES = 5
    }
}
