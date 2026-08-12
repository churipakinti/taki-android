package org.moire.ultrasonic.api.subsonic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.Artist
import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.Indexes

/**
 * Integration test for [SubsonicAPIClient] for getIndexes() request.
 */
class SubsonicApiGetIndexesTest : SubsonicAPIClientTest() {
    @Test
    fun `Should parse get indexes ok response`() = runTest {
        mockWebServerRule.enqueueResponse("get_indexes_ok.json")

        val response = client.api.getIndexesSuspend(null, null)

        response.indexes `should not be` null
        with(response.indexes) {
            lastModified `should be equal to` 1491069027523
            ignoredArticles `should be equal to` "The El La Los Las Le Les"
            shortcutList `should be equal to` listOf(
                Artist(id = "889", name = "podcasts"),
                Artist(id = "890", name = "audiobooks")
            )
            indexList `should be equal to` mutableListOf(
                Index(
                    "A",
                    listOf(
                        Artist(
                            id = "50",
                            name = "Ace Of Base",
                            starred = parseDate("2017-04-02T20:16:29.815Z")
                        ),
                        Artist(id = "379", name = "A Perfect Circle")
                    )
                ),
                Index(
                    "H",
                    listOf(
                        Artist(id = "299", name = "Haddaway"),
                        Artist(id = "297", name = "Halestorm")
                    )
                )
            )
        }
    }

    @Test
    fun `Should add music folder id as a query param for getIndexes api call`() = runTest {
        val musicFolderId = "9"

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_indexes_ok.json",
            expectedParam = "musicFolderId=$musicFolderId"
        ) {
            client.api.getIndexesSuspend(musicFolderId, null)
        }
    }

    @Test
    fun `Should add ifModifiedSince as a query param for getIndexes api call`() = runTest {
        val ifModifiedSince = System.currentTimeMillis()

        mockWebServerRule.assertRequestParamSuspend(
            responseResourceName = "get_indexes_ok.json",
            expectedParam = "ifModifiedSince=$ifModifiedSince"
        ) {
            client.api.getIndexesSuspend(null, ifModifiedSince)
        }
    }

    @Test
    fun `Should parse get indexes error response`() = runTest {
        val response = checkErrorCallParsedSuspend(mockWebServerRule) {
            client.api.getIndexesSuspend(null, null)
        }

        response.indexes `should not be` null
        response.indexes `should be equal to` Indexes()
    }
}
