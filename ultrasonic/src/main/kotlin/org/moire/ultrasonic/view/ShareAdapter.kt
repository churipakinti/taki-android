/*
 * ShareAdapter.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.view

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Share

class ShareAdapter(private val context: Context, shares: List<Share>) :
    ArrayAdapter<Share>(
        context,
        R.layout.share_list_item,
        shares
    ) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val entry = getItem(position)
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            holder = ViewHolder()
            val inflater = LayoutInflater.from(context)
            view = inflater.inflate(R.layout.share_list_item, parent, false)
            holder.url = view.findViewById(R.id.share_url)
            holder.description = view.findViewById(R.id.share_description)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        if (entry != null) setData(entry, holder)

        return view
    }

    private fun setData(entry: Share, holder: ViewHolder) {
        holder.url?.text = entry.name
        holder.description?.text = entry.description
    }

    class ViewHolder {
        var url: TextView? = null
        var description: TextView? = null
    }
}
