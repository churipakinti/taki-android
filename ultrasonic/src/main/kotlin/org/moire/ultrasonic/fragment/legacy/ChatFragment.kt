/*
 * ChatFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.ChatViewModel
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.isNullOrWhiteSpace
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.ChatAdapter

class ChatFragment :
    Fragment(),
    KoinComponent,
    RefreshableFragment {
    private lateinit var chatListView: ListView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: MaterialButton
    private var timer: Timer? = null
    override var swipeRefresh: SwipeRefreshLayout? = null
    private val activeServerProvider: ActiveServerProvider by inject()

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Add the ChatMenuProvider for creating the menu
        (requireActivity() as MenuHost).addMenuProvider(
            menuProvider,
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )

        swipeRefresh = view.findViewById(R.id.chat_refresh)
        swipeRefresh?.isEnabled = false
        messageEditText = view.findViewById(R.id.chat_edittext)
        sendButton = view.findViewById(R.id.chat_send)
        sendButton.setOnClickListener { sendMessage() }
        chatListView = view.findViewById(R.id.chat_entries_list)
        chatListView.transcriptMode = ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL
        chatListView.isStackFromBottom = true
        val serverName = activeServerProvider.getActiveServer().name
        val userName = activeServerProvider.getActiveServer().userName
        val title = String.format(
            Locale.ROOT,
            "%s [%s@%s]",
            resources.getString(R.string.button_bar_chat),
            userName,
            serverName
        )
        setTitle(this, title)
        messageEditText.imeOptions = EditorInfo.IME_ACTION_SEND
        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                sendButton.isEnabled = !isNullOrWhiteSpace(editable.toString())
            }
        })
        messageEditText.setOnEditorActionListener(
            OnEditorActionListener {
                    _: TextView?,
                    actionId: Int,
                    event: KeyEvent
                ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (actionId == EditorInfo.IME_NULL && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    sendMessage()
                    return@OnEditorActionListener true
                }
                false
            }
        )
        load()
        timerMethod()
    }

    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.chat, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menu_refresh) {
                load()
                return true
            }
            return false
        }
    }
    override fun onResume() {
        super.onResume()
        chatViewModel.chatMessages.observe(viewLifecycleOwner) { messages ->
            if (!messages.isNullOrEmpty()) {
                val chatAdapter: ListAdapter = ChatAdapter(requireContext(), messages)
                chatListView.adapter = chatAdapter
            }
        }
        if (timer == null) {
            timerMethod()
        }
    }

    override fun onPause() {
        super.onPause()
        timer?.cancel()
        timer = null
    }

    private fun timerMethod() {
        val refreshInterval = Settings.CHAT_REFRESH_INTERVAL
        if (refreshInterval > 0) {
            timer = Timer()
            timer?.schedule(
                object : TimerTask() {
                    override fun run() {
                        requireActivity().runOnUiThread { load() }
                    }
                },
                refreshInterval.toLong(),
                refreshInterval.toLong()
            )
        }
    }

    private fun sendMessage() {
        val text = messageEditText.text ?: return
        val message = text.toString()
        if (!isNullOrWhiteSpace(message)) {
            messageEditText.setText("")
            viewLifecycleOwner.lifecycleScope.launch(
                toastingExceptionHandler()
            ) {
                withContext(Dispatchers.IO) {
                    val musicService = getMusicService()
                    musicService.addChatMessage(message)
                }
                load()
            }
        }
    }

    fun load() {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val result = withContext(Dispatchers.IO) {
                val musicService = getMusicService()
                musicService.getChatMessages(chatViewModel.lastChatMessageTime)?.filterNotNull()
            }
            swipeRefresh?.isRefreshing = false
            if (!result.isNullOrEmpty()) {
                for (message in result) {
                    if (message.time > chatViewModel.lastChatMessageTime) {
                        chatViewModel.lastChatMessageTime = message.time
                    }
                }
                chatViewModel.updateChatMessages(result.reversed())
            }
        }
    }
}
