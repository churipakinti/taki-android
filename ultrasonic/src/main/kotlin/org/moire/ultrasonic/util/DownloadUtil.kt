/*
 * DownloadUtil.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import androidx.fragment.app.Fragment
import java.util.LinkedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.MusicServiceFactory

/**
 * Retrieves a list of songs and adds them to the now playing list
 */
@Suppress("LongParameterList")
object DownloadUtil {

    fun justDownload(
        action: DownloadAction,
        fragment: Fragment,
        id: String? = null,
        name: String? = "",
        isShare: Boolean = false,
        isDirectory: Boolean = true,
        isArtist: Boolean = false,
        tracks: List<Track>? = null
    ) {
        // Launch the Job
        fragment.launchWithToast {
            val tracksToDownload: List<Track> = tracks
                ?: getTracksFromServerAsync(isArtist, id!!, isDirectory, name, isShare)

            // If we are just downloading tracks we don't need to add them to the controller
            when (action) {
                DownloadAction.DOWNLOAD -> DownloadService.downloadAsync(
                    tracksToDownload,
                    save = false,
                    updateSaveFlag = true
                )

                DownloadAction.PIN -> DownloadService.downloadAsync(
                    tracksToDownload,
                    save = true,
                    updateSaveFlag = true
                )

                DownloadAction.UNPIN -> DownloadService.unpinAsync(tracksToDownload)

                DownloadAction.DELETE -> DownloadService.deleteAsync(tracksToDownload)
            }

            // Return the string which should be displayed
            getToastString(action, fragment, tracksToDownload)
        }
    }

    suspend fun getTracksFromServerAsync(
        isArtist: Boolean,
        id: String,
        isDirectory: Boolean,
        name: String?,
        isShare: Boolean
    ): MutableList<Track> = withContext(Dispatchers.IO) {
        getTracksFromServer(isArtist, id, isDirectory, name, isShare)
    }

    suspend fun getTracksFromServer(
        isArtist: Boolean,
        id: String,
        isDirectory: Boolean,
        name: String?,
        isShare: Boolean
    ): MutableList<Track> {
        val musicService = MusicServiceFactory.getMusicService()
        val songs: MutableList<Track> = LinkedList()
        val root: MusicDirectory
        if (ActiveServerProvider.shouldUseId3Tags() && isArtist) {
            return getSongsForArtist(id)
        } else {
            if (isDirectory) {
                root = if (ActiveServerProvider.shouldUseId3Tags()) {
                    musicService.getAlbumAsDir(id, name, false)
                } else {
                    musicService.getMusicDirectory(id, name, false)
                }
            } else if (isShare) {
                root = MusicDirectory()
                val shares = musicService.getShares(true)
                // Filter the received shares by the given id, and get their entries
                val entries = shares.filter { it.id == id }.flatMap { it.getEntries() }
                root.addAll(entries)
            } else {
                root = musicService.getPlaylist(id, name!!)
            }
            getSongsRecursively(root, songs)
        }
        return songs
    }

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    @Throws(Exception::class)
    private fun getSongsRecursively(parent: MusicDirectory, songs: MutableList<Track>) {
        if (songs.size > Constants.MAX_SONGS_RECURSIVE) {
            return
        }
        for (song in parent.getTracks()) {
            if (!song.isVideo) {
                songs.add(song)
            }
        }
        val musicService = MusicServiceFactory.getMusicService()
        for ((id1, _, _, title) in parent.getAlbums()) {
            val root: MusicDirectory = if (ActiveServerProvider.shouldUseId3Tags()) {
                musicService.getAlbumAsDir(id1, title, false)
            } else {
                musicService.getMusicDirectory(id1, title, false)
            }
            getSongsRecursively(root, songs)
        }
    }

    @Throws(Exception::class)
    private fun getSongsForArtist(id: String): MutableList<Track> {
        val songs: MutableList<Track> = LinkedList()
        val musicService = MusicServiceFactory.getMusicService()
        val artist = musicService.getAlbumsOfArtist(id, "", false)
        for ((id1) in artist) {
            val albumDirectory = musicService.getAlbumAsDir(
                id1,
                "",
                false
            )
            for (song in albumDirectory.getTracks()) {
                if (!song.isVideo) {
                    songs.add(song)
                }
            }
        }
        return songs
    }

    private fun getToastString(
        action: DownloadAction,
        fragment: Fragment,
        tracksToDownload: List<Track>
    ): String = when (action) {
        DownloadAction.DOWNLOAD -> fragment.resources.getQuantityString(
            R.plurals.n_songs_to_be_downloaded,
            tracksToDownload.size,
            tracksToDownload.size
        )

        DownloadAction.UNPIN -> {
            fragment.resources.getQuantityString(
                R.plurals.n_songs_unpinned,
                tracksToDownload.size,
                tracksToDownload.size
            )
        }

        DownloadAction.PIN -> {
            fragment.resources.getQuantityString(
                R.plurals.n_songs_pinned,
                tracksToDownload.size,
                tracksToDownload.size
            )
        }

        DownloadAction.DELETE -> {
            fragment.resources.getQuantityString(
                R.plurals.n_songs_deleted,
                tracksToDownload.size,
                tracksToDownload.size
            )
        }
    }
}

enum class DownloadAction {
    DOWNLOAD,
    PIN,
    UNPIN,
    DELETE
}
