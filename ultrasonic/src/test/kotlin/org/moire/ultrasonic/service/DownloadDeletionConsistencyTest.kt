/*
 * DownloadDeletionConsistencyTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.AbstractFile
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.FileUtil.getCompleteFile
import org.moire.ultrasonic.util.FileUtil.getPartialFile
import org.moire.ultrasonic.util.FileUtil.getPinnedFile
import org.moire.ultrasonic.util.ResettableLazy
import org.moire.ultrasonic.util.Storage
import org.robolectric.RobolectricTestRunner

/**
 * Regression guard for the [DownloadService] delete path.
 *
 * The Runtime Regression Audit found that `deleteAsync()` removed a track's offline/database
 * record (via [CacheCleaner.cleanDatabaseSelective]) and emitted the non-downloaded
 * [DownloadState.IDLE] **unconditionally** -- the `Boolean` results of the three
 * [Storage.delete] calls were discarded. A media file that cannot be deleted (open handle,
 * read-only volume, permissions) would therefore be left orphaned on disk while every
 * persistent trace that it was ever downloaded was wiped.
 *
 * Contract locked in here:
 * ```
 * physical media file removed (or already absent)
 *   -> offline/database record removed, IDLE emitted
 * physical media file delete attempted and failed, file still on disk
 *   -> offline/database record kept, no false IDLE, real state re-emitted
 * ```
 *
 * [Storage]'s root is swapped for an in-memory [FakeMediaRoot] so a deletion failure is
 * simulated deterministically on every OS, with no reliance on real filesystem permissions.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadDeletionConsistencyTest {

    private lateinit var fakeRoot: FakeMediaRoot
    private lateinit var cacheCleaner: CacheCleaner
    private var startedKoin = false

    private val emissions =
        Collections.synchronizedList(mutableListOf<RxBus.TrackDownloadState>())
    private val subscription = RxBus.trackDownloadStatePublisher.subscribe { emissions.add(it) }

    private val track = Track(
        id = "/fake-music/Artist/Album/01-Song.mp3",
        title = "Song",
        artist = "Artist",
        album = "Album",
        suffix = "mp3",
        path = "Artist/Album/01-Song.mp3",
        isDirectory = false
    )

    private val otherTrack = Track(
        id = "/fake-music/Artist/Album/02-Other.mp3",
        title = "Other",
        artist = "Artist",
        album = "Album",
        suffix = "mp3",
        path = "Artist/Album/02-Other.mp3",
        isDirectory = false
    )

    private val Track.pinned get() = getPinnedFile()
    private val Track.complete get() = getCompleteFile()
    private val Track.partial get() = getPartialFile()

    @Before
    fun setUp() {
        RobolectricUAppContext.install()

        cacheCleaner = mock()
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                modules(
                    module {
                        single<Context> { ApplicationProvider.getApplicationContext() }
                        single<CacheCleaner> { cacheCleaner }
                    }
                )
            }
            startedKoin = true
        }

        fakeRoot = FakeMediaRoot()
        installRoot(fakeRoot)

        drainDownloadServiceState()
        emissions.clear()
    }

    @After
    fun tearDown() {
        subscription.dispose()
        drainDownloadServiceState()
        Storage.reset()
        if (startedKoin) {
            stopKoin()
            startedKoin = false
        }
    }

    // --- tests -------------------------------------------------------------------------------

    @Test
    fun `successful deletion removes the file, the offline record and emits IDLE`() {
        fakeRoot.present += track.pinned

        runBlocking { DownloadService.deleteAsync(listOf(track)) }

        assertFalse("pinned media file must be gone", fakeRoot.present.contains(track.pinned))
        assertFalse("no .complete artifact left", fakeRoot.present.contains(track.complete))
        assertFalse("no .partial artifact left", fakeRoot.present.contains(track.partial))
        verify(cacheCleaner).cleanDatabaseSelective(track)
        assertEquals(
            "IDLE must be the emitted state for a real deletion",
            listOf(DownloadState.IDLE),
            emissionsFor(track)
        )
        assertEquals(DownloadState.IDLE, DownloadService.getDownloadState(track))
    }

    @Test
    fun `a failed physical delete keeps the offline record and never emits a false IDLE`() {
        fakeRoot.present += track.pinned
        fakeRoot.undeletable += track.pinned

        runBlocking { DownloadService.deleteAsync(listOf(track)) }

        assertTrue(
            "the undeletable media file is still on disk",
            fakeRoot.present.contains(track.pinned)
        )
        verify(cacheCleaner, never()).cleanDatabaseSelective(any())
        assertFalse(
            "must not emit IDLE while the file is still present, saw: ${emissionsFor(track)}",
            emissionsFor(track).contains(DownloadState.IDLE)
        )
        assertEquals(
            "download must still report as downloaded",
            DownloadState.PINNED,
            DownloadService.getDownloadState(track)
        )
        assertEquals(listOf(DownloadState.PINNED), emissionsFor(track))
    }

    @Test
    fun `an already-missing file is an idempotent success and still clears stale offline state`() {
        // Nothing is present on disk, but a stale offline/database row still exists.
        runBlocking { DownloadService.deleteAsync(listOf(track)) }

        verify(cacheCleaner).cleanDatabaseSelective(track)
        assertEquals(listOf(DownloadState.IDLE), emissionsFor(track))
        assertEquals(DownloadState.IDLE, DownloadService.getDownloadState(track))
    }

    @Test
    fun `a leftover undeletable partial does not trap the offline record`() {
        // The real media (.complete / pinned) is gone; only transient .partial scratch remains
        // and cannot be removed. That must not block offline cleanup.
        fakeRoot.present += track.partial
        fakeRoot.undeletable += track.partial

        runBlocking { DownloadService.deleteAsync(listOf(track)) }

        verify(cacheCleaner).cleanDatabaseSelective(track)
        assertEquals(listOf(DownloadState.IDLE), emissionsFor(track))
    }

    @Test
    fun `batch delete handles each track independently`() {
        fakeRoot.present += track.pinned
        fakeRoot.present += otherTrack.pinned
        fakeRoot.undeletable += track.pinned

        runBlocking { DownloadService.deleteAsync(listOf(track, otherTrack)) }

        // Failing track: file kept, record kept, no false IDLE.
        assertTrue(fakeRoot.present.contains(track.pinned))
        verify(cacheCleaner, never()).cleanDatabaseSelective(track)
        assertFalse(emissionsFor(track).contains(DownloadState.IDLE))

        // Healthy track: fully cleaned.
        assertFalse(fakeRoot.present.contains(otherTrack.pinned))
        verify(cacheCleaner).cleanDatabaseSelective(otherTrack)
        assertEquals(listOf(DownloadState.IDLE), emissionsFor(otherTrack))
    }

    // --- helpers ----------------------------------------------------------------------------

    private fun emissionsFor(t: Track): List<DownloadState> =
        synchronized(emissions) { emissions.filter { it.id == t.id }.map { it.state } }

    private fun installRoot(root: AbstractFile) {
        val lazyRefField = ResettableLazy::class.java.getDeclaredField("lazyRef")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val ref = lazyRefField.get(Storage.mediaRoot) as AtomicReference<Lazy<AbstractFile>>
        ref.set(lazyOf(root))
    }

    @Suppress("UNCHECKED_CAST")
    private fun drainDownloadServiceState() {
        listOf("downloadQueue", "activeDownloads", "failedList").forEach { name ->
            val field = DownloadService::class.java.getDeclaredField(name)
                .apply { isAccessible = true }
            when (val value = field.get(null)) {
                is MutableCollection<*> -> value.clear()
                is MutableMap<*, *> -> value.clear()
            }
        }
        DownloadService.observableDownloads.postValue(emptyList())
    }

    /**
     * A fully in-memory [AbstractFile] tree standing in for [Storage]'s root. `present` is the
     * set of paths that "exist"; `undeletable` paths report `delete() == false` and stay.
     */
    private class FakeMediaRoot : AbstractFile() {
        val present: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val undeletable: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

        override val name = "fake-music"
        override val isDirectory = true
        override val isFile = false
        override val length = 0L
        override val lastModified = 0L
        override val path = "/fake-music"
        override val parent: AbstractFile? = null

        override fun delete(): Boolean = false
        override fun listFiles(): Array<AbstractFile> = emptyArray()
        override fun getFileOutputStream(append: Boolean): OutputStream =
            throw UnsupportedOperationException()
        override fun getFileInputStream(): InputStream = throw UnsupportedOperationException()
        override fun getDocumentFileDescriptor(openMode: String) = null
        override fun getOrCreateFileFromPath(path: String): AbstractFile {
            present += path
            return Node(path)
        }
        override fun isPathExists(path: String): Boolean = present.contains(path)
        override fun getFromPath(path: String): AbstractFile = Node(path)
        override fun createDirsOnPath(path: String) = Unit
        override fun rename(pathFrom: AbstractFile, pathTo: String) {
            if (present.remove(pathFrom.path)) present += pathTo
        }

        private inner class Node(override val path: String) : AbstractFile() {
            override val name = path.substringAfterLast('/')
            override val isDirectory = false
            override val isFile get() = present.contains(path)
            override val length = 0L
            override val lastModified = 0L
            override val parent: AbstractFile? = this@FakeMediaRoot

            override fun delete(): Boolean {
                if (undeletable.contains(path)) return false
                return present.remove(path)
            }
            override fun listFiles(): Array<AbstractFile> = emptyArray()
            override fun getFileOutputStream(append: Boolean): OutputStream =
                throw UnsupportedOperationException()
            override fun getFileInputStream(): InputStream = throw UnsupportedOperationException()
            override fun getDocumentFileDescriptor(openMode: String) = null
            override fun getOrCreateFileFromPath(path: String) =
                this@FakeMediaRoot.getOrCreateFileFromPath(path)
            override fun isPathExists(path: String): Boolean = present.contains(path)
            override fun getFromPath(path: String): AbstractFile = Node(path)
            override fun createDirsOnPath(path: String) = Unit
            override fun rename(pathFrom: AbstractFile, pathTo: String) =
                this@FakeMediaRoot.rename(pathFrom, pathTo)
        }
    }
}
