/*
 * GenreAdapter.kt
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
import android.widget.SectionIndexer
import android.widget.TextView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Genre

class GenreAdapter(context: Context, genres: List<Genre>) :
    ArrayAdapter<Genre?>(context, R.layout.list_item_generic, genres),
    SectionIndexer {
    private val layoutInflater: LayoutInflater

    // Both arrays are indexed by section ID.
    private val sections: Array<Any>
    private val positions: Array<Int>

    init {
        layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val sectionSet: MutableCollection<String> = LinkedHashSet(INITIAL_CAPACITY)
        val positionList: MutableList<Int> = ArrayList(INITIAL_CAPACITY)
        for (i in genres.indices) {
            val (index) = genres[i]
            if (!sectionSet.contains(index)) {
                sectionSet.add(index)
                positionList.add(i)
            }
        }
        sections = sectionSet.toTypedArray()
        positions = positionList.toTypedArray()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var rowView = convertView
        if (rowView == null) {
            rowView = layoutInflater.inflate(R.layout.list_item_generic, parent, false)
        }
        (rowView as TextView?)!!.text = getItem(position)!!.name
        return rowView!!
    }

    override fun getSections(): Array<Any> = sections

    override fun getPositionForSection(section: Int): Int = positions[section]

    override fun getSectionForPosition(pos: Int): Int {
        for (i in 0 until sections.size - 1) {
            if (pos < positions[i + 1]) {
                return i
            }
        }
        return sections.size - 1
    }

    companion object {
        const val INITIAL_CAPACITY: Int = 30
    }
}
