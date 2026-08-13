/*
 * APIAlbumConverter.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

// Converts Album entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIAlbumConverter")

package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Album
typealias DomainAlbum = org.moire.ultrasonic.domain.Album

fun Album.toDomainEntity(serverId: Int): DomainAlbum = Album(
    id = this@toDomainEntity.id,
    serverId = serverId,
    title = this@toDomainEntity.name ?: this@toDomainEntity.title,
    album = this@toDomainEntity.album,
    coverArt = this@toDomainEntity.coverArt,
    artist = this@toDomainEntity.artist,
    artistId = this@toDomainEntity.artistId,
    songCount = this@toDomainEntity.songCount.toLong(),
    duration = this@toDomainEntity.duration,
    created = this@toDomainEntity.created?.time,
    year = this@toDomainEntity.year,
    genre = this@toDomainEntity.genre,
    starred = this@toDomainEntity.starredDate.isNotEmpty(),
    // Pre-existing gap found while wiring Collections/Box Sets support: this converter never
    // copied discNumber from the DTO at all (every album silently got the domain default of 0),
    // even though AlbumID3 carries it. CollectionResolver needs a real per-album disc position
    // to order box-set discs (docs/TAKI_COLLECTIONS_BOXSETS_IMPLEMENTATION.md section 8), so this
    // has to be fixed here rather than worked around downstream.
    discNumber = this@toDomainEntity.discNumber,
    // AlbumID3 itself has no grouping field - only derivable when songList is populated, i.e.
    // from getAlbum.view, not from album lists.
    grouping = this@toDomainEntity.songList.firstNotNullOfOrNull {
        it.groupings.firstOrNull { g -> g.isNotBlank() }
    }
)

fun Album.toMusicDirectoryDomainEntity(serverId: Int): MusicDirectory = MusicDirectory().apply {
    addAll(this@toMusicDirectoryDomainEntity.songList.map { it.toTrackEntity(serverId) })
}

fun List<Album>.toDomainEntityList(serverId: Int): List<DomainAlbum> = this.map {
    it.toDomainEntity(serverId)
}
