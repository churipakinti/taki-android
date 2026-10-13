/*
 * ShareCardStore.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

internal const val SHARE_CARD_MIME_TYPE = "image/png"

/** Newest cards kept on disk; older ones are pruned on every write. */
internal const val SHARE_CARD_MAX_FILES = 3

/** Anything older than this is pruned even when under the count cap. */
internal const val SHARE_CARD_MAX_AGE_MS = 24L * 60 * 60 * 1000

private const val SHARE_DIR = "share"
private const val PNG_QUALITY = 100

/**
 * The share card's temporary home: `cache/share/`, the only directory the `FileProvider`
 * (`share_file_paths.xml`) exposes. Files are named `taki-share-<timestamp>-<n>.png` - nothing
 * about the track, server or user. Cleanup is bounded rather than immediate, because the
 * receiving app may still be reading the file after the Sharesheet opens: [prune] runs on each
 * [write] and keeps at most [SHARE_CARD_MAX_FILES] files younger than [SHARE_CARD_MAX_AGE_MS].
 */
class ShareCardStore(
    cacheDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dir = File(cacheDir, SHARE_DIR)
    private var counter = 0

    /** Encodes [bitmap] as PNG into a fresh file and prunes old ones. Throws [IOException]. */
    @Synchronized
    fun write(bitmap: Bitmap): File {
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("share dir")
        val file = File(dir, "taki-share-${clock()}-${counter++}.png")
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) throw IOException("png encode")
        }
        prune(keep = file)
        return file
    }

    /** Deletes everything but the newest [SHARE_CARD_MAX_FILES] fresh files (and [keep]). */
    @Synchronized
    fun prune(keep: File? = null) {
        val now = clock()
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("taki-share-") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        files.forEachIndexed { index, f ->
            val stale = now - f.lastModified() > SHARE_CARD_MAX_AGE_MS
            if (f != keep && (index >= SHARE_CARD_MAX_FILES || stale)) f.delete()
        }
    }

    fun files(): List<File> = dir.listFiles()?.toList().orEmpty()

    companion object {
        fun authority(context: Context) = "${context.packageName}.shareprovider"

        /** Content URI for [file]; throws unless it lives under `cache/share/`. */
        fun uriFor(context: Context, file: File): Uri =
            FileProvider.getUriForFile(context, authority(context), file)

        /**
         * `ACTION_SEND` for a PNG: the content URI as `EXTRA_STREAM` and also as `ClipData` (the
         * reliable way to propagate the read grant to the chosen app), wrapped in the standard
         * chooser. No `file://`, no storage permission.
         */
        fun shareIntent(uri: Uri, chooserTitle: String): Intent {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = SHARE_CARD_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return Intent.createChooser(send, chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
