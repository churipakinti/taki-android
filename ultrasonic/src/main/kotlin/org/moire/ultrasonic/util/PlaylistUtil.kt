/*
 * PlaylistUtil.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicServiceFactory

/** Adding one or more tracks to an existing server playlist (as opposed to creating a new one). */
object PlaylistUtil {

    suspend fun getPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        MusicServiceFactory.getMusicService().getPlaylists(false)
    }

    /**
     * Subsonic has no "append song to playlist" endpoint -- `createPlaylist` is also how an
     * existing playlist's song list is overwritten when a `playlistId` is supplied, so adding
     * means fetching the playlist's current songs and resubmitting them plus the new ones.
     */
    suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>) = withContext(Dispatchers.IO) {
        val service = MusicServiceFactory.getMusicService()
        val existing = service.getPlaylist(playlist.id, playlist.name).getTracks()
        service.createPlaylist(playlist.id, playlist.name, existing + tracks)
    }
}
