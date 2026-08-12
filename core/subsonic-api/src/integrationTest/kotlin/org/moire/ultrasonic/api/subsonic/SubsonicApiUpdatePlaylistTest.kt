package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Integration test for [SubsonicAPIClient] for updatePlaylist call.
 */
class SubsonicApiUpdatePlaylistTest : SubsonicAPIClientTest() {
    @Test
    fun `Should handle error response`() = runTest {
        checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.updatePlaylistSuspend("10")
        }
    }

    @Test
    fun `Should handle ok response`() = runTest {
        mockWebServerRule.enqueueResponse("ping_ok.json")

        client.api.updatePlaylistSuspend("15")
    }

    @Test
    fun `Should pass playlist id param in request`() = runTest {
        val id = "5453"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "playlistId=$id"
        ) {
            client.api.updatePlaylistSuspend(id = id)
        }
    }

    @Test
    fun `Should pass name param in request`() = runTest {
        val name = "some-name"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "name=$name"
        ) {
            client.api.updatePlaylistSuspend("22", name = name)
        }
    }

    @Test
    fun `Should pass comment param in request`() = runTest {
        val comment = "some-unusual-comment"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "comment=$comment"
        ) {
            client.api.updatePlaylistSuspend("42", comment = comment)
        }
    }

    @Test
    fun `Should pass public param in request`() = runTest {
        val public = true

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "public=$public"
        ) {
            client.api.updatePlaylistSuspend("53", public = public)
        }
    }

    @Test
    fun `Should pass song ids to update in request`() = runTest {
        val songIds = listOf("45", "81")

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "songIdToAdd=${songIds[0]}&songIdToAdd=${songIds[1]}"
        ) {
            client.api.updatePlaylistSuspend("25", songIdsToAdd = songIds)
        }
    }

    @Test
    fun `Should pass song indexes to remove in request`() = runTest {
        val songIndexesToRemove = listOf(129, 1)

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "ping_ok.json",
            expectedParam = "songIndexToRemove=${songIndexesToRemove[0]}&" +
                "songIndexToRemove=${songIndexesToRemove[1]}"
        ) {
            client.api.updatePlaylistSuspend(
                "49",
                songIndexesToRemove = songIndexesToRemove
            )
        }
    }
}
