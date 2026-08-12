package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.api.subsonic.models.AlbumListType.BY_GENRE

/**
 * Integration tests for [SubsonicAPIDefinition] for getAlbumList call.
 */
class SubsonicApiGetAlbumListRequestTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.getAlbumListSuspend(BY_GENRE)
        }

        response.albumList `should be equal to` emptyList()
    }

    @Test
    fun `Should handle ok response`() = runTest {
        mockWebServerRule.enqueueResponse("get_album_list_ok.json")

        val response = client.api.getAlbumListSuspend(BY_GENRE)

        with(response.albumList) {
            size `should be equal to` 2
            this[1] `should be equal to` Album(
                id = "9997", parent = "9996",
                title = "Endless Forms Most Beautiful", album = "Endless Forms Most Beautiful",
                artist = "Nightwish", year = 2015, genre = "Symphonic Metal",
                coverArt = "9997", playCount = 11,
                created = parseDate("2017-09-02T16:22:49.000Z")
            )
        }
    }

    @Test
    fun `Should pass type in request params`() = runTest {
        val listType = AlbumListType.HIGHEST

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_album_list_ok.json",
            expectedParam = "type=${listType.typeName}"
        ) {
            client.api.getAlbumListSuspend(type = listType)
        }
    }

    @Test
    fun `Should pass size in request params`() = runTest {
        val size = 45

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_album_list_ok.json",
            expectedParam = "size=$size"
        ) {
            client.api.getAlbumListSuspend(type = BY_GENRE, size = size)
        }
    }

    @Test
    fun `Should pass offset in request params`() = runTest {
        val offset = 3

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_album_list_ok.json",
            expectedParam = "offset=$offset"
        ) {
            client.api.getAlbumListSuspend(type = BY_GENRE, offset = offset)
        }
    }

    @Test
    fun `Should pass from year in request params`() = runTest {
        val fromYear = 2001

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_album_list_ok.json",
            expectedParam = "fromYear=$fromYear"
        ) {
            client.api.getAlbumListSuspend(type = BY_GENRE, fromYear = fromYear)
        }
    }

    @Test
    fun `Should pass to year in request params`() = runTest {
        val toYear = 2017

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_album_list_ok.json",
            expectedParam = "toYear=$toYear"
        ) {
            client.api.getAlbumListSuspend(type = BY_GENRE, toYear = toYear)
        }
    }

    @Test
    fun `Should pass genre in request params`() = runTest {
        val genre = "Rock"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_album_list_ok.json",
            expectedParam = "genre=$genre"
        ) {
            client.api.getAlbumListSuspend(type = BY_GENRE, genre = genre)
        }
    }

    @Test
    fun `Should pass music folder id in request params`() = runTest {
        val folderId = "545"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_album_list_ok.json",
            expectedParam = "musicFolderId=$folderId"
        ) {
            client.api.getAlbumListSuspend(type = BY_GENRE, musicFolderId = folderId)
        }
    }
}
