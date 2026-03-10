/*
 * AlbumArtContentProvider.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.provider

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.Locale
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.FileUtil
import timber.log.Timber

class AlbumArtContentProvider :
    ContentProvider(),
    KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    companion object {
        fun mapArtworkToContentProviderUri(track: Track?): Uri? {
            if (track?.coverArt.isNullOrBlank()) return null
            val domain = UApp.applicationContext().packageName + ".provider.AlbumArtContentProvider"
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(domain)
                // currently only large files are cached
                .path(
                    String.format(
                        Locale.ROOT,
                        "%s|%s",
                        track!!.coverArt,
                        FileUtil.getAlbumArtKey(track, true)
                    )
                )
                .build()
        }
    }

    override fun onCreate(): Boolean {
        Timber.i("AlbumArtContentProvider.onCreate called")
        return true
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val parts = uri.path?.trim('/')?.split('|')
        if (parts?.count() != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null

        val albumArtFile = FileUtil.getAlbumArtFile(parts[1])
        Timber.d("AlbumArtContentProvider openFile id: %s; file: %s", parts[0], albumArtFile)

        // TODO: Check if the dependency on the image loader could be removed.
        // TODO: This method can be called outside of our regular lifecycle, where Koin might not exist yet
        imageLoaderProvider.executeOn {
            it.downloadCoverArt(parts[0], albumArtFile)
        }
        val file = File(albumArtFile)
        if (!file.exists()) return null

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ) = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

    override fun getType(uri: Uri): String? = null

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String> =
        arrayOf("image/jpeg", "image/png", "image/gif")
}
