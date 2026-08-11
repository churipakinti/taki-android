package org.moire.ultrasonic.api.subsonic.models

/**
 * A single entry of the OpenSubsonic extensions list (see
 * https://opensubsonic.netlify.app/docs/extensions/), e.g. `{"name": "songLyrics",
 * "versions": [1]}`. Plain Subsonic servers don't implement the endpoint that returns this at
 * all -- see [org.moire.ultrasonic.api.subsonic.SubsonicAPIDefinition.getOpenSubsonicExtensions].
 */
data class OpenSubsonicExtension(val name: String = "", val versions: List<Int> = emptyList())
