/*
 * Storage.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import timber.log.Timber

/**
 * Provides filesystem access abstraction which works
 * both on File based paths (when using the internal directory for storing media files)
 * and Storage Access Framework Uris (when using a custom directory)
 */
object Storage {

    val mediaRoot: ResettableLazy<AbstractFile> = ResettableLazy {
        val ret = getRoot()
        rootNotFoundError = ret.second
        ret.first
    }

    var rootNotFoundError: Boolean = false

    fun reset() {
        StorageFile.storageFilePathDictionary.clear()
        StorageFile.notExistingPathDictionary.clear()
        mediaRoot.reset()
        rootNotFoundError = false
        Timber.i("StorageFile caches were reset")
    }

    fun checkForErrorsWithCustomRoot() {
        if (rootNotFoundError) {
            Settings.customCacheLocation = false
            Settings.cacheLocationUri = ""
            Util.toast(R.string.settings_cache_location_error, true, UApp.applicationContext())
        }
    }

    fun getOrCreateFileFromPath(path: String): AbstractFile =
        mediaRoot.value.getOrCreateFileFromPath(path)

    fun isPathExists(path: String): Boolean = mediaRoot.value.isPathExists(path)

    fun getFromPath(path: String): AbstractFile? = mediaRoot.value.getFromPath(path)

    fun createDirsOnPath(path: String) {
        mediaRoot.value.createDirsOnPath(path)
    }

    fun rename(pathFrom: String, pathTo: String) {
        mediaRoot.value.rename(pathFrom, pathTo)
    }

    fun rename(pathFrom: AbstractFile, pathTo: String) {
        mediaRoot.value.rename(pathFrom, pathTo)
    }

    fun renameOrDeleteIfAlreadyExists(pathFrom: AbstractFile, pathTo: String) {
        try {
            rename(pathFrom, pathTo)
        } catch (ignored: FileAlreadyExistsException) {
            // Play console has revealed a crash when for some reason both files exist
            delete(pathFrom.path)
        } catch (ignored: Exception) {
            // Ignore any other exceptions, such as NoSuchFileException etc.
        }
    }

    fun delete(path: String): Boolean {
        // Some implementations will return false on Error,
        // others will throw a FileNotFoundException...
        // Handle both here..

        val success: Boolean

        try {
            val storageFile = getFromPath(path)
            success = storageFile?.delete() == true
        } catch (all: Exception) {
            Timber.d(all, "Failed to delete file $path")
            return false
        }

        if (!success) {
            Timber.d("Failed to delete file %s", path)
        }

        return success
    }

    private fun getRoot(): Pair<AbstractFile, Boolean> {
        return if (Settings.customCacheLocation) {
            if (Settings.cacheLocationUri.isBlank()) return Pair(getDefaultRoot(), true)
            val documentFile = DocumentFile.fromTreeUri(
                UApp.applicationContext(),
                Settings.cacheLocationUri.toUri()
            ) ?: return Pair(getDefaultRoot(), true)
            if (!documentFile.exists()) return Pair(getDefaultRoot(), true)
            Pair(
                StorageFile(null, documentFile.uri, documentFile.name!!, documentFile.isDirectory),
                false
            )
        } else {
            Pair(getDefaultRoot(), false)
        }
    }

    private fun getDefaultRoot(): JavaFile {
        val file = File(FileUtil.defaultMusicDirectory.path)
        return JavaFile(null, file)
    }
}
