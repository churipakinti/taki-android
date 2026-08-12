package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] for createPlaylist call.
 */
class SubsonicApiCreatePlaylistTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() = runTest {
        checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.createPlaylistSuspend()
        }
    }

    @Test
    fun `Should hanlde ok response`() = runTest {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        client.api.createPlaylistSuspend()
    }

    @Test
    fun `Should pass id param in request`() = runTest {
        val id = "56"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "playlistId=$id"
        ) {
            client.api.createPlaylistSuspend(id = id)
        }
    }

    @Test
    fun `Should pass name param in request`() = runTest {
        val name = "some-name"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "name=$name"
        ) {
            client.api.createPlaylistSuspend(name = name)
        }
    }

    @Test
    fun `Should pass song id param in request`() = runTest {
        val songId = listOf("4410", "852")

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "songId=${songId[0]}&songId=${songId[1]}"
        ) {
            client.api.createPlaylistSuspend(songIds = songId)
        }
    }
}
