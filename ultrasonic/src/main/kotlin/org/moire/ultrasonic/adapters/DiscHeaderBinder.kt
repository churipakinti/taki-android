/*
 * DiscHeaderBinder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Track

class DiscHeaderBinder(
    private val onDownload: (List<Track>) -> Unit
) : ItemViewBinder<DiscHeader, DiscHeaderBinder.ViewHolder>() {

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder =
        ViewHolder(inflater.inflate(R.layout.disc_header_item, parent, false))

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val label: TextView = itemView.findViewById(R.id.disc_header_label)
        val download: View = itemView.findViewById(R.id.disc_header_download)
    }

    override fun onBindViewHolder(holder: ViewHolder, item: DiscHeader) {
        holder.label.text = holder.itemView.resources.getString(
            R.string.album_disc_header,
            item.discNumber
        )
        holder.download.setOnClickListener { onDownload(item.tracks) }
    }
}
