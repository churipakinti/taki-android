package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetTopSongsResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?,
    @JsonProperty("topSongs") private val topSongs: TopSongsWrapper = TopSongsWrapper()
) : SubsonicResponse(status, version, error) {
    val songsList get() = topSongs.songs
}

data class TopSongsWrapper(
    @JsonProperty("song") val songs: List<MusicDirectoryChild> = emptyList()
)
