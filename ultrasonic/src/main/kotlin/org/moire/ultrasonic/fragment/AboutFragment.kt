/*
 * AboutFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.about.AboutActions
import org.moire.ultrasonic.ui.about.AboutScreen
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.Util.getVersionName

/**
 * About (post-issue-#10 residual migration, phase 5A1): a thin Compose host. Owns the
 * nav-graph boundary (the unchanged `aboutFragment` destination, no arguments) and the two
 * Android-specific actions - launching the website/report-bug URLs via `Intent.ACTION_VIEW`,
 * exactly as the legacy Fragment did. Everything visible, including the back+title header, is
 * [AboutScreen].
 */
class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            TakiTheme {
                AboutScreen(
                    versionName = getVersionName(requireContext()).orEmpty(),
                    actions = AboutActions(
                        onBack = { findNavController().navigateUp() },
                        onWebsite = { openUrl(R.string.about_webpage_url) },
                        onReportBug = { openUrl(R.string.about_report_url) },
                    ),
                )
            }
        }
    }

    private fun openUrl(urlRes: Int) {
        startActivity(Intent(Intent.ACTION_VIEW, getString(urlRes).toUri()))
    }
}
