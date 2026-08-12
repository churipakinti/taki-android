package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Instrumentation test for [SubsonicAPIClient] for deletePlaylist call.
 */
class SubsonicApiDeletePlaylistTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() = runTest {
        checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.deletePlaylistSuspend("10")
        }
    }

    @Test
    fun `Should handle ok response`() = runTest {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        client.api.deletePlaylistSuspend("10")
    }

    @Test
    fun `Should pass id param in request`() = runTest {
        val id = "534"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "id=$id"
        ) {
            client.api.deletePlaylistSuspend(id)
        }
    }
}
