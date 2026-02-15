/*
 * ImageLoaderTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.imageloader

import android.os.Looper.getMainLooper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ImageLoaderTest {
    @Test
    fun `Image request failure`() = runTest {
        // Setup the image loader
        val mockApiClient: SubsonicAPIClient = mock()
        val config = ImageLoaderConfig(0, 0, null)
        val imageLoader =
            ImageLoader(ApplicationProvider.getApplicationContext(), mockApiClient, config)

        // Generate a filename for our cover art. We put it in a temp folder so it's cleaned up
        // after the test.
        val temporaryFolder = TemporaryFolder()
        temporaryFolder.create()
        val coverArtFilename = File(temporaryFolder.root.path, "coverArt.jpg").path

        // Launch the download in the background
        val job = imageLoader.downloadCoverArt("id", coverArtFilename)

        // Force the test to execute downloadCoverArt()
        val looper = shadowOf(getMainLooper())
        while (job.isActive) {
            looper.idle()
        }

        // Wait for the downloadCoverArt() to finish.
        job.join()

        // We didn't connect imageLoader to a subsonic server so the request should have failed.
        // When the request fails we should not have created a cover art file.
        File(coverArtFilename).exists() shouldBeEqualTo false
    }
}
