package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.junit.Test

class SubsonicApiGetTopSongsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse top songs`() = runTest {
        mockWebServerRule.enqueueResponse("get_top_songs_ok.json")

        val response = client.api.getTopSongsSuspend("AC/DC", count = 10)

        with(response.songsList.single()) {
            id `should be equal to` "song-1"
            title `should be equal to` "Back in Black"
            artist `should be equal to` "AC/DC"
            album `should be equal to` "Back in Black"
        }
    }

    @Test
    fun `Should pass top songs parameters`() = runTest {
        mockWebServerRule.assertRequestParamSuspend(expectedParam = "artist=AC%2FDC") {
            client.api.getTopSongsSuspend("AC/DC", count = 10)
        }
    }
}
