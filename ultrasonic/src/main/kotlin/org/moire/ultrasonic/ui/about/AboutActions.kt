/*
 * AboutActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.about

/**
 * Callbacks [AboutScreen] needs from its host. [onWebsite] mirrors the legacy `help_webpage`
 * button, which was permanently `android:visibility="gone"` in production and never toggled by
 * any code path - kept wired here (hide-don't-delete) rather than removed, even though
 * [AboutScreen] does not currently render a control for it either.
 */
data class AboutActions(
    val onBack: () -> Unit,
    val onWebsite: () -> Unit,
    val onReportBug: () -> Unit,
) {
    companion object {
        val Noop = AboutActions(onBack = {}, onWebsite = {}, onReportBug = {})
    }
}
