package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class ArtistInfo(
    val biography: String = "",
    val musicBrainzId: String = "",
    val lastFmUrl: String = "",
    val smallImageUrl: String = "",
    val mediumImageUrl: String = "",
    val largeImageUrl: String = "",
    @JsonProperty("similarArtist")
    val similarArtists: List<Artist> = emptyList()
)
