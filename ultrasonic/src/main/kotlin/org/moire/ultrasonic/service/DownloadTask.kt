/*
 * DownloadTask.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.SystemClock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.MetaDatabase
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadState.Companion.isFinalState
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.AudioContainerSniffer
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.FileUtil.copyWithProgress
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.safeClose
import timber.log.Timber

private const val MAX_RETRIES = 5
private const val REFRESH_INTERVAL = 50
private const val RESUME_SUFFIX = ".resume"

class DownloadTask(
    val downloadTrack: DownloadableTrack,
    private val scope: CoroutineScope,
    private val stateChangedCallback: (DownloadableTrack, DownloadState, progress: Int?) -> Unit
) : KoinComponent {
    private val musicService = MusicServiceFactory.getMusicService()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    private var job: Job? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var lastPostTime: Long = 0
    private var state: DownloadState = DownloadState.UNKNOWN

    private fun setState(state: DownloadState, progress: Int?) {
        this.state = state
        stateChangedCallback(downloadTrack, state, progress)
    }

    private suspend fun checkIfExists(): Boolean {
        // An existing copy may carry the extension of its real (possibly transcoded) format
        val existingPinned = FileUtil.resolveExistingFile(downloadTrack.pinnedFile)
        if (existingPinned != null) {
            Timber.i("%s already exists. Skipping.", existingPinned)
            setState(DownloadState.PINNED, null)
            return true
        }

        val existingComplete = FileUtil.resolveExistingFile(downloadTrack.completeFile)
        if (existingComplete != null) {
            var newStatus: DownloadState = DownloadState.DONE
            if (downloadTrack.pinned) {
                Storage.rename(
                    existingComplete,
                    FileUtil.withExtension(
                        downloadTrack.pinnedFile,
                        FileUtil.getExtension(existingComplete)
                    )
                )
                newStatus = DownloadState.PINNED
            } else {
                Timber.i(
                    "%s already exists. Skipping.",
                    existingComplete
                )
            }

            // Hidden feature: If track is toggled between pinned/saved, refresh the metadata..
            try {
                downloadTrack.track.cacheMetadataAndArtwork()
            } catch (ignore: Exception) {
                Timber.w(ignore)
            }
            setState(newStatus, null)
            return true
        }

        return false
    }

    fun download() {
        setState(DownloadState.DOWNLOADING, null)

        val bitRate = if (downloadTrack.pinned) Settings.maxBitRatePinning else Settings.maxBitRate
        val save = downloadTrack.pinned && Settings.pinWithHighestQuality
        val identity = RequestIdentity(
            downloadTrack.track.id,
            if (save) "download" else "stream",
            bitRate.coerceAtLeast(0)
        )

        // Bytes may only be appended to a partial file that provably belongs to the same
        // representation (issue #40); otherwise the transfer starts again from zero.
        val partialLength = Storage.getFromPath(downloadTrack.partialFile)?.length ?: 0
        val record = if (partialLength > 0) readResumeRecord() else null
        val outcome = DownloadResume.open(
            partialLength,
            record,
            identity,
            ::discardPartial
        ) { offset -> musicService.getDownloadInputStream(downloadTrack.track, offset, bitRate, save) }
        val response = outcome.response
        val resumed = outcome.resumed

        inputStream = response.stream ?: throw IOException("Download failed: HTTP ${response.httpCode}")

        val baseLength = if (resumed) partialLength else 0L
        if (resumed) {
            Timber.i("Resuming at byte %d", baseLength)
        } else {
            writeResumeRecord(DownloadResume.recordFor(identity, response))
        }

        // A 200 replaces the file, a 206 (resumed, or a fresh range from zero) extends it.
        outputStream = Storage.getOrCreateFileFromPath(downloadTrack.partialFile)
            .getFileOutputStream(response.isPartial)

        val len = inputStream!!.copyWithProgress(outputStream!!) { totalBytesCopied ->
            publishProgressUpdate(baseLength + totalBytesCopied)
        }

        Timber.i("Downloaded %d bytes to %s", len, downloadTrack.partialFile)

        inputStream?.close()
        outputStream?.flush()
        outputStream?.close()

        // Never promote a truncated transfer to a finished file
        val expected = DownloadResume.recordFor(identity, response).totalLength
        val actual = Storage.getFromPath(downloadTrack.partialFile)?.length ?: 0
        if (expected >= 0 && actual != expected) {
            throw IOException("Incomplete download: $actual of $expected bytes")
        }
    }

    private val resumeRecordPath: String get() = downloadTrack.partialFile + RESUME_SUFFIX

    private fun readResumeRecord(): ResumeRecord? = try {
        Storage.getFromPath(resumeRecordPath)
            ?.takeIf { Storage.isPathExists(resumeRecordPath) }
            ?.getFileInputStream()?.use { ResumeRecord.parse(it.readBytes().decodeToString()) }
    } catch (e: IOException) {
        Timber.w(e, "Could not read the resume record")
        null
    }

    private fun writeResumeRecord(record: ResumeRecord) {
        Storage.getOrCreateFileFromPath(resumeRecordPath).getFileOutputStream(false).use {
            it.write(record.serialize().encodeToByteArray())
        }
    }

    private fun discardPartial() {
        Storage.delete(downloadTrack.partialFile)
        Storage.delete(resumeRecordPath)
    }

    private fun publishProgressUpdate(totalBytesCopied: Long) {
        // Check if we are cancelled...
        if (job?.isCancelled == true) {
            throw CancellationException()
        }

        // Manual throttling to avoid overloading Rx
        if (SystemClock.elapsedRealtime() - lastPostTime > REFRESH_INTERVAL) {
            lastPostTime = SystemClock.elapsedRealtime()

            // If the file size is unknown we can only provide null as the progress
            val size = downloadTrack.track.size ?: 0
            val progress = if (size <= 0) {
                null
            } else {
                (totalBytesCopied * 100 / (size)).toInt()
            }

            setState(DownloadState.DOWNLOADING, progress)
        }
    }

    private suspend fun afterDownload() {
        try {
            downloadTrack.track.cacheMetadataAndArtwork()
        } catch (ignore: Exception) {
            Timber.w(ignore)
        }

        // The server may have transcoded the download, so name the file after the bytes received
        val actualExtension = detectDownloadedExtension()
        val target = if (downloadTrack.pinned) downloadTrack.pinnedFile else downloadTrack.completeFile
        val finalPath = withActualExtension(target, actualExtension)

        Storage.rename(downloadTrack.partialFile, finalPath)
        Storage.delete(resumeRecordPath)
        Timber.i("Renamed file to $finalPath")
        if (downloadTrack.pinned) {
            setState(DownloadState.PINNED, null)
            Util.scanMedia(finalPath)
        } else {
            setState(DownloadState.DONE, null)
        }
    }

    private fun withActualExtension(path: String, actualExtension: String?): String =
        if (actualExtension == null || actualExtension == FileUtil.getExtension(path)) {
            path
        } else {
            Timber.w(
                "Downloaded data is %s, not %s: storing it with the matching extension",
                actualExtension,
                FileUtil.getExtension(path)
            )
            FileUtil.withExtension(path, actualExtension)
        }

    /** Extension matching the container of the downloaded bytes, or null when unrecognised. */
    private fun detectDownloadedExtension(): String? = try {
        Storage.getFromPath(downloadTrack.partialFile)?.getFileInputStream()?.use { stream ->
            val header = ByteArray(AudioContainerSniffer.HEADER_BYTES)
            var read = 0
            while (read < header.size) {
                val n = stream.read(header, read, header.size - read)
                if (n < 0) break
                read += n
            }
            AudioContainerSniffer.detectExtension(header, read)
        }
    } catch (e: IOException) {
        Timber.w(e, "Could not inspect the downloaded file")
        null
    }

    private fun onCompletion(e: Throwable?) {
        if (e is CancellationException) {
            if (!state.isFinalState()) {
                Timber.w(e, "CompletionHandler ${downloadTrack.pinnedFile}")
                setState(DownloadState.CANCELLED, null)
            }
        } else if (e != null) {
            Timber.w(e, "CompletionHandler ${downloadTrack.pinnedFile}")
            if (downloadTrack.tryCount < MAX_RETRIES) {
                setState(DownloadState.RETRYING, null)
            } else {
                setState(DownloadState.FAILED, null)
            }
        }
        inputStream.safeClose()
        outputStream.safeClose()
    }

    private fun exceptionHandler(): CoroutineExceptionHandler = CoroutineExceptionHandler {
            _,
            exception
        ->
        Timber.w(exception, "Exception in DownloadTask ${downloadTrack.pinnedFile}")
        Storage.delete(downloadTrack.completeFile)
        Storage.delete(downloadTrack.pinnedFile)
    }

    fun start() {
        Timber.i("Launching new Job ${downloadTrack.pinnedFile}")
        job = scope.launch(exceptionHandler()) {
            FileUtil.createDirectoryForParent(downloadTrack.pinnedFile)
            if (!checkIfExists() && isActive) {
                download()
                afterDownload()
            }
        }

        job!!.invokeOnCompletion(::onCompletion)
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun Track.cacheMetadataAndArtwork() {
        val onlineDB = activeServerProvider.getActiveMetaDatabase()
        val offlineDB = activeServerProvider.offlineMetaDatabase

        var artistId: String? = if (artistId.isNullOrEmpty()) null else artistId
        val albumId: String? = if (albumId.isNullOrEmpty()) null else albumId

        var album: Album? = null
        var directArtist: Artist? = null
        var compilationArtist: Artist? = null

        // Sometime in compilation albums, the individual tracks won't have an Artist id
        // In this case, try to get the ArtistId of the album...
        if (artistId == null && albumId != null) {
            album = musicService.getAlbum(albumId, null, false)
            artistId = album?.artistId
        }

        // Cache the artist
        if (artistId != null) {
            directArtist = cacheArtist(onlineDB, offlineDB, artistId)
        }

        // Now cache the album
        if (albumId != null) {
            if (album == null) {
                // This is a cached call
                val albums = musicService.getAlbumsOfArtist(artistId!!, null, false)
                album = albums.find { it.id == albumId }
            }

            if (album != null) {
                // Often the album entity returned from the server won't have the path set.
                if (album.path.isNullOrEmpty()) album.path = FileUtil.getParentPath(path)

                offlineDB.albumDao().insert(album)

                // If the album is a Compilation, also cache the Album artist
                if (album.artistId != null && album.artistId != artistId) {
                    compilationArtist = cacheArtist(onlineDB, offlineDB, album.artistId!!)
                }
            }
        }

        // Now cache the track data
        offlineDB.trackDao().insert(this)

        // Download the largest size that we can display in the UI
        imageLoaderProvider.executeOn { imageLoader ->
            imageLoader.downloadCoverArt(this@cacheMetadataAndArtwork)
            // Cache small copies of the Artist picture
            directArtist?.let { imageLoader.cacheArtistPicture(it) }
            compilationArtist?.let { imageLoader.cacheArtistPicture(it) }
        }
    }

    private suspend fun cacheArtist(
        onlineDB: MetaDatabase,
        offlineDB: MetaDatabase,
        artistId: String
    ): Artist? {
        var artist: Artist? = onlineDB.artistDao().get(artistId)

        // If we are downloading a new album, and the user has not visited the Artists list
        // recently, then the artist won't be in the database.
        if (artist == null) {
            val artists: List<Artist> = musicService.getArtists(true)
            artist = artists.find {
                it.id == artistId
            }
        }

        // If we have found an artist, cache it.
        if (artist != null) {
            offlineDB.artistDao().insert(artist)
        }

        return artist
    }
}
