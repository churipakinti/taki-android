/*
 * CoverArtFetcher.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.imageloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.io.File
import java.io.IOException
import okio.buffer
import okio.source
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.FileUtil.SUFFIX_LARGE
import org.moire.ultrasonic.util.FileUtil.SUFFIX_SMALL
import org.moire.ultrasonic.util.Util
import timber.log.Timber

data class CoverArtRequest(val id: String, val cacheKey: String, val size: Int)

class CoverArtKeyer : Keyer<CoverArtRequest> {
    override fun key(data: CoverArtRequest, options: Options): String? = data.cacheKey
}

class CoverArtFetcher(private val coverArtRequest: CoverArtRequest, private val options: Options) :
    Fetcher,
    KoinComponent {
    private val client: SubsonicAPIClient by inject()

    override suspend fun fetch(): FetchResult {
        // Check the cache before sending out a network request
        val result = getCachedImage()
        if (result != null) return result

        // Try to fetch the image from the API
        // Inverted call order, because Mockito has problems with chained calls.
        val response = client.toStreamResponse(
            client.api.getCoverArt(
                coverArtRequest.id,
                coverArtRequest.size.toLong()
            ).execute()
        )

        // Handle the response
        if (!response.hasError() && response.stream != null) {
            return SourceFetchResult(
                ImageSource(response.stream!!.source().buffer(), options.fileSystem),
                null,
                DataSource.NETWORK
            )
        }

        // Throw an error if still not successful
        throw IOException("${response.apiError}")
    }

    private fun getCachedImage(): FetchResult? {
        val result = getCachedImage(coverArtRequest.cacheKey)
        if (result != null) return result
        // The image wasn't cached, check if a large version is cached that we can later downsize.
        val largeKey = coverArtRequest.cacheKey.replace(SUFFIX_SMALL, SUFFIX_LARGE)
        if (largeKey == coverArtRequest.cacheKey) {
            // We were already checking for the large image, nothing found in the cache.
            return null
        }
        // Check for the large size in the cache.
        return getCachedImage(largeKey)
    }

    private fun getCachedImage(cacheKey: String): FetchResult? {
        val bitmap = getAlbumArtBitmapFromDisk(cacheKey, coverArtRequest.size) ?: return null
        return ImageFetchResult(
            bitmap.asImage(),
            false,
            DataSource.DISK
        )
    }

    private fun getAlbumArtBitmapFromDisk(filename: String, size: Int?): Bitmap? {
        val albumArtFile = FileUtil.getAlbumArtFile(filename)
        val bitmap: Bitmap? = null
        if (File(albumArtFile).exists()) {
            return getBitmapFromDisk(albumArtFile, size, bitmap)
        }
        return null
    }

    private fun getBitmapFromDisk(path: String, size: Int?, bitmap: Bitmap?): Bitmap? {
        var bitmap1 = bitmap
        val opt = BitmapFactory.Options()
        if (size != null && size > 0) {
            // With this flag we only calculate the size first
            opt.inJustDecodeBounds = true

            // Decode the size
            BitmapFactory.decodeFile(path, opt)

            // Now set the remaining flags
            opt.inSampleSize = Util.calculateInSampleSize(
                opt,
                size,
                Util.getScaledHeight(opt.outHeight.toDouble(), opt.outWidth.toDouble(), size)
            )

            // Enable real decoding
            opt.inJustDecodeBounds = false
        }
        try {
            bitmap1 = BitmapFactory.decodeFile(path, opt)
        } catch (expected: Exception) {
            Timber.e(expected, "Exception in BitmapFactory.decodeFile()")
        }
        return bitmap1
    }

    class Factory : Fetcher.Factory<CoverArtRequest> {
        override fun create(
            data: CoverArtRequest,
            options: Options,
            imageLoader: coil3.ImageLoader
        ): Fetcher = CoverArtFetcher(data, options)
    }
}
