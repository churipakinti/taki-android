/*
 * AvatarFetcherTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.imageloader

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.mockito.Answers
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.moire.ultrasonic.util.FileUtil
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
class AvatarFetcherTest : KoinTest {
    private val mockApiClient: SubsonicAPIClient = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imageLoader = coil3.ImageLoader.Builder(context).build()

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { mockApiClient }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `Should throw IOException when request to api failed`() = runTest {
        // Create the fetcher under test.
        val fetcher = AvatarFetcher.Factory().create(
            AvatarRequest("user"),
            Options(context),
            imageLoader
        )

        // Create and return a fake stream response with an error code.
        val streamResponse = StreamResponse(null, null, 500)
        FileUtil.cachedUltrasonicDirectory = File(
            Environment.getExternalStorageDirectory(),
            "Android/data/org.moire.ultrasonic"
        )
        whenever(
            mockApiClient.toStreamResponse(any())
        ).thenReturn(streamResponse)

        // Verify we throw an IOException when getting a bad stream response.
        var thrown = false
        try {
            fetcher.fetch()
        } catch (ioException: IOException) {
            thrown = true
            Timber.d("Caught expected exception: %s", ioException)
        }
        thrown shouldBeEqualTo true
    }

    @Test
    fun `Should load bitmap from network`() = runTest {
        // Create the fetcher under test.
        val user = "user"
        val fetcher = AvatarFetcher.Factory().create(
            AvatarRequest(user),
            Options(context),
            imageLoader
        )

        // Prepare a fake stream response with a test image.
        val testImage = "Big_Buck_Bunny.jpeg"
        val streamResponse = StreamResponse(
            loadResourceStream(testImage),
            apiError = null,
            responseHttpCode = 200
        )
        FileUtil.cachedUltrasonicDirectory = File(
            Environment.getExternalStorageDirectory(),
            "Android/data/org.moire.ultrasonic"
        )

        // If the correct subsonic API is called return the above fake stream response.
        val mockResponse: Response<ResponseBody> = mock()
        `when`(mockApiClient.api.getAvatar(user).execute()).thenReturn(mockResponse)
        `when`(
            mockApiClient.toStreamResponse(mockResponse)
        ).thenReturn(streamResponse)

        // Run the fetch and validate the result.
        val result = fetcher.fetch() as SourceFetchResult
        result.dataSource shouldBeEqualTo DataSource.NETWORK
        result.source shouldNotBe null
        val expectedFile = File(javaClass.classLoader!!.getResource(testImage).file)
        val actualFile = result.source.file().toFile()
        actualFile.readLines() shouldBeEqualTo expectedFile.readLines()
    }
}
