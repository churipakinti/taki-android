package org.moire.ultrasonic.domain

data class ArtistInfo(
    val biography: String = "",
    val musicBrainzId: String = "",
    val lastFmUrl: String = "",
    val smallImageUrl: String = "",
    val mediumImageUrl: String = "",
    val largeImageUrl: String = "",
    val similarArtists: List<Artist> = emptyList()
)
