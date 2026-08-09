/*
 * LyricsFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment.legacy

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.LyricsLineAdapter
import org.moire.ultrasonic.domain.Lyrics
import org.moire.ultrasonic.domain.LyricsLine
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.themeColor
import org.moire.ultrasonic.util.toastingExceptionHandler
import timber.log.Timber

/**
 * Displays the lyrics of a song. Prefers time-synced lyrics (OpenSubsonic's
 * `getLyricsBySongId`), highlighting and centering the currently-sung line as playback
 * progresses; falls back to the legacy artist/title-based `getLyrics` (plain text) when the
 * server or track doesn't have synced data, still shown as large, one-line-per-row text.
 */
class LyricsFragment :
    Fragment(),
    RefreshableFragment {
    private var artistView: TextView? = null
    private var titleView: TextView? = null
    private var syncIcon: ImageView? = null
    private var recyclerView: RecyclerView? = null
    override var swipeRefresh: SwipeRefreshLayout? = null

    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val navArgs by navArgs<LyricsFragmentArgs>()

    private var adapter: LyricsLineAdapter? = null
    private var lyricsLines: List<LyricsLine> = emptyList()
    private var isSynced = false
    private var executorService: ScheduledExecutorService? = null

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
        syncIcon = view.findViewById(R.id.lyrics_sync_icon)
        recyclerView = view.findViewById(R.id.lyrics_recycler_view)
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        load()
    }

    override fun onResume() {
        super.onResume()
        if (isSynced) startPositionTracking()
    }

    override fun onPause() {
        super.onPause()
        executorService?.shutdown()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executorService?.shutdown()
        recyclerView = null
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler()
        ) {
            val result = withContext(Dispatchers.IO) { fetchLyrics() }
            swipeRefresh?.isRefreshing = false
            withContext(Dispatchers.Main) { display(result) }
        }
    }

    private fun fetchLyrics(): Lyrics? {
        val musicService = getMusicService()

        val synced = runCatching { musicService.getLyricsBySongId(navArgs.id) }
            .getOrNull()

        if (synced != null && synced.lines.isNotEmpty()) return synced

        val legacy = runCatching { musicService.getLyrics(navArgs.artist, navArgs.title) }
            .getOrNull()

        val legacyText = legacy?.text
        if (legacy?.artist == null || legacyText.isNullOrBlank()) return null

        return legacy.copy(
            lines = legacyText.lines().filter { it.isNotBlank() }
                .map { LyricsLine(start = null, value = it) }
        )
    }

    private fun display(result: Lyrics?) {
        if (result == null || result.lines.isEmpty()) {
            titleView?.setText(R.string.lyrics_nomatch)
            return
        }

        titleView?.text = result.title
        artistView?.text = result.artist
        lyricsLines = result.lines
        isSynced = result.synced && result.lines.any { it.start != null }
        showSyncStatus(isSynced)

        val newAdapter = LyricsLineAdapter(lyricsLines)
        adapter = newAdapter
        recyclerView?.adapter = newAdapter

        if (isSynced) startPositionTracking()
    }

    private fun showSyncStatus(synced: Boolean) {
        val icon = syncIcon ?: return
        icon.isVisible = true
        icon.setImageResource(
            if (synced) R.drawable.ic_lyrics_synced else R.drawable.ic_lyrics_unsynced
        )
        icon.imageTintList = ColorStateList.valueOf(
            requireContext().themeColor(
                if (synced) {
                    androidx.appcompat.R.attr.colorPrimary
                } else {
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                }
            )
        )
        icon.contentDescription = getString(
            if (synced) R.string.lyrics_synced else R.string.lyrics_not_synced
        )
    }

    private fun startPositionTracking() {
        executorService?.shutdown()
        val handler = Handler(Looper.getMainLooper())
        val executor = Executors.newSingleThreadScheduledExecutor()
        executor.scheduleWithFixedDelay(
            { handler.post { updateActiveLine() } },
            0L,
            300L,
            TimeUnit.MILLISECONDS
        )
        executorService = executor
    }

    private fun updateActiveLine() {
        val currentAdapter = adapter ?: return
        val positionMs = mediaPlayerManager.playerPosition.toLong()

        var index = -1
        for (i in lyricsLines.indices) {
            val start = lyricsLines[i].start ?: continue
            if (start <= positionMs) index = i else break
        }

        if (index == currentAdapter.activeIndex || index < 0) return
        currentAdapter.setActiveIndex(index)
        scrollToActiveLine(index)
    }

    private fun scrollToActiveLine(position: Int) {
        val list = recyclerView ?: return
        val layoutManager = list.layoutManager as? LinearLayoutManager ?: return
        val targetView = layoutManager.findViewByPosition(position)

        if (targetView != null) {
            val listCenterY = list.height / 2
            val viewCenterY = targetView.top + targetView.height / 2
            list.smoothScrollBy(0, viewCenterY - listCenterY)
        } else {
            layoutManager.scrollToPositionWithOffset(position, list.height / 2)
        }
    }
}
