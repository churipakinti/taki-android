package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Playlist

/**
 * Integration test for [SubsonicAPIClient] for getPlaylists call.
 */
class SubsonicApiGetPlaylistsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse error call`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.getPlaylistsSuspend()
        }

        response.playlists `should not be` null
        response.playlists `should be equal to` emptyList()
    }

    @Test
    fun `Should parse ok response`() = runTest {
        mockWebServerRule.enqueueResponse("get_playlists_ok.json")

        val response = client.api.getPlaylistsSuspend()

        with(response.playlists) {
            size `should be equal to` 1
            this[0] `should be equal to` Playlist(
                id = "0", name = "Aug 27, 2017 11:17 AM",
                owner = "admin", public = false, songCount = 16, duration = 3573,
                comment = "Some comment",
                created = parseDate("2017-08-27T11:17:26.216Z"),
                changed = parseDate("2017-08-27T11:17:26.218Z"),
                coverArt = "pl-0"
            )
        }
    }

    @Test
    fun `Should pass username as a parameter`() = runTest {
        val username = "SomeUsername"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_playlists_ok.json",
            expectedParam = "username=$username"
        ) {
            client.api.getPlaylistsSuspend(username = username)
        }
    }
}
