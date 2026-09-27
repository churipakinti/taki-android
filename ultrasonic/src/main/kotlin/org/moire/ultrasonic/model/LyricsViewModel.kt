/*
 * LyricsViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.domain.Lyrics
import org.moire.ultrasonic.domain.LyricsLine
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.lyrics.LyricsContent
import org.moire.ultrasonic.ui.lyrics.LyricsLineUi
import org.moire.ultrasonic.ui.lyrics.LyricsUiState

/**
 * Owns the Compose Lyrics state (issue #10 phase 4K3) as one [StateFlow]<[LyricsUiState]> - a
 * read-only projection of what the legacy `LyricsFragment` fetched: OpenSubsonic
 * `getLyricsBySongId` first (time-synced), then the artist/title `getLyrics` (plain text).
 * Fetching stays in the music service; this class only sequences the calls, maps the result and
 * follows the playing track - [show] with a new track id cancels the in-flight load and starts
 * over, so a previous track's lyrics are never shown for the current one.
 */
class LyricsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** The two music-service lookups, off the main thread. Test seam. */
    internal var lyricsLoader: suspend (id: String, artist: String, title: String) -> Lyrics? =
        { id, artist, title ->
            withContext(Dispatchers.IO) {
                val service = MusicServiceFactory.getMusicService()
                fetchLyrics(
                    bySongId = { service.getLyricsBySongId(id) },
                    byArtistTitle = { service.getLyrics(artist, title) },
                )
            }
        }

    /** Show [trackId]'s lyrics. A no-op when it is already the shown (or loading) track. */
    fun show(trackId: String, artist: String?, title: String) {
        val current = _uiState.value
        if (current.trackId == trackId && current.content !is LyricsContent.Error) return
        load(trackId, artist, title)
    }

    fun retry() {
        val s = _uiState.value
        val id = s.trackId ?: return
        load(id, s.artist, s.title)
    }

    private fun load(trackId: String, artist: String?, title: String) {
        loadJob?.cancel()
        _uiState.value = LyricsUiState(trackId, title, artist, LyricsContent.Loading)
        loadJob = viewModelScope.launch {
            val content = try {
                lyricsLoader(trackId, artist.orEmpty(), title).toContent()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                LyricsContent.Error
            }
            _uiState.update { if (it.trackId == trackId) it.copy(content = content) else it }
        }
    }
}

/**
 * The legacy two-step lookup, ported: a synced result wins; otherwise the plain artist/title
 * lookup, split on line breaks (blank lines kept - they are stanza breaks). Returns null when the
 * server has nothing, and throws only when *every* lookup that was tried failed, so "no lyrics"
 * and "couldn't reach the server" stay distinguishable (the legacy screen swallowed both). Unlike
 * the legacy screen, a non-null [Lyrics.artist] on the result is not required - that check never
 * guarded anything meaningful (the field is just an echo of the query), only whether there is text.
 */
internal fun fetchLyrics(bySongId: () -> Lyrics?, byArtistTitle: () -> Lyrics?): Lyrics? {
    var failure: Exception? = null
    var succeeded = false

    val synced = try {
        bySongId().also { succeeded = true }
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        failure = e
        null
    }
    if (synced != null && synced.lines.isNotEmpty()) return synced

    val legacy = try {
        byArtistTitle().also { succeeded = true }
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        failure = e
        null
    }
    if (!succeeded && failure != null) throw failure

    val text = legacy?.text
    if (text.isNullOrBlank()) return null
    return legacy.copy(lines = text.lines().map { LyricsLine(start = null, value = it) })
}

/** Maps a fetched [Lyrics] to what the screen renders; timestamps are never invented. */
internal fun Lyrics?.toContent(): LyricsContent {
    if (this == null) return LyricsContent.Empty
    val isSynced = synced && lines.any { it.start != null }
    if (isSynced) {
        var previous = 0L
        val mapped = lines.map { line ->
            previous = maxOf(previous, line.start ?: previous)
            LyricsLineUi(previous, line.value)
        }
        return LyricsContent.Synced(mapped)
    }
    val plain = lines.map { it.value.trimEnd() }
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
    return if (plain.isEmpty()) LyricsContent.Empty else LyricsContent.Plain(plain)
}
