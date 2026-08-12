package org.moire.ultrasonic.api.subsonic

import java.util.Calendar
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.api.subsonic.models.SearchResult

/**
 * Integration test for [SubsonicAPIClient] for search call.
 */
class SubsonicApiSearchTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error response`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.searchSuspend()
        }

        response.searchResult `should not be` null
        response.searchResult `should be equal to` SearchResult()
    }

    @Test
    fun `Should parse ok response`() = runTest {
        mockWebServerRule.enqueueResponse("search_ok.json")

        val response = client.api.searchSuspend()

        with(response.searchResult) {
            offset `should be equal to` 10
            totalHits `should be equal to` 53
            matchList.size `should be equal to` 1
            matchList[0] `should be equal to` MusicDirectoryChild(
                id = "5831", parent = "5766",
                isDir = false, title = "You'll Be Under My Wheels",
                album = "Need for Speed Most Wanted", artist = "The Prodigy",
                track = 17, year = 2005, genre = "Rap", coverArt = "5766",
                size = 5607024, contentType = "audio/mpeg", suffix = "mp3", duration = 233,
                bitRate = 192,
                path = "Compilations/Need for Speed Most Wanted/17 You'll Be Under My Wheels" +
                    ".mp3",
                isVideo = false, playCount = 0, discNumber = 1,
                created = parseDate("2016-10-23T20:09:02.000Z"), albumId = "568",
                artistId = "505", type = "music"
            )
        }
    }

    @Test
    fun `Should pass artist param`() = runTest {
        val artist = "some-artist"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search_ok.json",
            expectedParam = "artist=$artist"
        ) {
            client.api.searchSuspend(artist = artist)
        }
    }

    @Test
    fun `Should pass album param`() = runTest {
        val album = "some-album"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search_ok.json",
            expectedParam = "album=$album"
        ) {
            client.api.searchSuspend(album = album)
        }
    }

    @Test
    fun `Should pass title param`() = runTest {
        val title = "some-title"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search_ok.json",
            expectedParam = "title=$title"
        ) {
            client.api.searchSuspend(title = title)
        }
    }

    @Test
    fun `Should contain any param`() = runTest {
        val any = "AnyString"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search_ok.json",
            expectedParam = "any=$any"
        ) {
            client.api.searchSuspend(any = any)
        }
    }

    @Test
    fun `Should contain count param`() = runTest {
        val count = 11

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search_ok.json",
            expectedParam = "count=$count"
        ) {
            client.api.searchSuspend(count = count)
        }
    }

    @Test
    fun `Should contain offset param`() = runTest {
        val offset = 54

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search_ok.json",
            expectedParam = "offset=$offset"
        ) {
            client.api.searchSuspend(offset = offset)
        }
    }

    @Test
    fun `Should contain newerThan param`() = runTest {
        val newerThan = Calendar.getInstance()

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "search_ok.json",
            expectedParam = "newerThan=${newerThan.time.time}"
        ) {
            client.api.searchSuspend(newerThan = newerThan.time.time)
        }
    }
}
