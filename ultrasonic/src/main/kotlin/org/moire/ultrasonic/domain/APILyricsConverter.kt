// Converts Lyrics entities from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APILyricsConverter")

package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Lyrics as APILyrics
import org.moire.ultrasonic.api.subsonic.models.LyricsList as APILyricsList
import org.moire.ultrasonic.api.subsonic.models.StructuredLyrics as APIStructuredLyrics

fun APILyrics.toDomainEntity(): Lyrics = Lyrics(
    artist = this@toDomainEntity.artist,
    title = this@toDomainEntity.title,
    text = this@toDomainEntity.text
)

fun APIStructuredLyrics.toDomainEntity(): Lyrics {
    val domainLines = lines.map { LyricsLine(it.start, it.value) }
    return Lyrics(
        artist = displayArtist,
        title = displayTitle,
        text = domainLines.joinToString("\n") { it.value },
        synced = synced,
        lines = domainLines
    )
}

// A server can return several structured entries (e.g. different languages/translations).
// Prefer a synced one; otherwise take the first plain entry. Null when the server has nothing.
fun APILyricsList.toDomainEntity(): Lyrics? {
    val best = structuredLyrics.firstOrNull { it.synced } ?: structuredLyrics.firstOrNull()
    return best?.toDomainEntity()
}
