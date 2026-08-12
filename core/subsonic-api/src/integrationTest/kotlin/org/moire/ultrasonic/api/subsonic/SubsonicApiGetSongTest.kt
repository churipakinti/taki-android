package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIClient] for getSong call.
 */
class SubsonicApiGetSongTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error responce`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.getSongSuspend("56")
        }

        response.song `should not be` null
        response.song `should be equal to` MusicDirectoryChild()
    }

    @Test
    fun `Should add id to request params`() = runTest {
        val id = "76"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_song_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.getSongSuspend(id)
        }
    }

    @Test
    fun `Should parse ok response`() = runTest {
        mockWebServerRule.enqueueResponse("get_song_ok.json")

        val response = client.api.getSongSuspend("6491")

        response.song `should be equal to` MusicDirectoryChild(
            id = "6491", parent = "6475",
            isDir = false, title = "Rock 'n' Roll Train", album = "Black Ice",
            artist = "AC/DC", track = 1, year = 2008, genre = "Hard Rock",
            coverArt = "6475", size = 7205451, contentType = "audio/mpeg", suffix = "mp3",
            duration = 261, bitRate = 219,
            path = "AC_DC/Black Ice/01 Rock 'n' Roll Train.mp3",
            isVideo = false, playCount = 3, discNumber = 1,
            created = parseDate("2016-10-23T15:31:20.000Z"),
            albumId = "618", artistId = "362", type = "music"
        )
    }
}
