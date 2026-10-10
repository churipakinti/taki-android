package org.moire.ultrasonic.api.subsonic

/**
 * Provides configuration for [SubsonicAPIClient].
 */
data class SubsonicClientConfiguration(
    val baseUrl: String,
    val username: String,
    val password: String,
    val minimalProtocolVersion: SubsonicAPIVersions,
    val clientID: String,
    val allowSelfSignedCertificate: Boolean = false,
    val forcePlainTextPassword: Boolean = false,
    val debug: Boolean = false,
    val isRealProtocolVersion: Boolean = false
) {
    /** Credential-safe: the generated `toString()` would print the plaintext [password]. */
    override fun toString(): String =
        "SubsonicClientConfiguration(baseUrl=$baseUrl, clientID=$clientID, password=<redacted>)"
}
