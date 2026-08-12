package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] for getAlbumInfo2 call.
 *
 * No test previously existed for this endpoint (Fase 7 of TAKI_CODE_OPTIMIZATION_PLAN.md).
 */
class SubsonicApiGetAlbumInfo2Test : SubsonicAPIClientTest() {
    @Test
    fun `Should parse album notes and artwork urls`() = runTest {
        mockWebServerRule.enqueueResponse("get_album_info2_ok.json")

        val response = client.api.getAlbumInfo2Suspend("album-1")

        with(response.albumInfo) {
            notes `should be equal to` "A landmark hard rock album."
            musicBrainzId `should be equal to` "1a2b3c4d-5e6f-7890-abcd-ef1234567890"
            lastFmUrl `should be equal to` "https://www.last.fm/music/AC%2FDC/Black+Ice"
            smallImageUrl `should be equal to` "https://example.test/blackice-small.jpg"
            mediumImageUrl `should be equal to` "https://example.test/blackice-medium.jpg"
            largeImageUrl `should be equal to` "https://example.test/blackice-large.jpg"
        }
    }

    @Test
    fun `Should pass id param in request`() = runTest {
        val id = "album-42"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_album_info2_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.getAlbumInfo2Suspend(id)
        }
    }

    @Test
    fun `Should parse error response`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.getAlbumInfo2Suspend("album-1")
        }

        response.albumInfo.notes `should be equal to` ""
    }
}
