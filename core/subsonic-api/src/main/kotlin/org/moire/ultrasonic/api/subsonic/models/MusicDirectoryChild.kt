package org.moire.ultrasonic.api.subsonic.models

import java.util.Calendar

data class MusicDirectoryChild(
    val id: String = "",
    val parent: String = "",
    val isDir: Boolean = false,
    val title: String = "",
    val album: String = "",
    val artist: String = "",
    val track: Int = -1,
    val year: Int? = null,
    val genre: String = "",
    val coverArt: String = "",
    val size: Long = -1,
    val contentType: String = "",
    val suffix: String = "",
    val transcodedContentType: String = "",
    val transcodedSuffix: String = "",
    val duration: Int = -1,
    val bitRate: Int = -1,
    val path: String = "",
    val isVideo: Boolean = false,
    val playCount: Int = 0,
    // Last-played timestamp (OpenSubsonic/Navidrome expose this on <song>, vanilla Subsonic
    // servers omit it). Used for Mix diario v1.1 history signals.
    val played: Calendar? = null,
    val discNumber: Int = -1,
    val created: Calendar? = null,
    val albumId: String = "",
    val artistId: String = "",
    val type: String = "",
    val starred: Calendar? = null,
    val streamId: String = "",
    val channelId: String = "",
    val description: String = "",
    val status: String = "",
    val publishDate: Calendar? = null,
    val userRating: Int? = null,
    val averageRating: Float? = null,
    // OpenSubsonic extension (Navidrome exposes it on <song>, confirmed via a live response
    // capture 2026-08-13: "groupings":[]). Maps the ID3 "content group"/GROUPING tag, used to
    // detect box-set membership. Not present at all on the AlbumID3 response, only here.
    val groupings: List<String> = emptyList()
)
