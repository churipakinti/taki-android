/*
 * ArtworkBitmapLoader.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.imageloader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Executors
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

@SuppressLint("UnsafeOptInUsageError")
class ArtworkBitmapLoader : BitmapLoader, KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    private val executorService: ListeningExecutorService by lazy {
        MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor()
        )
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        // TODO: Implement?
        return true
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return executorService.submit<Bitmap> {
            decode(
                data
            )
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return load(uri)
    }

    private fun decode(data: ByteArray): Bitmap {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        return bitmap ?: throw IllegalArgumentException("Could not decode bitmap")
    }

    @Throws(IOException::class)
    private fun load(uri: Uri): ListenableFuture<Bitmap> {
        val parts = uri.path?.trim('/')?.split('|')

        require(parts!!.count() == 2) { "Invalid bitmap Uri" }
        return imageLoaderProvider.getImageLoader().getImage(parts[0], parts[1], false, 0)
    }
}
