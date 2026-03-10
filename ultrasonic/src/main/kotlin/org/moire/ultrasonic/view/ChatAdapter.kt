/*
 * ChatAdapter.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.view

import android.content.Context
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.ChatMessage
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

class ChatAdapter(private val context: Context, private val messages: List<ChatMessage>) :
    ArrayAdapter<ChatMessage>(
        context,
        R.layout.chat_item,
        messages
    ),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun areAllItemsEnabled(): Boolean = true

    override fun isEnabled(position: Int): Boolean = false

    override fun getCount(): Int = messages.size

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val message = getItem(position)
        val holder: ViewHolder
        val layout: Int
        val me = activeServerProvider.getActiveServer().userName

        // Different layouts based on the message sender/recipient
        layout = if (message?.username == me) R.layout.chat_item_reverse else R.layout.chat_item

        if (view == null || (view.tag as ViewHolder).layout != layout) {
            view = inflateView(layout, parent)
            holder = ViewHolder()
        } else {
            holder = view.tag as ViewHolder
        }

        linkHolder(holder, layout, view)
        if (message != null) setData(holder, message)
        return view
    }

    private fun inflateView(layout: Int, parent: ViewGroup): View =
        LayoutInflater.from(context).inflate(layout, parent, false)

    private class ViewHolder {
        var layout = R.layout.chat_item
        var avatar: ImageView? = null
        var message: TextView? = null
        var username: TextView? = null
        var time: TextView? = null
        var chatMessage: ChatMessage? = null
    }

    private fun linkHolder(holder: ViewHolder, layout: Int, view: View) {
        holder.layout = layout
        holder.avatar = view.findViewById(R.id.chat_avatar)
        holder.message = view.findViewById(R.id.chat_message)
        holder.message?.movementMethod = LinkMovementMethod.getInstance()
        holder.username = view.findViewById(R.id.chat_username)
        holder.time = view.findViewById(R.id.chat_time)
        view.tag = holder
    }

    private fun setData(holder: ViewHolder, message: ChatMessage) {
        holder.chatMessage = message
        val timeFormat = DateFormat.getTimeFormat(context)
        val messageTimeFormatted = "[${timeFormat.format(message.time)}]"
        val imageLoader = imageLoaderProvider.getImageLoader()
        if (holder.avatar != null && !TextUtils.isEmpty(message.username)) {
            imageLoader.loadAvatarImage(holder.avatar!!, message.username)
        }
        holder.username?.text = message.username
        holder.message?.text = message.message
        holder.time?.text = messageTimeFormatted
    }
}
