@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Lyrics
import org.moire.ultrasonic.api.subsonic.models.LyricsLine as APILyricsLine
import org.moire.ultrasonic.api.subsonic.models.LyricsList
import org.moire.ultrasonic.api.subsonic.models.StructuredLyrics

/**
 * Unit test for extension functions in [APILyricsConverter.kt] file.
 */
class APILyricsConverterTest {
    @Test
    fun `Should convert Lyrics entity to domain`() {
        val entity = Lyrics(artist = "some-artist", title = "some-title", text = "song-text")

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            artist `should be equal to` entity.artist
            title `should be equal to` entity.title
            text `should be equal to` entity.text
        }
    }

    @Test
    fun `Should convert StructuredLyrics entity to domain with joined text`() {
        val entity = StructuredLyrics(
            displayArtist = "some-artist",
            displayTitle = "some-title",
            synced = true,
            lines = listOf(
                APILyricsLine(start = 0, value = "line one"),
                APILyricsLine(start = 1000, value = "line two")
            )
        )

        val convertedEntity = entity.toDomainEntity()

        with(convertedEntity) {
            artist `should be equal to` "some-artist"
            title `should be equal to` "some-title"
            text `should be equal to` "line one\nline two"
            synced `should be equal to` true
            lines.size `should be equal to` 2
            lines[0].start `should be equal to` 0L
            lines[1].start `should be equal to` 1000L
        }
    }

    @Test
    fun `Should prefer synced entry when picking best structured lyrics`() {
        val unsynced = StructuredLyrics(synced = false, lines = listOf(APILyricsLine(value = "a")))
        val synced = StructuredLyrics(synced = true, lines = listOf(APILyricsLine(value = "b")))
        val list = LyricsList(structuredLyrics = listOf(unsynced, synced))

        val convertedEntity = list.toDomainEntity()

        convertedEntity?.synced `should be equal to` true
        convertedEntity?.text `should be equal to` "b"
    }

    @Test
    fun `Should return null when structured lyrics list is empty`() {
        val list = LyricsList(structuredLyrics = emptyList())

        list.toDomainEntity() `should be equal to` null
    }
}
