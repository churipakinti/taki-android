/*
 * DurableStateFileTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import androidx.core.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.ObjectOutputStream
import java.io.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.PlaybackState
import org.moire.ultrasonic.service.PlaybackStateSerializer
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner

/**
 * #47. The saved queue (and equalizer preset) must survive "Clear cache", an interrupted write and the
 * upgrade from the old cacheDir location, and must never bring back an older state.
 */
@RunWith(RobolectricTestRunner::class)
class DurableStateFileTest {

    private lateinit var context: Context
    private val name = Constants.FILENAME_PLAYLIST_SER

    private val current get() = File(context.filesDir, name)
    private val legacy get() = File(context.cacheDir, name)

    private fun state(vararg ids: String, index: Int = 0, position: Int = 0) =
        PlaybackState(ids.map { Track(id = it, title = "Title $it") }, index, position, true, 2)

    private fun ids(state: PlaybackState?) = state?.songs?.map { it.id }

    private fun writeLegacy(state: Serializable, file: File = legacy) {
        file.parentFile!!.mkdirs()
        ObjectOutputStream(file.outputStream()).use { it.writeObject(state) }
    }

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        context = ApplicationProvider.getApplicationContext()
        current.delete()
        legacy.delete()
    }

    @After
    fun tearDown() {
        current.delete()
        legacy.delete()
        File(current.path + ".bak").delete()
    }

    private fun read() = FileUtil.deserialize<PlaybackState>(context, name)

    // ---- a fresh install ----

    @Test
    fun `a saved queue round trips with index, position, shuffle and repeat`() {
        assertTrue(FileUtil.serialize(context, state("a", "b", "c", index = 2, position = 4_200), name))

        val restored = read()!!
        assertEquals(listOf("a", "b", "c"), ids(restored))
        assertEquals(2, restored.currentPlayingIndex)
        assertEquals(4_200, restored.currentPlayingPosition)
        assertTrue(restored.shufflePlay)
        assertEquals(2, restored.repeatMode)
        assertFalse("nothing is written to the cache dir", legacy.exists())
    }

    @Test
    fun `a missing file restores nothing and does not fail`() {
        assertNull(read())
    }

    // ---- "Clear cache" / OS reclamation ----

    @Test
    fun `clearing the cache directory keeps the queue`() {
        FileUtil.serialize(context, state("a", "b"), name)

        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()

        assertEquals(listOf("a", "b"), ids(read()))
    }

    // ---- upgrade from the old location ----

    @Test
    fun `a valid queue from the old cache location is migrated and the old file removed`() {
        writeLegacy(state("old1", "old2", index = 1, position = 900))

        val first = read()!!
        assertEquals(listOf("old1", "old2"), ids(first))
        assertEquals(1, first.currentPlayingIndex)
        assertTrue("new file exists", current.exists())
        assertFalse("old file removed only after the new one verified", legacy.exists())

        // running it again changes nothing and loses nothing
        assertEquals(listOf("old1", "old2"), ids(read()))
        assertEquals(listOf("old1", "old2"), ids(read()))
    }

    @Test
    fun `the new state wins over a stale old file, which is then discarded`() {
        FileUtil.serialize(context, state("new"), name)
        writeLegacy(state("stale1", "stale2"))

        assertEquals(listOf("new"), ids(read()))
        assertFalse(legacy.exists())
        assertEquals(listOf("new"), ids(read()))
    }

    @Test
    fun `writing a new state also discards an unread old copy`() {
        writeLegacy(state("stale"))

        FileUtil.serialize(context, state("fresh"), name)

        assertFalse(legacy.exists())
        assertEquals(listOf("fresh"), ids(read()))
    }

    @Test
    fun `a corrupt old file is not migrated and does not crash`() {
        legacy.parentFile!!.mkdirs()
        legacy.writeText("not a queue")

        assertNull(read())
        assertFalse("no bogus new file is created", current.exists())
        assertTrue("the user's file is not deleted on failure", legacy.exists())
    }

    @Test
    fun `a failed migration keeps the old file and still restores from it`() {
        // the destination cannot be written: a regular file sits where its parent directory must be
        val sub = "nodir/state.ser"
        val oldCopy = File(context.cacheDir, sub)
        writeLegacy(state("old"), oldCopy)
        File(context.filesDir, "nodir").writeText("in the way")

        assertEquals(listOf("old"), ids(FileUtil.deserialize<PlaybackState>(context, sub)))
        assertTrue("old copy kept for the next attempt", oldCopy.exists())

        File(context.filesDir, "nodir").delete()
        assertEquals("the next run migrates", listOf("old"), ids(FileUtil.deserialize<PlaybackState>(context, sub)))
        assertFalse(oldCopy.exists())
        assertTrue(File(context.filesDir, sub).exists())

        File(context.filesDir, "nodir").deleteRecursively()
        File(context.cacheDir, "nodir").deleteRecursively()
    }

    @Test
    fun `a corrupt current file falls back to the old copy and then repairs itself`() {
        FileUtil.serialize(context, state("new"), name)
        current.writeText("garbage")
        writeLegacy(state("old"))

        assertEquals("the only readable state is used", listOf("old"), ids(read()))
        assertFalse(legacy.exists())
        assertEquals("and the current file is valid again", listOf("old"), ids(read()))
    }

    @Test
    fun `concurrent saves and restores never see a missing or partial state`() {
        FileUtil.serialize(context, state("seed"), name)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(5)
        try {
            val jobs = (1..3).map { writer ->
                pool.submit {
                    repeat(40) { n ->
                        if (!FileUtil.serialize(context, state("w$writer", "n$n", index = n % 2), name)) failures += "write failed"
                    }
                }
            } + (1..2).map {
                pool.submit {
                    repeat(80) {
                        val restored = read()
                        if (restored == null || restored.songs.isEmpty()) failures += "unreadable state"
                    }
                }
            }
            jobs.forEach { it.get(60, java.util.concurrent.TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertTrue(failures.toString(), failures.isEmpty())
        assertNotNull(read())
    }

    @Test
    fun `a migration interrupted after the new file was written leaves nothing to lose`() {
        // crash between "new written" and "old deleted" == both files present
        FileUtil.serialize(context, state("moved"), name)
        writeLegacy(state("moved"))

        assertEquals(listOf("moved"), ids(read()))
        assertFalse(legacy.exists())
    }

    // ---- interrupted writes ----

    @Test
    fun `an interrupted write keeps the last good state`() {
        FileUtil.serialize(context, state("good1", "good2", index = 1), name)

        // the process dies half way through the next write: started, partly written, never finished
        val atomic = AtomicFile(current)
        atomic.startWrite().apply { write(byteArrayOf(0x41, 0x42, 0x43)) }

        val restored = read()!!
        assertEquals(listOf("good1", "good2"), ids(restored))
        assertEquals(1, restored.currentPlayingIndex)
    }

    @Test
    fun `a failed write returns false and keeps the last good state`() {
        FileUtil.serialize(context, state("good"), name)

        assertFalse(FileUtil.serialize(context, NotSerializable(), name))

        assertEquals(listOf("good"), ids(read()))
    }

    @Test
    fun `a truncated or garbage current file fails safely`() {
        FileUtil.serialize(context, state("a", "b", "c"), name)
        val bytes = current.readBytes()
        current.writeBytes(bytes.copyOf(bytes.size / 2))
        assertNull(read())

        current.writeText("garbage")
        assertNull(read())
    }

    // ---- empty queue ----

    @Test
    fun `an empty queue restores as empty and does not bring back an older queue`() {
        writeLegacy(state("old1", "old2"))
        FileUtil.serialize(context, PlaybackState(), name)

        val restored = read()!!
        assertTrue(restored.songs.isEmpty())
        assertFalse(legacy.exists())
    }

    // ---- through the real serializer ----

    @Test
    fun `the playback serializer persists and restores through the durable file`() {
        startKoin { modules(module { single<Context> { context } }) }
        try {
            val serializer = PlaybackStateSerializer()
            serializer.serializeNow(
                listOf(Track(id = "t1", title = "One"), Track(id = "t2", title = "Two")),
                currentPlayingIndex = 1,
                currentPlayingPosition = 12_345,
                shufflePlay = false,
                repeatMode = 1
            )
            assertTrue(current.exists())
            assertFalse(legacy.exists())

            context.cacheDir.deleteRecursively()
            val restored = PlaybackStateSerializer().deserializeNow()!!

            assertEquals(listOf("t1", "t2"), ids(restored))
            assertEquals(1, restored.currentPlayingIndex)
            assertEquals(12_345, restored.currentPlayingPosition)
            assertEquals(1, restored.repeatMode)
        } finally {
            stopKoin()
        }
    }

    // ---- equalizer preset (same helper, own file) ----

    @Test
    fun `the equalizer preset file uses the same durable location and migration`() {
        val preset = EqPreset(shortArrayOf(100, -50, 0, 25, 300), 2, true)

        // a preset saved by the old version, in the cache dir
        writeLegacy(preset, File(context.cacheDir, "equalizer.dat"))
        val migrated = FileUtil.deserialize<EqPreset>(context, "equalizer.dat")!!
        assertTrue(File(context.filesDir, "equalizer.dat").exists())
        assertFalse(File(context.cacheDir, "equalizer.dat").exists())
        assertEquals(preset.levels.toList(), migrated.levels.toList())
        assertEquals(2, migrated.preset.toInt())
        assertTrue(migrated.enabled)

        // and a new save survives a cache wipe
        FileUtil.serialize(context, EqPreset(shortArrayOf(1, 2), 0, false), "equalizer.dat")
        context.cacheDir.deleteRecursively()
        assertEquals(listOf<Short>(1, 2), FileUtil.deserialize<EqPreset>(context, "equalizer.dat")!!.levels.toList())

        File(context.filesDir, "equalizer.dat").delete()
    }

    // Holds a field that cannot be serialized, so writing it fails half way through
    private class NotSerializable : Serializable {
        @Suppress("unused")
        val notSerializable = Any()

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    private class EqPreset(val levels: ShortArray, val preset: Short, val enabled: Boolean) : Serializable {
        constructor(levels: ShortArray, preset: Int, enabled: Boolean) : this(levels, preset.toShort(), enabled)

        companion object {
            private const val serialVersionUID = 1L
        }
    }
}
