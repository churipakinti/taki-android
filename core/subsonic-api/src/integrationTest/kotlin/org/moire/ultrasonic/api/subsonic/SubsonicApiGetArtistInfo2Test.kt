package org.moire.ultrasonic.api.subsonic

import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist

class SubsonicApiGetArtistInfo2Test : SubsonicAPIClientTest() {
    @Test
    fun `Should parse artist biography and similar artists`() {
        mockWebServerRule.enqueueResponse("get_artist_info2_ok.json")

        val response = client.api.getArtistInfo2("artist-1").execute()

        assertResponseSuccessful(response)
        with(response.body()!!.artistInfo) {
            biography `should be equal to` "An influential hard rock band."
            musicBrainzId `should be equal to` "66c662b6-6e2f-4930-8610-912e24c63ed1"
            lastFmUrl `should be equal to` "https://www.last.fm/music/AC%2FDC"
            similarArtists `should be equal to` listOf(
                Artist(
                    id = "artist-2",
                    name = "Airbourne",
                    coverArt = "ar-artist-2",
                    albumCount = 5
                )
            )
        }
    }

    @Test
    fun `Should pass artist info parameters`() {
        mockWebServerRule.assertRequestParam(expectedParam = "id=artist-1") {
            client.api.getArtistInfo2("artist-1", count = 8, includeNotPresent = false).execute()
        }
    }
}
