package org.moire.ultrasonic.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.util.ServerColor
import org.moire.ultrasonic.util.Util.themeColor

/**
 * Renders each configured server (plus the always-present Offline entry) as a card, and
 * builds the row's "⋮" context menu (edit/delete/reorder).
 */
@Suppress("LongParameterList")
internal class ServerRowAdapter(
    private val context: Context,
    private val model: ServerSettingsModel,
    private val activeServerProvider: ActiveServerProvider,
    private val onItemClick: (ServerSetting) -> Unit,
    private val serverDeletedCallback: (Int) -> Unit,
    private val serverEditRequestedCallback: (Int) -> Unit
) : RecyclerView.Adapter<ServerRowAdapter.ViewHolder>() {

    private var data: MutableList<ServerSetting> = mutableListOf()

    companion object {
        private const val MENU_ID_EDIT = 1
        private const val MENU_ID_DELETE = 2
        private const val MENU_ID_UP = 3
        private const val MENU_ID_DOWN = 4
    }

    fun setData(newData: Array<ServerSetting>) {
        data = mutableListOf(ActiveServerProvider.OFFLINE_DB)
        data.addAll(newData)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.server_row, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val setting = data[position]
        val isOffline = setting.id == ActiveServerProvider.OFFLINE_DB_ID

        holder.name.text = setting.name
        holder.description.text = setting.url
        holder.menu.isInvisible = isOffline

        val icon = ContextCompat.getDrawable(
            context,
            if (isOffline) R.drawable.ic_menu_screen_on_off else R.drawable.ic_menu_server
        )
        val background = ContextCompat.getDrawable(context, R.drawable.circle)
        icon?.setTint(ServerColor.getForegroundColor(context, setting.color))
        background?.setTint(ServerColor.getBackgroundColor(context, setting.color))
        holder.image.setImageDrawable(icon)
        holder.image.background = background

        val isActive = position == activeServerProvider.getActiveServer().index
        holder.card.strokeColor = if (isActive) {
            context.themeColor(androidx.appcompat.R.attr.colorPrimary)
        } else {
            android.graphics.Color.TRANSPARENT
        }

        holder.card.setOnClickListener { onItemClick(setting) }
        holder.menu.setOnClickListener { view -> serverMenuClick(view, position) }
    }

    /**
     * Builds the Context Menu of a row when the "more" icon is clicked
     */
    private fun serverMenuClick(view: View, position: Int) {
        val menu = PopupMenu(context, view)
        val firstServer = 1
        val lastServer = itemCount - 1

        menu.menu.add(
            Menu.NONE,
            MENU_ID_EDIT,
            Menu.NONE,
            context.getString(R.string.server_menu_edit)
        )

        menu.menu.add(
            Menu.NONE,
            MENU_ID_DELETE,
            Menu.NONE,
            context.getString(R.string.server_menu_delete)
        )

        if (position != firstServer) {
            menu.menu.add(
                Menu.NONE,
                MENU_ID_UP,
                Menu.NONE,
                context.getString(R.string.server_menu_move_up)
            )
        }

        if (position != lastServer) {
            menu.menu.add(
                Menu.NONE,
                MENU_ID_DOWN,
                Menu.NONE,
                context.getString(R.string.server_menu_move_down)
            )
        }

        menu.show()

        menu.setOnMenuItemClickListener { menuItem -> popupMenuItemClick(menuItem, position) }
    }

    /**
     * Handles the click on a context menu item
     */
    private fun popupMenuItemClick(menuItem: MenuItem, position: Int): Boolean {
        when (menuItem.itemId) {
            MENU_ID_EDIT -> {
                serverEditRequestedCallback.invoke(position)
                return true
            }

            MENU_ID_DELETE -> {
                serverDeletedCallback.invoke(data[position].id)
                return true
            }

            MENU_ID_UP -> {
                model.moveItemUp(position)
                return true
            }

            MENU_ID_DOWN -> {
                model.moveItemDown(position)
                return true
            }

            else -> return false
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val name: TextView = view.findViewById(R.id.server_name)
        val description: TextView = view.findViewById(R.id.server_description)
        val image: ImageView = view.findViewById(R.id.server_image)
        val menu: MaterialButton = view.findViewById(R.id.server_menu)
    }
}
