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
    val layout: Int = R.layout.list_item_track,
    val showArtist: (Track) -> Boolean = { true },
    val showRating: Boolean = true,
    val queueStyle: Boolean = false,
    val trackNumberText: ((Track) -> String?)? = null,
    // Selection here means "tap toggles a checkbox instead of playing" -- off by default, only
    // entered via long-press (see onEnterSelectionMode), not an always-on mode like it used to be.
    val isSelectionModeActive: () -> Boolean = { false },
    val onEnterSelectionMode: ((Track) -> Unit)? = null,
    val createContextMenu: (View, Track) -> PopupMenu = { view, _ ->
        Utils.createPopupMenu(
            view,
            R.menu.context_menu_track
        )
    }
) : ItemViewBinder<Identifiable, TrackViewHolder>(),
    KoinComponent {

    var startDrag: ((TrackViewHolder) -> Unit)? = null

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): TrackViewHolder =
        TrackViewHolder(inflater.inflate(layout, parent, false))

    @SuppressLint("ClickableViewAccessibility")
    @Suppress("LongMethod")
    override fun onBindViewHolder(holder: TrackViewHolder, item: Identifiable) {
        val diffAdapter = adapter as BaseAdapter<*>

        val track = (item as? Track) ?: return
        val selecting = checkable && isSelectionModeActive()

        // Remove observer before binding
        holder.observableChecked.removeObservers(lifecycleOwner)

        holder.setSong(
            song = track,
            checkable = selecting,
            draggable = draggable,
            isSelected = diffAdapter.isSelected(track.longId),
            showArtist = showArtist(track),
            showRating = showRating,
            queueStyle = queueStyle,
            trackNumberText = trackNumberText?.invoke(track),
            showRowActions = checkable && !selecting && onContextMenuClick != null
        )

        holder.itemView.setOnLongClickListener {
            when {
                checkable && !selecting -> onEnterSelectionMode?.invoke(track)

                selecting && !track.isVideo -> holder.isChecked = !holder.check.isChecked

                onContextMenuClick != null -> {
                    val popup = createContextMenu(holder.itemView, track)
                    popup.setOnMenuItemClickListener { menuItem ->
                        onContextMenuClick.invoke(menuItem, track)
                    }
                }

                !track.isDirectory -> holder.maximizeOrMinimize()
            }

            true
        }

        holder.itemView.setOnClickListener {
            if (selecting && !track.isVideo) {
                val nowChecked = !holder.check.isChecked
                holder.isChecked = nowChecked
            } else {
                onItemClick(track, holder.bindingAdapterPosition)
            }
        }

        holder.menu.setOnClickListener { view ->
            if (onContextMenuClick == null) return@setOnClickListener
            // createContextMenu() already calls .show() internally (see Utils.createPopupMenu)
            val popup = createContextMenu(view, track)
            popup.setOnMenuItemClickListener { menuItem ->
                onContextMenuClick.invoke(menuItem, track)
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
