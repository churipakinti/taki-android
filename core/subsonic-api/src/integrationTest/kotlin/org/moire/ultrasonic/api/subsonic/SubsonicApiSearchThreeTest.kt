package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.SearchThreeResult

/**
 * Integration test for [SubsonicAPIClient] for search3 call.
 */
class SubsonicApiSearchThreeTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error response`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.search3Suspend("some-query")
        }

        response.searchResult `should not be` null
        response.searchResult `should be equal to` SearchThreeResult()
    }

    @Test
    fun `Should parse ok response`() = runTest {
        mockWebServerRule.enqueueResponse("search3_ok.json")

        val response = client.api.search3Suspend("some-query")

        with(response.searchResult) {
            artistList.size `should be equal to` 1
            artistList[0] `should be equal to` Artist(
                id = "505",
                name = "The Prodigy",
                coverArt = "ar-505",
                albumCount = 5
            )
            albumList.size `should be equal to` 1
            albumList[0] `should be equal to` Album(
                id = "855",
                name = "Always Outnumbered, Never Outgunned",
                artist = "The Prodigy", artistId = "505", coverArt = "al-855", songCount = 12,
                duration = 3313, created = parseDate("2016-10-23T20:57:27.000Z"),
                year = 2004, genre = "Electronic"
            )
            songList.size `should be equal to` 1
            songList[0] `should be equal to` MusicDirectoryChild(
                id = "5831", parent = "5766",
                isDir = false,
                title = "You'll Be Under My Wheels", album = "Need for Speed Most Wanted",
                artist = "The Prodigy", track = 17, year = 2005, genre = "Rap",
                coverArt = "5766", size = 5607024, contentType = "audio/mpeg",
                suffix = "mp3", duration = 233, bitRate = 192,
                path = "Compilations/Need for Speed Most Wanted/17 You'll Be Under My Wheels" +
                    ".mp3",
                isVideo = false, playCount = 0, discNumber = 1,
                created = parseDate("2016-10-23T20:09:02.000Z"), albumId = "568",
                artistId = "505", type = "music"
            )
        }
    }

    @Test
    fun `Should pass query as request param`() = runTest {
        val query = "some-wip-query"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search3_ok.json",
            expectedParam = "query=$query"
        ) {
            client.api.search3Suspend(query = query)
        }
    }

    @Test
    fun `Should pass artist count as request param`() = runTest {
        val artistCount = 67

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search3_ok.json",
            expectedParam = "artistCount=$artistCount"
        ) {
            client.api.search3Suspend("some", artistCount = artistCount)
        }
    }

    @Test
    fun `Should pass artist offset as request param`() = runTest {
        val artistOffset = 34

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search3_ok.json",
            expectedParam = "artistOffset=$artistOffset"
        ) {
            client.api.search3Suspend("some", artistOffset = artistOffset)
        }
    }

    @Test
    fun `Should pass album count as request param`() = runTest {
        val albumCount = 21

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search3_ok.json",
            expectedParam = "albumCount=$albumCount"
        ) {
            client.api.search3Suspend("some", albumCount = albumCount)
        }
    }

    @Test
    fun `Should pass album offset as request param`() = runTest {
        val albumOffset = 43

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search3_ok.json",
            expectedParam = "albumOffset=$albumOffset"
        ) {
            client.api.search3Suspend("some", albumOffset = albumOffset)
        }
    }

    @Test
    fun `Should pass song count as request param`() = runTest {
        val songCount = 15

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search3_ok.json",
            expectedParam = "songCount=$songCount"
        ) {
            client.api.search3Suspend("some", songCount = songCount)
        }
    }

    @Test
    fun `Should pass song offset as request param`() = runTest {
        val songOffset = 40

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search3_ok.json",
            expectedParam = "songOffset=$songOffset"
        ) {
            client.api.search3Suspend("some", songOffset = songOffset)
        }
    }

    @Test
    fun `Should pass music folder id as request param`() = runTest {
        val musicFolderId = "43"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search3_ok.json",
            expectedParam = "musicFolderId=$musicFolderId"
        ) {
            client.api.search3Suspend("some", musicFolderId = musicFolderId)
        }
    }
}
