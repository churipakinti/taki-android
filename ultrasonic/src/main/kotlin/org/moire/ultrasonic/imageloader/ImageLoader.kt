/*
 * ImageLoader.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.imageloader

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.squareup.picasso.LruCache
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.throwOnFailure
import org.moire.ultrasonic.api.subsonic.toStreamResponse
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util.safeClose
import timber.log.Timber

/**
 * Our new image loader which uses Picasso as a backend.
 */
class ImageLoader(
    context: Context,
    apiClient: SubsonicAPIClient,
    private val config: ImageLoaderConfig
) : CoroutineScope by CoroutineScope(Dispatchers.Main + SupervisorJob()) {
    private val cacheInProgress: ConcurrentHashMap<String, CountDownLatch> = ConcurrentHashMap()

    // Shortcut
    @Suppress("VariableNaming", "PropertyName")
    val API = apiClient.api

    private val picasso = Picasso.Builder(context)
        .addRequestHandler(CoverArtRequestHandler(apiClient))
        .addRequestHandler(AvatarRequestHandler(apiClient))
        .memoryCache(LruCache(calculateMemoryCacheSize(context)))
        .build().apply {
            // setIndicatorsEnabled(BuildConfig.DEBUG)
        }

    private fun load(request: ImageRequest) = when (request) {
        is ImageRequest.CoverArt -> loadCoverArt(request)
        is ImageRequest.Avatar -> loadAvatar(request)
    }

    private fun loadCoverArt(request: ImageRequest.CoverArt) {
        picasso.load(createLoadCoverArtRequest(request.entityId, request.size.toLong()))
            .addPlaceholder(request)
            .addError(request)
            .stableKey(request.cacheKey)
            .into(request.imageView)
    }

    private fun getCoverArt(request: ImageRequest.CoverArt): Bitmap {
        return picasso.load(createLoadCoverArtRequest(request.entityId, request.size.toLong()))
            .addPlaceholder(request)
            .addError(request)
            .stableKey(request.cacheKey)
            .get()
    }

    private fun loadAvatar(request: ImageRequest.Avatar) {
        picasso.load(createLoadAvatarRequest(request.username))
            .addPlaceholder(request)
            .addError(request)
            .stableKey(request.username)
            .into(request.imageView)
    }

    private fun RequestCreator.addPlaceholder(request: ImageRequest): RequestCreator {
        if (request.placeHolderDrawableRes != null) {
            placeholder(request.placeHolderDrawableRes)
        }

        return this
    }

    private fun RequestCreator.addError(request: ImageRequest): RequestCreator {
        if (request.errorDrawableRes != null) {
            error(request.errorDrawableRes)
        }

        return this
    }

    /**
     * Load the cover of a given entry into a Bitmap
     */
    fun getImage(
        id: String?,
        cacheKey: String?,
        large: Boolean,
        size: Int,
        defaultResourceId: Int = R.drawable.unknown_album
    ): Bitmap {
        val requestedSize = resolveSize(size, large)

        val request = ImageRequest.CoverArt(
            id!!,
            cacheKey!!,
            null,
            requestedSize,
            placeHolderDrawableRes = defaultResourceId,
            errorDrawableRes = defaultResourceId
        )
        return getCoverArt(request)
    }

    /**
     * Load the cover of a given entry into an ImageView
     */
    @JvmOverloads
    fun loadImage(
        view: View?,
        entry: MusicDirectory.Child?,
        large: Boolean,
        size: Int,
        defaultResourceId: Int = R.drawable.unknown_album
    ) = launch {
        val id = entry?.coverArt
        val key: String?

        withContext(Dispatchers.IO) {
            key = FileUtil.getAlbumArtKey(entry, large)
        }

        loadImage(view, id, key, large, size, defaultResourceId)
    }

    /**
     * Load the cover of a given entry into an ImageView
     */
    @JvmOverloads
    @Suppress("LongParameterList", "ComplexCondition")
    fun loadImage(
        view: View?,
        id: String?,
        key: String?,
        large: Boolean,
        size: Int,
        defaultResourceId: Int = R.drawable.unknown_album
    ) {
        val requestedSize = resolveSize(size, large)

        if (id != null && key != null && id.isNotEmpty() && view is ImageView) {
            val request = ImageRequest.CoverArt(
                id,
                key,
                view,
                requestedSize,
                placeHolderDrawableRes = defaultResourceId,
                errorDrawableRes = defaultResourceId
            )
            load(request)
        } else if (view is ImageView) {
            view.setImageResource(defaultResourceId)
        }
    }

    /**
     * Load the avatar of a given user into an ImageView
     */
    fun loadAvatarImage(view: ImageView, username: String) {
        if (username.isNotEmpty()) {
            val request = ImageRequest.Avatar(
                username,
                view,
                placeHolderDrawableRes = R.drawable.ic_contact_picture,
                errorDrawableRes = R.drawable.ic_contact_picture
            )
            load(request)
        } else {
            view.setImageResource(R.drawable.ic_contact_picture)
        }
    }

    fun cacheArtistPicture(artist: Artist) {
        if (artist.coverArt == null) return
        val key = FileUtil.getArtistArtKey(artist.name, false)
        val file = FileUtil.getAlbumArtFile(key)
        downloadCoverArt(artist.coverArt!!, file)
    }

    /**
     * Download a cover art file of a Track and cache it on disk
     */
    fun downloadCoverArt(track: Track) {
        if (track.coverArt == null) return
        downloadCoverArt(track.coverArt!!, FileUtil.getAlbumArtFile(track))
    }

    fun downloadCoverArt(id: String, file: String) = launch {
        if (id.isBlank()) return@launch

        withContext(Dispatchers.IO) {
            // Return if have a cache hit
            if (File(file).exists()) return@withContext

            // If another coroutine has started caching, abort
            if (cacheInProgress[file] != null) return@withContext

            // Always download the large size..
            val size = config.largeSize

            File(file).createNewFile()

            // Query the API
            Timber.d("Loading cover art for: %s", id)
            try {
                val response = API.getCoverArt(id, size.toLong()).execute().toStreamResponse()

                response.throwOnFailure()

                // Check for failure
                if (response.stream == null) return@withContext

                // Write Response stream to file
                var inputStream: InputStream? = null
                try {
                    inputStream = response.stream
                    val bytes = inputStream!!.readBytes()
                    var outputStream: OutputStream? = null
                    try {
                        outputStream = FileOutputStream(file)
                        outputStream.write(bytes)
                    } finally {
                        outputStream.safeClose()
                    }
                } finally {
                    inputStream.safeClose()
                }
            } catch (all: Exception) {
                Timber.w(all)
                runCatching { File(file).delete() }
            } finally {
                cacheInProgress.remove(file)?.countDown()
            }
        }
    }

    private fun resolveSize(requested: Int, large: Boolean): Int {
        return if (requested <= 0) {
            if (large) config.largeSize else config.defaultSize
        } else {
            requested
        }
    }

    private fun calculateMemoryCacheSize(context: Context): Int {
        val am = ContextCompat.getSystemService(
            context,
            ActivityManager::class.java
        )
        val largeHeap = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
        val memoryClass = if (largeHeap) am!!.largeMemoryClass else am!!.memoryClass
        // Target 25% of the available heap.
        @Suppress("MagicNumber")
        return (1024L * 1024L * memoryClass / 4).toInt()
    }
}

/**
 * Data classes to hold all the info we need later on to process the request
 */
sealed class ImageRequest(
    val placeHolderDrawableRes: Int? = null,
    val errorDrawableRes: Int? = null,
    val imageView: ImageView?
) {
    class CoverArt(
        val entityId: String,
        val cacheKey: String,
        imageView: ImageView?,
        val size: Int,
        placeHolderDrawableRes: Int? = null,
        errorDrawableRes: Int? = null
    ) : ImageRequest(
        placeHolderDrawableRes,
        errorDrawableRes,
        imageView
    )

    class Avatar(
        val username: String,
        imageView: ImageView,
        placeHolderDrawableRes: Int? = null,
        errorDrawableRes: Int? = null
    ) : ImageRequest(
        placeHolderDrawableRes,
        errorDrawableRes,
        imageView
    )
}

/**
 * Used to configure an instance of the ImageLoader
 */
data class ImageLoaderConfig(
    val largeSize: Int = 0,
    val defaultSize: Int = 0,
    val cacheFolder: File?
)
