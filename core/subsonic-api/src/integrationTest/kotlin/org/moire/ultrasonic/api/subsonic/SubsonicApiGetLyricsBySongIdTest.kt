package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.LyricsLine

class SubsonicApiGetLyricsBySongIdTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse synced lyrics lines`() {
        mockWebServerRule.enqueueResponse("get_lyrics_by_song_id_ok.json")

        val response = client.api.getLyricsBySongId("song-1").execute()

        assertResponseSuccessful(response)
        with(response.body()!!.lyricsList.structuredLyrics.first()) {
            displayArtist `should be equal to` "Amorphis"
            displayTitle `should be equal to` "Alone"
            synced `should be equal to` true
            lines `should be equal to` listOf(
                LyricsLine(0, "Tear dimmed rememberance"),
                LyricsLine(3200, "In a womb of time"),
                LyricsLine(6800, "Breath upon me"),
                LyricsLine(10100, "Possessed by the")
            )
        }
    }

    @Test
    fun `Should pass id param in request`() {
        mockWebServerRule.assertRequestParam(
            responseResourceName = "get_lyrics_by_song_id_ok.json",
            expectedParam = "id=song-1"
        ) {
            client.api.getLyricsBySongId("song-1").execute()
        }
    }
}
