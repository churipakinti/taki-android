@file:JvmName("MusicServiceModule")

package org.moire.ultrasonic.di

import kotlin.math.abs
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicClientConfiguration
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.log.TimberOkHttpLogger
import org.moire.ultrasonic.service.CachedMusicService
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.OfflineMusicService
import org.moire.ultrasonic.service.RESTMusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.PerfMetricsEventListener
import org.moire.ultrasonic.util.PerfMetricsInterceptor

/**
 * This Koin module contains the registration of classes related to the Music Services
 */
internal const val ONLINE_MUSIC_SERVICE = "OnlineMusicService"
internal const val OFFLINE_MUSIC_SERVICE = "OfflineMusicService"

val musicServiceModule = module {

    single(named("ServerInstance")) {
        return@single ActiveServerProvider.getActiveServerId()
    }

    single(named("ServerID")) {
        val serverInstance = get<Int>(named("ServerInstance"))
        val serverUrl = get<ActiveServerProvider>().getActiveServer().url
        return@single abs("$serverUrl$serverInstance".hashCode()).toString()
    }

    single {
        val server = get<ActiveServerProvider>().getActiveServer()

        return@single SubsonicClientConfiguration(
            baseUrl = server.url,
            username = server.userName,
            password = server.password,
            minimalProtocolVersion = SubsonicAPIVersions.getClosestKnownClientApiVersion(
                server.minimumApiVersion
                    ?: Constants.REST_PROTOCOL_VERSION
            ),
            clientID = Constants.REST_CLIENT_ID,
            allowSelfSignedCertificate = server.allowSelfSignedCertificate,
            forcePlainTextPassword = server.forcePlainTextPassword,
            debug = BuildConfig.DEBUG,
            isRealProtocolVersion = server.minimumApiVersion != null
        )
    }

    single<HttpLoggingInterceptor.Logger> { TimberOkHttpLogger() }

    // Baseline network instrumentation (Fase 0 of the optimization plan): only ever wired in
    // debug builds, never in release. See PerfMetricsInterceptor. Named to avoid colliding with
    // the unqualified OkHttpClient single already provided by baseNetworkModule.
    single(named("PerfMetricsOkHttpClient")) {
        if (BuildConfig.DEBUG) {
            OkHttpClient.Builder()
                .addInterceptor(PerfMetricsInterceptor())
                .eventListener(PerfMetricsEventListener())
                .build()
        } else {
            OkHttpClient.Builder().build()
        }
    }
    single { SubsonicAPIClient(get(), get(), get(named("PerfMetricsOkHttpClient"))) }

    // Cover art and avatar fetches (ImageLoader/CoverArtFetcher/AvatarFetcher) get their own
    // connection pool, isolated from the client above. They previously shared the streaming/API
    // client's pool, which defaults to 5 concurrent connections per host -- on Home's initial
    // load, ~5 album-list calls plus up to a dozen cover art fetches all competed for those same
    // 5 slots, and would keep competing with an in-progress audio stream during normal use.
    // Isolating them means a burst of cover art loading can no longer delay a stream/API request
    // (or vice versa) by starving its connection slot. No concurrency tuning yet -- this commit
    // is isolation only, see Fase 1 of TAKI_CODE_OPTIMIZATION_PLAN.md.
    single(named("ImageOkHttpClient")) {
        OkHttpClient.Builder()
            .dispatcher(Dispatcher())
            .connectionPool(ConnectionPool())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(PerfMetricsInterceptor())
                    eventListener(PerfMetricsEventListener())
                }
            }
            .build()
    }
    single(named("ImageSubsonicAPIClient")) {
        SubsonicAPIClient(get(), get(), get(named("ImageOkHttpClient")))
    }

    single<MusicService>(named(ONLINE_MUSIC_SERVICE)) {
        CachedMusicService(RESTMusicService(get(), get()))
    }

    single<MusicService>(named(OFFLINE_MUSIC_SERVICE)) {
        OfflineMusicService()
    }
}
