/*
 * MusicDirectory.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.domain

import java.util.Date

class MusicDirectory : ArrayList<MusicDirectory.Child>() {
    var name: String? = null

    @JvmOverloads
    fun getChildren(includeDirs: Boolean = true, includeFiles: Boolean = true): List<Child> {
        if (includeDirs && includeFiles) {
            return toList()
        }

        return filter { it.isDirectory && includeDirs || !it.isDirectory && includeFiles }
    }

    fun getTracks(): List<Track> = mapNotNull {
        it as? Track
    }

    fun getAlbums(): List<Album> = mapNotNull {
        it as? Album
    }

    abstract class Child : GenericEntry() {
        abstract override var id: String
        abstract var serverId: Int
        abstract var parent: String?
        abstract var isDirectory: Boolean
        abstract var album: String?
        abstract var title: String?
        abstract override val name: String?
        abstract var discNumber: Int?
        abstract var coverArt: String?
        abstract var songCount: Long?
        abstract var created: Date?
        abstract var artist: String?
        abstract var artistId: String?
        abstract var duration: Int?
        abstract var year: Int?
        abstract var genre: String?
        abstract var starred: Boolean
        abstract var path: String?
        abstract var closeness: Int
        abstract var isVideo: Boolean

        // Collections/Box Sets (docs/TAKI_COLLECTIONS_BOXSETS_IMPLEMENTATION.md). Sourced from
        // the OpenSubsonic "groupings" array on Song responses - the Subsonic AlbumID3 response
        // has no such field, so an Album's grouping is only known once at least one of its
        // tracks has actually been fetched (see CachedMusicService.getAlbumAsDir). Null means
        // "not yet discovered", not "confirmed no collection" - see CollectionResolver.
        abstract var grouping: String?
    }
}
