package org.moire.ultrasonic.domain

data class AlbumInfo(
    val notes: String = "",
    val musicBrainzId: String = "",
    val lastFmUrl: String = "",
    val smallImageUrl: String = "",
    val mediumImageUrl: String = "",
    val largeImageUrl: String = ""
)
