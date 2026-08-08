package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.junit.Test

class SubsonicApiGetTopSongsTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse top songs`() {
        mockWebServerRule.enqueueResponse("get_top_songs_ok.json")

        val response = client.api.getTopSongs("AC/DC", count = 10).execute()

        assertResponseSuccessful(response)
        with(response.body()!!.songsList.single()) {
            id `should be equal to` "song-1"
            title `should be equal to` "Back in Black"
            artist `should be equal to` "AC/DC"
            album `should be equal to` "Back in Black"
        }
    }

    @Test
    fun `Should pass top songs parameters`() {
        mockWebServerRule.assertRequestParam(expectedParam = "artist=AC%2FDC") {
            client.api.getTopSongs("AC/DC", count = 10).execute()
        }
    }
}
