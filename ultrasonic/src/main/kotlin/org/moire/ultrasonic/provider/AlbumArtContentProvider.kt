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
        // The only legitimate cache keys are the ones FileUtil.getAlbumArtKey() actually
        // produces for this URI (large-size album art): a 32-char lowercase MD5 hex digest
        // plus FileUtil.SUFFIX_LARGE. Anything else -- including traversal syntax like ".."
        // or path separators -- is rejected outright rather than sanitized.
        private val CACHE_KEY_PATTERN = Regex("^[0-9a-f]{32}\\Q${FileUtil.SUFFIX_LARGE}\\E$")
        private const val MAX_KNOWN_ARTWORK_ENTRIES = 2000

        // Cache keys this app has itself minted, mapped to the cover art id they were minted
        // for. Populated only by mapArtworkToContentProviderUri() below and cross-checked in
        // openFile(), so a caller with access to this exported provider (e.g. the home screen
        // launcher resolving the widget's artwork URI) cannot substitute an arbitrary coverArt
        // id and make Taki fetch it from the authenticated server on the caller's behalf --
        // only (id, cacheKey) pairs Taki already generated for its own UI are accepted.
        private val knownArtwork = object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
                size > MAX_KNOWN_ARTWORK_ENTRIES
        }

        private fun isKnownArtwork(coverArtId: String, cacheKey: String): Boolean =
            synchronized(knownArtwork) { knownArtwork[cacheKey] == coverArtId }

        // Canonical, boundary-correct containment check (walks the resolved parent chain)
        // rather than a fragile string-prefix comparison.
        private fun isContainedIn(file: File, directory: File): Boolean {
            val root = directory.canonicalFile
            var current = file.canonicalFile.parentFile
            while (current != null) {
                if (current == root) return true
                current = current.parentFile
            }
            return false
        }

        fun mapArtworkToContentProviderUri(track: Track?): Uri? {
            if (track?.coverArt.isNullOrBlank()) return null
            val coverArtId = track.coverArt!!
            // currently only large files are cached
            val cacheKey = FileUtil.getAlbumArtKey(track, true) ?: return null
            synchronized(knownArtwork) { knownArtwork[cacheKey] = coverArtId }
            val domain = UApp.applicationContext().packageName + ".provider.AlbumArtContentProvider"
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(domain)
                .path(String.format(Locale.ROOT, "%s|%s", coverArtId, cacheKey))
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

        val coverArtId = parts[0]
        val cacheKey = parts[1]

        if (!CACHE_KEY_PATTERN.matches(cacheKey)) {
            Timber.w("AlbumArtContentProvider rejected malformed cache key")
            return null
        }

        if (!isKnownArtwork(coverArtId, cacheKey)) {
            Timber.w("AlbumArtContentProvider rejected unrecognized cover art id")
            return null
        }

        val albumArtDir = FileUtil.albumArtDirectory
        val albumArtFile = File(FileUtil.getAlbumArtFile(cacheKey))

        if (!isContainedIn(albumArtFile, albumArtDir)) {
            Timber.w("AlbumArtContentProvider rejected file outside artwork directory")
            return null
        }

        Timber.d("AlbumArtContentProvider openFile id: %s; file: %s", coverArtId, albumArtFile)

        // TODO: Check if the dependency on the image loader could be removed.
        // TODO: This method can be called outside of our regular lifecycle, where Koin might not exist yet
        imageLoaderProvider.executeOn {
            it.downloadCoverArt(coverArtId, albumArtFile.path)
        }

        if (!albumArtFile.exists()) return null

        return ParcelFileDescriptor.open(albumArtFile, ParcelFileDescriptor.MODE_READ_ONLY)
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
