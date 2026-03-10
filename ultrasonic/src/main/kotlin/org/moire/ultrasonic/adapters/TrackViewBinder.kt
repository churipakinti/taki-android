package org.moire.ultrasonic.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.lifecycle.LifecycleOwner
import com.drakeet.multitype.ItemViewBinder
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track

@Suppress("LongParameterList")
class TrackViewBinder(
    val onItemClick: (Track, Int) -> Unit,
    val onContextMenuClick: ((MenuItem, Track) -> Boolean)? = null,
    val checkable: Boolean,
    val draggable: Boolean,
    val lifecycleOwner: LifecycleOwner,
    val createContextMenu: (View, Track) -> PopupMenu = { view, _ ->
        Utils.createPopupMenu(
            view,
            R.menu.context_menu_track
        )
    }
) : ItemViewBinder<Identifiable, TrackViewHolder>(),
    KoinComponent {

    var startDrag: ((TrackViewHolder) -> Unit)? = null

    // Set our layout files
    val layout = R.layout.list_item_track

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): TrackViewHolder =
        TrackViewHolder(inflater.inflate(layout, parent, false))

    @SuppressLint("ClickableViewAccessibility")
    @Suppress("LongMethod")
    override fun onBindViewHolder(holder: TrackViewHolder, item: Identifiable) {
        val diffAdapter = adapter as BaseAdapter<*>

        val track = (item as? Track) ?: return

        // Remove observer before binding
        holder.observableChecked.removeObservers(lifecycleOwner)

        holder.setSong(
            song = track,
            checkable = checkable,
            draggable = draggable,
            diffAdapter.isSelected(track.longId)
        )

        holder.itemView.setOnLongClickListener {
            if (onContextMenuClick != null) {
                val popup = createContextMenu(holder.itemView, track)

                popup.setOnMenuItemClickListener { menuItem ->
                    onContextMenuClick.invoke(menuItem, track)
                }
            } else {
                // Minimize or maximize the Text view (if song title is very long)
                if (!track.isDirectory) {
                    holder.maximizeOrMinimize()
                }
            }

            true
        }

        holder.itemView.setOnClickListener {
            if (checkable && !track.isVideo) {
                val nowChecked = !holder.check.isChecked
                holder.isChecked = nowChecked
            } else {
                onItemClick(track, holder.bindingAdapterPosition)
            }
        }

        holder.drag.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startDrag?.invoke(holder)
            }
            false
        }

        // Notify the adapter of selection changes
        holder.observableChecked.observe(
            lifecycleOwner
        ) { isCheckedNow ->
            if (isCheckedNow) {
                diffAdapter.notifySelected(holder.entry!!.longId)
            } else {
                diffAdapter.notifyUnselected(holder.entry!!.longId)
            }
        }

        // Listen to changes in selection status and update ourselves
        diffAdapter.selectionRevision.observe(
            lifecycleOwner
        ) {
            val newStatus = diffAdapter.isSelected(track.longId)

            if (newStatus != holder.check.isChecked) holder.check.isChecked = newStatus
        }
    }

    override fun onViewRecycled(holder: TrackViewHolder) {
        holder.dispose()
        super.onViewRecycled(holder)
    }
}
