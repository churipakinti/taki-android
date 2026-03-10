/*
 * LyricsFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.toastingExceptionHandler
import timber.log.Timber

/**
 * Displays the lyrics of a song
 */
class LyricsFragment :
    Fragment(),
    RefreshableFragment {
    private var artistView: TextView? = null
    private var titleView: TextView? = null
    private var textView: TextView? = null
    override var swipeRefresh: SwipeRefreshLayout? = null

    private val navArgs by navArgs<LyricsFragmentArgs>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.lyrics, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("Lyrics set title")
        setTitle(this, R.string.download_menu_lyrics)
        swipeRefresh = view.findViewById(R.id.lyrics_refresh)
        swipeRefresh?.isEnabled = false
        artistView = view.findViewById(R.id.lyrics_artist)
        titleView = view.findViewById(R.id.lyrics_title)
        textView = view.findViewById(R.id.lyrics_text)
        load()
    }
    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val result = withContext(Dispatchers.IO) {
                val musicService = getMusicService()
                musicService.getLyrics(navArgs.artist, navArgs.title)!!
            }
            swipeRefresh?.isRefreshing = false
            withContext(Dispatchers.Main) {
                if (result.artist != null) {
                    artistView?.text = result.artist
                    titleView?.text = result.title
                    textView?.text = result.text
                } else {
                    artistView?.setText(R.string.lyrics_nomatch)
                }
            }
        }
    }
}
