/*
 * EditServerModel.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.SubsonicAPIDefinition
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicClientConfiguration
import org.moire.ultrasonic.api.subsonic.SubsonicRESTException
import org.moire.ultrasonic.api.subsonic.allowSelfSignedCertificates
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.util.Constants
import retrofit2.HttpException
import timber.log.Timber

@Suppress("MagicNumber")
class EditServerModel(val app: Application) :
    AndroidViewModel(app),
    KoinComponent {

    val activeServerProvider: ActiveServerProvider by inject()

    private suspend fun serverFunctionAvailable(
        type: ServerFeature,
        function: suspend () -> SubsonicResponse
    ): FeatureSupport {
        val result = try {
            function().falseOnFailure()
        } catch (_: IOException) {
            false
        } catch (_: SubsonicRESTException) {
            false
        } catch (_: HttpException) {
            false
        }
        return FeatureSupport(type, result)
    }

    /**
     * This extension checks API call results for errors, API version, etc
     * @return Boolean: True if everything was ok, false if an error was found
     */
    private fun SubsonicResponse.falseOnFailure(): Boolean =
        (this.status === SubsonicResponse.Status.OK)

    private fun requestFlow(type: ServerFeature, api: SubsonicAPIDefinition, userName: String) =
        flow {
            when (type) {
                ServerFeature.CHAT -> emit(
                    serverFunctionAvailable(type, api::getChatMessagesSuspend)
                )

                ServerFeature.BOOKMARK -> emit(
                    serverFunctionAvailable(type, api::getBookmarksSuspend)
                )

                ServerFeature.SHARE -> emit(
                    serverFunctionAvailable(type, api::getSharesSuspend)
                )

                ServerFeature.PODCAST -> emit(
                    serverFunctionAvailable(type, api::getPodcastsSuspend)
                )

                ServerFeature.JUKEBOX -> emit(
                    serverFunctionAvailable(type) {
                        val response = api.getUserSuspend(userName)
                        if (!response.user.jukeboxRole) throw IOException()
                        response
                    }
                )

                ServerFeature.VIDEO -> emit(
                    serverFunctionAvailable(type, api::getVideosSuspend)
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun queryFeatureSupport(currentServerSetting: ServerSetting): Flow<FeatureSupport> {
        val client = buildTestClient(currentServerSetting)
        // One line of magic:
        // Get all possible feature values, turn them into a flow,
        // and execute each request concurrently
        return (ServerFeature.values()).asFlow().flatMapMerge {
            requestFlow(it, client.api, currentServerSetting.userName)
        }
    }

    private suspend fun buildTestClient(serverSetting: ServerSetting): SubsonicAPIClient {
        val configuration = SubsonicClientConfiguration(
            serverSetting.url,
            serverSetting.userName,
            serverSetting.password,
            SubsonicAPIVersions.getClosestKnownClientApiVersion(
                Constants.REST_PROTOCOL_VERSION
            ),
            Constants.REST_CLIENT_ID,
            serverSetting.allowSelfSignedCertificate,
            serverSetting.forcePlainTextPassword,
            BuildConfig.DEBUG
        )

        val builder = OkHttpClient.Builder()
        if (serverSetting.allowSelfSignedCertificate) {
            builder.allowSelfSignedCertificates()
        }
        val client = SubsonicAPIClient(configuration, baseOkClient = builder.build())

        // Execute a ping to retrieve the API version.
        // This is accepted to fail if the authentication is incorrect yet.
        val pingResponse = client.api.pingSuspend()
        val restApiVersion = pingResponse.version.restApiVersion
        serverSetting.minimumApiVersion = restApiVersion
        Timber.i("Server minimum API version set to %s", restApiVersion)

        // Execute a ping to check the authentication, now using the correct API version.
        client.api.pingSuspend()
        return client
    }

    fun storeFeatureSupport(settings: ServerSetting, it: FeatureSupport) {
        when (it.type) {
            ServerFeature.CHAT -> settings.chatSupport = it.supported
            ServerFeature.BOOKMARK -> settings.bookmarkSupport = it.supported
            ServerFeature.SHARE -> settings.shareSupport = it.supported
            ServerFeature.PODCAST -> settings.podcastSupport = it.supported
            ServerFeature.JUKEBOX -> settings.jukeboxSupport = it.supported
            ServerFeature.VIDEO -> settings.videoSupport = it.supported
        }
    }

    companion object {
        enum class ServerFeature(val named: String) {
            CHAT("chat"),
            BOOKMARK("bookmark"),
            SHARE("share"),
            PODCAST("podcast"),
            JUKEBOX("jukebox"),
            VIDEO("video")
        }

        data class FeatureSupport(val type: ServerFeature, val supported: Boolean)
    }
}
