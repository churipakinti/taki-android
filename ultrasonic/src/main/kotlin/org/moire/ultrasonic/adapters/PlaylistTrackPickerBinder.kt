/*
 * PlaylistTrackPickerBinder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

class PlaylistTrackPickerBinder(
    private val isSelected: (Track) -> Boolean,
    private val onSelectionChanged: (Track) -> Unit
) : ItemViewBinder<Track, PlaylistTrackPickerBinder.ViewHolder>(),
    KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder =
        ViewHolder(inflater.inflate(R.layout.list_item_playlist_track_picker, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, item: Track) {
        holder.title.text = item.title ?: item.name
        holder.subtitle.text = listOfNotNull(
            item.artist?.takeIf(String::isNotBlank),
            item.album?.takeIf(String::isNotBlank)
        ).joinToString(SUBTITLE_SEPARATOR)
        holder.check.isChecked = isSelected(item)
        holder.container.setOnClickListener {
            onSelectionChanged(item)
            holder.check.isChecked = isSelected(item)
        }

        imageLoaderProvider.executeOn {
            it.loadImage(holder.cover, item, false, 0, R.drawable.unknown_album)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.playlist_picker_track_container)
        val cover: ImageView = view.findViewById(R.id.playlist_picker_track_cover)
        val title: TextView = view.findViewById(R.id.playlist_picker_track_title)
        val subtitle: TextView = view.findViewById(R.id.playlist_picker_track_subtitle)
        val check: CheckBox = view.findViewById(R.id.playlist_picker_track_check)
    }

    companion object {
        private const val SUBTITLE_SEPARATOR = " · "
    }
}
