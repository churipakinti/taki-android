package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse

/**
 * Integration test for [SubsonicAPIClient] for star request.
 */
class SubsonicApiStarTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse star ok response`() = runTest {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.starSuspend()

        response.status `should be` SubsonicResponse.Status.OK
    }

    @Test
    fun `Should parse star error response`() = runTest {
        checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.starSuspend()
        }
    }

    @Test
    fun `Should pass id param`() = runTest {
        val id = "110"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.starSuspend(id = id)
        }
    }

    @Test
    fun `Should pass artist id param`() = runTest {
        val artistId = "123"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "artistId=$artistId"
        ) {
            client.api.starSuspend(artistId = artistId)
        }
    }

    @Test
    fun `Should pass album id param`() = runTest {
        val albumId = "1001"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "albumId=$albumId"
        ) {
            client.api.starSuspend(albumId = albumId)
        }
    }
}
