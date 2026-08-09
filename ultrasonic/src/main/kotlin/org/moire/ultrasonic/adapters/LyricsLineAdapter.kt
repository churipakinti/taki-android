package org.moire.ultrasonic.adapters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.LyricsLine
import org.moire.ultrasonic.util.Util.themeColor

/**
 * Renders a lyrics screen as one big line per row instead of a single text block, so the
 * currently-sung line (when timing data is available) can be highlighted and centered.
 * With no timing data every line simply renders in its plain, non-active style.
 */
class LyricsLineAdapter(private val lines: List<LyricsLine>) :
    RecyclerView.Adapter<LyricsLineAdapter.ViewHolder>() {

    var activeIndex: Int = -1
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.lyrics_line_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(lines[position], position == activeIndex)
    }

    override fun getItemCount(): Int = lines.size

    fun setActiveIndex(index: Int) {
        if (index == activeIndex) return
        val previous = activeIndex
        activeIndex = index
        if (previous in lines.indices) notifyItemChanged(previous)
        if (index in lines.indices) notifyItemChanged(index)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.lyrics_line_text)

        fun bind(line: LyricsLine, isActive: Boolean) {
            textView.text = line.value
            if (isActive) {
                textView.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall
                )
                textView.setTypeface(null, Typeface.BOLD)
                textView.setTextColor(
                    textView.context.themeColor(com.google.android.material.R.attr.colorOnSurface)
                )
                textView.alpha = 1.0f
            } else {
                textView.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
                )
                textView.setTypeface(null, Typeface.NORMAL)
                textView.setTextColor(
                    textView.context.themeColor(
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
                textView.alpha = 0.6f
            }
        }
    }
}
