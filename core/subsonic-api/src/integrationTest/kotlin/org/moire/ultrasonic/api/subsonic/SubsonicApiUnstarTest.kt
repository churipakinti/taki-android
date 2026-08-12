package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse

/**
 * Integration test for [SubsonicAPIClient] for unstar call.
 */
class SubsonicApiUnstarTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse ok response`() = runTest {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        val response = client.api.unstarSuspend()

        response.status `should be` SubsonicResponse.Status.OK
    }

    @Test
    fun `Should parse error response`() = runTest {
        checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.unstarSuspend()
        }
    }

    @Test
    fun `Should pass id param`() = runTest {
        val id = "545"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.unstarSuspend(id = id)
        }
    }

    @Test
    fun `Should pass artistId param`() = runTest {
        val artistId = "644"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "artistId=$artistId"
        ) {
            client.api.unstarSuspend(artistId = artistId)
        }
    }

    @Test
    fun `Should pass albumId param`() = runTest {
        val albumId = "3344"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "albumId=$albumId"
        ) {
            client.api.unstarSuspend(albumId = albumId)
        }
    }
}
