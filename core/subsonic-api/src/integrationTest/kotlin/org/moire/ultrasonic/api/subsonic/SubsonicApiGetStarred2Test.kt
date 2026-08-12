package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.SearchTwoResult

/**
 * Integration test for [SubsonicAPIClient] for getStarred2 call.
 */
@Suppress("NamingConventionViolation")
class SubsonicApiGetStarred2Test : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.getStarred2Suspend()
        }

        response.starred2 `should be equal to` SearchTwoResult()
    }

    @Test
    fun `Should handle ok reponse`() = runTest {
        mockWebServerRule.enqueueResponse("get_starred_2_ok.json")

        val response = client.api.getStarred2Suspend()

        with(response.starred2) {
            albumList `should be equal to` emptyList()
            artistList.size `should be equal to` 1
            artistList[0] `should be equal to` Artist(
                id = "364",
                name = "Parov Stelar",
                starred = parseDate("2017-08-12T18:32:58.768Z")
            )
            songList `should be equal to` emptyList()
        }
    }

    @Test
    fun `Should pass music folder id in request param`() = runTest {
        val musicFolderId = "441"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_starred_2_ok.json",
            expectedParam = "musicFolderId=$musicFolderId"
        ) {
            client.api.getStarred2Suspend(musicFolderId = musicFolderId)
        }
    }
}
