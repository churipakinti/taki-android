/*
 * ContextMenuUtil.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.GenericEntry
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.ArtistRadioQueueBuilder
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.TrackRadioQueueBuilder
import org.moire.ultrasonic.util.Util.navigateToCurrent
import org.moire.ultrasonic.util.Util.toast

object ContextMenuUtil {

    /*
     * Callback for menu items of collections (albums, artists etc)
     */
    fun handleContextMenu(
        menuItem: MenuItem,
        item: Identifiable,
        isArtist: Boolean,
        mediaPlayerManager: MediaPlayerManager,
        fragment: Fragment
    ): Boolean {
        when (menuItem.itemId) {
            R.id.menu_play_now ->
                mediaPlayerManager.playTracksAndToast(
                    fragment = fragment,
                    insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
                    id = item.id,
                    isArtist = isArtist
                )

            R.id.menu_play_next ->
                mediaPlayerManager.playTracksAndToast(
                    fragment = fragment,
                    insertionMode = MediaPlayerManager.InsertionMode.AFTER_CURRENT,
                    id = item.id,
                    isArtist = isArtist
                )

            R.id.menu_play_last ->
                mediaPlayerManager.playTracksAndToast(
                    fragment = fragment,
                    insertionMode = MediaPlayerManager.InsertionMode.APPEND,
                    id = item.id,
                    isArtist = isArtist
                )

            R.id.menu_start_radio -> {
                if (!isArtist) return false
                val artistName = (item as? GenericEntry)?.name
                fragment.lifecycleScope.launch(
                    fragment.toastingExceptionHandler(
                        fragment.getString(R.string.artist_radio_error)
                    )
                ) {
                    val queue = ArtistRadioQueueBuilder(MusicServiceFactory.getMusicService())
                        .build(
                            artistId = item.id,
                            artistName = artistName
                        )
                    if (queue.isEmpty()) {
                        fragment.toast(R.string.artist_radio_empty)
                        return@launch
                    }
                    if (queue.size < ArtistRadioQueueBuilder.TARGET_SIZE) {
                        fragment.toast(
                            fragment.getString(R.string.artist_radio_short, queue.size)
                        )
                    }
                    mediaPlayerManager.suggestedPlaylistName = fragment.getString(
                        R.string.artist_radio_playlist_name,
                        artistName ?: item.id
                    )
                    mediaPlayerManager.addToPlaylist(
                        songs = queue,
                        autoPlay = true,
                        shuffle = false,
                        insertionMode = MediaPlayerManager.InsertionMode.CLEAR
                    )
                    fragment.navigateToCurrent()
                }
            }

            R.id.menu_download ->
                DownloadUtil.justDownload(
                    action = DownloadAction.DOWNLOAD,
                    fragment = fragment,
                    id = item.id,
                    isArtist = isArtist
                )

            else -> return false
        }
        return true
    }

    fun handleContextMenuTracks(
        menuItem: MenuItem,
        tracks: List<Track>,
        mediaPlayerManager: MediaPlayerManager,
        fragment: Fragment
    ): Boolean {
        when (menuItem.itemId) {
            R.id.song_menu_play_now -> {
                mediaPlayerManager.playTracksAndToast(
                    fragment = fragment,
                    insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
                    tracks = tracks
                )
            }

            R.id.song_menu_play_next -> {
                mediaPlayerManager.playTracksAndToast(
                    fragment = fragment,
                    insertionMode = MediaPlayerManager.InsertionMode.AFTER_CURRENT,
                    tracks = tracks
                )
            }

            R.id.song_menu_play_last -> {
                mediaPlayerManager.playTracksAndToast(
                    fragment = fragment,
                    insertionMode = MediaPlayerManager.InsertionMode.APPEND,
                    tracks = tracks
                )
            }

            R.id.song_menu_start_radio -> {
                val seed = tracks.firstOrNull() ?: return false
                fragment.lifecycleScope.launch(
                    fragment.toastingExceptionHandler(fragment.getString(R.string.song_radio_error))
                ) {
                    val queue = TrackRadioQueueBuilder(MusicServiceFactory.getMusicService())
                        .build(seed)
                    if (queue.size <= 1) {
                        fragment.toast(R.string.song_radio_empty)
                        return@launch
                    }
                    if (queue.size < TrackRadioQueueBuilder.TARGET_SIZE) {
                        fragment.toast(
                            fragment.getString(R.string.song_radio_short, queue.size)
                        )
                    }
                    mediaPlayerManager.suggestedPlaylistName = fragment.getString(
                        R.string.song_radio_playlist_name,
                        seed.title ?: seed.name ?: seed.id
                    )
                    mediaPlayerManager.addToPlaylist(
                        songs = queue,
                        autoPlay = true,
                        shuffle = false,
                        insertionMode = MediaPlayerManager.InsertionMode.CLEAR
                    )
                    fragment.navigateToCurrent()
                }
            }

            R.id.song_menu_download -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.DOWNLOAD,
                    fragment = fragment,
                    tracks = tracks
                )
            }

            R.id.song_menu_delete -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.DELETE,
                    fragment = fragment,
                    tracks = tracks
                )
            }

            else -> return false
        }
        return true
    }
}
