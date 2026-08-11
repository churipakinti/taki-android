/*
 * ImageLoaderProvider.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.subsonic

import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.imageloader.ImageLoader
import org.moire.ultrasonic.imageloader.ImageLoaderConfig
import org.moire.ultrasonic.util.CoroutinePatterns
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Handles the lifetime of the Image Loader
 */
class ImageLoaderProvider :
    KoinComponent,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private var imageLoader: ImageLoader? = null
    private var serverID: String = get(named("ServerID"))

    @Synchronized
    fun clearImageLoader() {
        imageLoader = null
    }

    init {
        Timber.d("Prepping Loader")
        // Populate the ImageLoader async & early
        launch {
            getImageLoader()
        }
    }

    @Synchronized
    fun getImageLoader(): ImageLoader {
        // We need to generate a new ImageLoader if the server has changed...
        val currentID = get<String>(named("ServerID"))
        if (imageLoader == null || currentID != serverID) {
            // Isolated connection pool -- see MusicServiceModule.kt's "ImageSubsonicAPIClient".
            imageLoader = ImageLoader(
                UApp.applicationContext(),
                get(named("ImageSubsonicAPIClient")),
                config
            )
            serverID = currentID

            launch {
                FileUtil.ensureAlbumArtDirectory()
            }
        }

        return imageLoader!!
    }

    fun executeOn(cb: (iL: ImageLoader) -> Unit) {
        launch(CoroutinePatterns.loggingExceptionHandler) {
            val iL = getImageLoader()
            withContext(Dispatchers.Main) {
                cb(iL)
            }
        }
    }

    companion object {
        val config by lazy {
            var defaultSize = 0
            val fallbackImage = ResourcesCompat.getDrawable(
                UApp.applicationContext().resources,
                R.drawable.unknown_album,
                null
            )

            // Determine the density-dependent image sizes by taking the fallback album
            // image and querying its size.
            if (fallbackImage != null) {
                defaultSize = fallbackImage.intrinsicHeight
            }

            ImageLoaderConfig(
                Util.getMaxDisplayMetric(),
                defaultSize,
                FileUtil.albumArtDirectory
            )
        }
    }
}
