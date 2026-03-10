/*
 * ContextMenuUtil.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.view.MenuItem
import androidx.fragment.app.Fragment
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.subsonic.ShareHandler

object ContextMenuUtil : KoinComponent {

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

            R.id.menu_pin ->
                DownloadUtil.justDownload(
                    action = DownloadAction.PIN,
                    fragment = fragment,
                    id = item.id,
                    isArtist = isArtist
                )

            R.id.menu_unpin ->
                DownloadUtil.justDownload(
                    action = DownloadAction.UNPIN,
                    fragment = fragment,
                    id = item.id,
                    isArtist = isArtist
                )

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

            R.id.song_menu_pin -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.PIN,
                    fragment = fragment,
                    tracks = tracks
                )
            }

            R.id.song_menu_unpin -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.UNPIN,
                    fragment = fragment,
                    tracks = tracks
                )
            }

            R.id.song_menu_download -> {
                DownloadUtil.justDownload(
                    action = DownloadAction.DOWNLOAD,
                    fragment = fragment,
                    tracks = tracks
                )
            }

            R.id.song_menu_share -> {
                val shareHandler: ShareHandler by inject()
                shareHandler.createShare(
                    fragment = fragment,
                    tracks = tracks
                )
            }

            else -> return false
        }
        return true
    }
}
