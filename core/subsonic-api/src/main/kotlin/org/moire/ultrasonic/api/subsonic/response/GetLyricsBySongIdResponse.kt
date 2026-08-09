package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.LyricsList

class GetLyricsBySongIdResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?,
    @JsonProperty("lyricsList") val lyricsList: LyricsList = LyricsList()
) : SubsonicResponse(status, version, error)
