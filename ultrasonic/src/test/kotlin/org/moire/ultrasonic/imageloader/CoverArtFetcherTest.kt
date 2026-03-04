/*
 * CoverArtFetcherTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.imageloader

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import coil3.decode.DataSource
import coil3.fetch.ImageFetchResult
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
class CoverArtFetcherTest : KoinTest {
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
        val fetcher = CoverArtFetcher.Factory().create(
            CoverArtRequest("some", "-1", 0),
            Options(context),
            imageLoader
        )

        // Create and return a fake stream response with an error code.
        val streamResponse = StreamResponse(null, null, 500)
        FileUtil.cachedUltrasonicDirectory = File(
            Environment.getExternalStorageDirectory(), "Android/data/org.moire.ultrasonic"
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
        val id = "id"
        val size = 0
        val fetcher = CoverArtFetcher.Factory().create(
            CoverArtRequest(id, "someCacheKey.jpg", size),
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
            Environment.getExternalStorageDirectory(), "Android/data/org.moire.ultrasonic"
        )

        // If the correct subsonic API is called return the above fake stream response.
        val mockResponse: Response<ResponseBody> = mock()
        `when`(mockApiClient.api.getCoverArt(id, size.toLong()).execute()).thenReturn(mockResponse)
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

    @Test
    fun `Fetch pinned image from disk`() = runTest {
        // Create the fetcher under test.
        val id = "id"
        val size = 0
        val cacheKey = "Big_Buck_Bunny.jpeg"
        val fetcher = CoverArtFetcher.Factory().create(
            CoverArtRequest(id, cacheKey, size),
            Options(context),
            imageLoader
        )

        // Copy the test image into the pinned album art directory so the fetcher can find it.
        FileUtil.cachedUltrasonicDirectory = File(
            Environment.getExternalStorageDirectory(), "Android/data/org.moire.ultrasonic"
        )
        val sourceFile = File(javaClass.classLoader!!.getResource(cacheKey).file)
        val cachedAlbumArtFile = File(FileUtil.albumArtDirectory, cacheKey)
        sourceFile.copyTo(cachedAlbumArtFile)

        // Fetch and validate the result.
        val result = fetcher.fetch() as ImageFetchResult
        result.dataSource shouldBeEqualTo DataSource.DISK
        result.image shouldNotBe null
    }

    @Test
    fun `Fetch pinned image from disk falling back to larger image`() = runTest {
        // Create the fetcher under test. The fetch request is for a small image.
        val id = "id"
        val size = 0
        val cacheKeySmall = "Big_Buck_Bunny.jpeg-small"
        val cacheKeyLarge = "Big_Buck_Bunny.jpeg"
        val fetcher = CoverArtFetcher.Factory().create(
            CoverArtRequest(id, cacheKeySmall, size),
            Options(context),
            imageLoader
        )

        // Copy the test image into the pinned album art directory so the fetcher can find it. We
        // only make a large version of the image so the initial cache check for the small image
        // will fail but we should fall back to the larger image.
        FileUtil.cachedUltrasonicDirectory = File(
            Environment.getExternalStorageDirectory(), "Android/data/org.moire.ultrasonic"
        )
        val sourceFile = File(javaClass.classLoader!!.getResource(cacheKeyLarge).file)
        val cachedAlbumArtFile = File(FileUtil.albumArtDirectory, cacheKeyLarge)
        sourceFile.copyTo(cachedAlbumArtFile)

        // Fetch and validate the result.
        val result = fetcher.fetch() as ImageFetchResult
        result.dataSource shouldBeEqualTo DataSource.DISK
        result.image shouldNotBe null
    }
}
