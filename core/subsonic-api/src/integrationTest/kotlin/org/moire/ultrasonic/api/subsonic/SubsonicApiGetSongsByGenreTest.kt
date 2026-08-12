package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild

/**
 * Integration test for [SubsonicAPIDefinition.getSongsByGenreSuspend] call.
 */
class SubsonicApiGetSongsByGenreTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.getSongsByGenreSuspend("Metal")
        }

        response.songsList `should be equal to` emptyList()
    }

    @Test
    fun `Should handle ok response`() = runTest {
        mockWebServerRule.enqueueResponse("get_songs_by_genre_ok.json")

        val response = client.api.getSongsByGenreSuspend("Trance")

        response.songsList.size `should be equal to` 2
        with(response.songsList) {
            this[0] `should be equal to` MusicDirectoryChild(
                id = "575", parent = "576", isDir = false,
                title = "Time Machine (Vadim Zhukov Remix)", album = "668",
                artist = "Tasadi", year = 2008, genre = "Trance", size = 22467672,
                contentType = "audio/mpeg", suffix = "mp3", duration = 561, bitRate = 320,
                path = "Tasadi/668/00 Time Machine (Vadim Zhukov Remix).mp3",
                isVideo = false, playCount = 0, created = parseDate("2016-10-23T21:58:29.000Z"),
                albumId = "0", artistId = "0", type = "music"
            )
            this[1] `should be equal to` MusicDirectoryChild(
                id = "621", parent = "622", isDir = false,
                title = "My Heart (Vadim Zhukov Remix)", album = "668",
                artist = "DJ Polyakov PPK Feat Kate Cameron", year = 2009, genre = "Trance",
                size = 26805932, contentType = "audio/mpeg", suffix = "mp3", duration = 670,
                bitRate = 320,
                path = "DJ Polyakov PPK Feat Kate Cameron/668/00 My Heart (Vadim Zhukov " +
                    "Remix).mp3",
                isVideo = false, playCount = 2,
                created = parseDate("2016-10-23T21:58:29.000Z"),
                albumId = "5", artistId = "4", type = "music"
            )
        }
    }

    @Test
    fun `Should pass genre in request param`() = runTest {
        val genre = "Rock"
        mockWebServerRule.assertRequestParamSuspend(expectedParam = "genre=$genre") {
            client.api.getSongsByGenreSuspend(genre = genre)
        }
    }

    @Test
    fun `Should pass count in request param`() = runTest {
        val count = 494

        mockWebServerRule.assertRequestParamSuspend(expectedParam = "count=$count") {
            client.api.getSongsByGenreSuspend("Trance", count = count)
        }
    }

    @Test
    fun `Should pass offset in request param`() = runTest {
        val offset = 31

        mockWebServerRule.assertRequestParamSuspend(expectedParam = "offset=$offset") {
            client.api.getSongsByGenreSuspend("Trance", offset = offset)
        }
    }

    @Test
    fun `Should pass music folder id in request param`() = runTest {
        val musicFolderId = "1010"

        mockWebServerRule.assertRequestParamSuspend(
            expectedParam = "musicFolderId=$musicFolderId"
        ) {
            client.api.getSongsByGenreSuspend("Trance", musicFolderId = musicFolderId)
        }
    }
}
