package org.moire.ultrasonic.adapters

import java.util.HashSet
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.Settings.SHOULD_USE_FOLDER_FOR_ARTIST_NAME
import org.moire.ultrasonic.util.Util.getGrandparent

class AlbumHeader(var entries: List<MusicDirectory.Child>, var name: String?) : Identifiable {
    // Optional album notes/review from the server's external metadata agent, only used by
    // AlbumDetailHeaderBinder (Album Detail). Set after construction since it arrives from a
    // separate, slower network call than the entries the header is built from.
    var notes: String? = null

    var isAllVideo: Boolean
        private set

    var totalDuration: Long
        private set

    var childCount = 0

    private val _artists: MutableSet<String>
    private val _grandParents: MutableSet<String>
    private val _genres: MutableSet<String>
    private val _years: MutableSet<Int>

    val artists: Set<String>
        get() = _artists

    val grandParents: Set<String>
        get() = _grandParents

    val genres: Set<String>
        get() = _genres

    val years: Set<Int>
        get() = _years

    private fun processGrandParents(entry: MusicDirectory.Child) {
        val grandParent = getGrandparent(entry.path)
        if (grandParent != null) {
            _grandParents.add(grandParent)
        }
    }

    @Suppress("NestedBlockDepth")
    private fun processEntries(list: List<MusicDirectory.Child>) {
        entries = list
        childCount = entries.size
        for (entry in entries) {
            if (!entry.isVideo) {
                isAllVideo = false
            }
            if (!entry.isDirectory) {
                if (SHOULD_USE_FOLDER_FOR_ARTIST_NAME) {
                    processGrandParents(entry)
                }
                if (entry.artist != null) {
                    val duration = entry.duration
                    if (duration != null) {
                        totalDuration += duration.toLong()
                    }
                    _artists.add(entry.artist!!)
                }
                if (entry.genre != null) {
                    _genres.add(entry.genre!!)
                }
                if (entry.year != null) {
                    _years.add(entry.year!!)
                }
            }
        }
    }

    init {
        _artists = HashSet()
        _grandParents = HashSet()
        _genres = HashSet()
        _years = HashSet()

        isAllVideo = true
        totalDuration = 0

        processEntries(entries)
    }

    override val id: String
        get() = "HEADER"

    override val longId: Long
        get() = -1L
}
