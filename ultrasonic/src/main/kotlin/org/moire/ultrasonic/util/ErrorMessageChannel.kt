/*
 * ErrorMessageChannel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The pending user-facing error messages that [CommunicationError.handleError] used to show in an
 * app-owned `AlertDialog` (issue #10 phase 5A6). A message is queued only while a UI host is
 * attached - `NavigationActivity` attaches while it is created - which mirrors the legacy dialog,
 * which silently failed to show (and was only logged) when there was no foreground Activity to
 * show it. Messages are shown one at a time, in arrival order; the host dismisses each one.
 *
 * Holds plain strings only - no Context, View or Fragment - so it is safe as a process-wide
 * singleton, and is the single seam tests drive to check the error overlay.
 */
object ErrorMessageChannel {
    private val _messages = MutableStateFlow<List<String>>(emptyList())

    /** Oldest first; the first entry is the one currently displayed. */
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private val hosts = AtomicInteger(0)

    /** Called by a UI host when it starts showing this channel. */
    fun attachHost() {
        hosts.incrementAndGet()
    }

    /** Called by the UI host when it goes away; the last host leaving drops anything pending. */
    fun detachHost() {
        if (hosts.decrementAndGet() <= 0) {
            hosts.set(0)
            _messages.value = emptyList()
        }
    }

    /** Queues [message] for display. Returns `false` (and queues nothing) if no host is attached. */
    fun post(message: String): Boolean {
        if (hosts.get() <= 0) return false
        _messages.update { it + message }
        return true
    }

    /** Removes the message currently being displayed (the oldest one). */
    fun dismissCurrent() {
        _messages.update { it.drop(1) }
    }

    /** Test seam: back to a clean, host-less state. */
    internal fun reset() {
        hosts.set(0)
        _messages.value = emptyList()
    }
}
