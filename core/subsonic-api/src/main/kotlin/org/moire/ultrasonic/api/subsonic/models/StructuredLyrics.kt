package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class LyricsList(
    val structuredLyrics: List<StructuredLyrics> = emptyList()
)

data class StructuredLyrics(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String = "xxx",
    val synced: Boolean = false,
    @JsonProperty("line")
    val lines: List<LyricsLine> = emptyList()
)

data class LyricsLine(
    val start: Long? = null,
    val value: String = ""
)
