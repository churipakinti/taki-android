package org.moire.ultrasonic.api.subsonic

import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.shouldNotContain
import org.junit.Test

/**
 * The debug-build HTTP logger prints the request line, which carries the auth query params
 * (legacy `p`, or `t` + `s`). They must be redacted before reaching the Timber/file logger.
 */
class HttpLoggingCredentialRedactionTest {
    private val secret = "SUPER_SECRET_SENTINEL_9f21"

    private fun loggedLines(forcePlainTextPassword: Boolean): List<String> {
        val lines = mutableListOf<String>()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            val config = SubsonicClientConfiguration(
                server.url("/").toString(), "alice", secret,
                SubsonicAPIVersions.V1_16_0, "test",
                forcePlainTextPassword = forcePlainTextPassword, debug = true
            )
            val client = SubsonicAPIClient(config, { lines += it })
            runCatching { client.api.ping().execute() }
        }
        return lines
    }

    @Test
    fun `legacy password query param is redacted`() {
        val lines = loggedLines(forcePlainTextPassword = true)
        check(lines.any { it.contains("ping") }) { "request was not logged: $lines" }
        lines.forEach { it shouldNotContain secret; it shouldNotContain "enc:" }
    }

    @Test
    fun `token and salt query params are redacted`() {
        val lines = loggedLines(forcePlainTextPassword = false)
        val request = lines.first { it.contains("ping") }
        for (param in listOf("t", "s", "p")) {
            val value = Regex("[?&]$param=([^& ]*)").find(request)?.groupValues?.get(1)
            check(value == null || value == REDACTED) { "$param not redacted: $request" }
        }
    }

    private companion object {
        const val REDACTED = "%E2%96%88%E2%96%88"
    }
}
