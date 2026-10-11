/*
 * ListLoadFeedback.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import org.moire.ultrasonic.R

/**
 * Secondary feedback for a list refresh that failed while the screen still has usable content
 * (issue #31). The content stays on screen; this raises exactly one pending message through
 * [ErrorMessageChannel] (the app's existing error sheet). A first load with nothing to show uses
 * the inline error state instead, never this.
 */
object ListLoadFeedback {
    fun refreshFailed(context: Context) {
        val text = context.getString(R.string.list_refresh_failed)
        // Pull-to-refresh spam must not stack identical sheets.
        if (text in ErrorMessageChannel.messages.value) return
        ErrorMessageChannel.post(text)
    }
}
