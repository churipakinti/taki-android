package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.MusicFolder

/**
 * Integration test for [SubsonicAPIClient] for getMusicFolders() request.
 */
class SubsonicApiGetMusicFoldersTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse get music folders ok response`() = runTest {
        mockWebServerRule.enqueueResponse("get_music_folders_ok.json")

        val response = client.api.getMusicFoldersSuspend()

        with(response) {
            assertBaseResponseOk()
            musicFolders `should be equal to` listOf(
                MusicFolder("0", "Music"),
                MusicFolder("2", "Test")
            )
        }
    }

    @Test
    fun `Should parse get music folders error response`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.getMusicFoldersSuspend()
        }

        response.musicFolders `should be equal to` emptyList()
    }
}
