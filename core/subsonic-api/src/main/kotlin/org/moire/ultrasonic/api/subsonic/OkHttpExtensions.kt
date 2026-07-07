package org.moire.ultrasonic.api.subsonic

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Configure OkHttpClient to allow self-signed certificates.
 * This should only be used if the user has explicitly allowed it for a server.
 */
@Suppress("CustomX509TrustManager", "TrustAllX509TrustManager")
fun OkHttpClient.Builder.allowSelfSignedCertificates(): OkHttpClient.Builder {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
        override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, arrayOf(trustManager), SecureRandom())

    sslSocketFactory(sslContext.socketFactory, trustManager)

    hostnameVerifier { _, _ -> true }

    return this
}
