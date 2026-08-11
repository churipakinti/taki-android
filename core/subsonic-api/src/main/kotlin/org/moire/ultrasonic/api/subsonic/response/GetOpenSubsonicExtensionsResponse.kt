package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.OpenSubsonicExtension

class GetOpenSubsonicExtensionsResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?,
    @JsonProperty("openSubsonicExtensions")
    val openSubsonicExtensions: List<OpenSubsonicExtension> = emptyList()
) : SubsonicResponse(status, version, error)
