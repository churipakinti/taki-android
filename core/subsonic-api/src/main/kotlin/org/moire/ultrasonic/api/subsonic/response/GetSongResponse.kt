package org.moire.ultrasonic.api.subsonic.response

import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

class GetSongResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?,
    val song: MusicDirectoryChild = MusicDirectoryChild()
) : SubsonicResponse(status, version, error)
