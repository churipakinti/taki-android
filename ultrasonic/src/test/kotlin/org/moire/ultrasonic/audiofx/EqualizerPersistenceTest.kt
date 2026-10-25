/*
 * EqualizerPersistenceTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.audiofx

import android.content.Context
import android.media.audiofx.Equalizer
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.FileUtil
import org.robolectric.RobolectricTestRunner

/**
 * #47. The equalizer preset is stored by the real, private `EqualizerController.EqualizerSettings`
 * class; these tests use that class (through reflection and a mocked [Equalizer]) so the values
 * themselves, not a stand-in, are what must survive a cache wipe, a restart, an interrupted write and
 * the upgrade from the old cacheDir location.
 */
@RunWith(RobolectricTestRunner::class)
class EqualizerPersistenceTest {

    private lateinit var context: Context
    private val name = "equalizer.dat"
    private val current get() = File(context.filesDir, name)
    private val legacy get() = File(context.cacheDir, name)

    private val levels = shortArrayOf(300, -150, 0, 75, 1200)

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        context = ApplicationProvider.getApplicationContext()
        current.delete()
        legacy.delete()
        File(current.path + ".bak").delete()
    }

    @After
    fun tearDown() {
        current.delete()
        legacy.delete()
        File(current.path + ".bak").delete()
    }

    private fun equalizer(levels: ShortArray, preset: Short, enabled: Boolean): Equalizer = mock {
        on { numberOfBands } doReturn levels.size.toShort()
        on { numberOfPresets } doReturn 10.toShort()
        on { currentPreset } doReturn preset
        on { getEnabled() } doReturn enabled
        on { getBandLevel(any()) } doAnswer { levels[(it.arguments[0] as Short).toInt()] }
    }

    /** The preset exactly as `EqualizerController.saveSettings()` captures it from a live equalizer. */
    private fun capture(eq: Equalizer): Serializable {
        val type = Class.forName("org.moire.ultrasonic.audiofx.EqualizerController\$EqualizerSettings")
        val ctor = type.getDeclaredConstructor(Equalizer::class.java).apply { isAccessible = true }
        return ctor.newInstance(eq) as Serializable
    }

    private fun apply(settings: Any, eq: Equalizer) {
        settings.javaClass.getDeclaredMethod("apply", Equalizer::class.java)
            .apply { isAccessible = true }
            .invoke(settings, eq)
    }

    private fun assertRestored(settings: Any?, levels: ShortArray, preset: Short, enabled: Boolean) {
        assertNotNull("the preset must be readable", settings)
        val target = equalizer(ShortArray(levels.size), -1, false)
        apply(settings!!, target)
        levels.forEachIndexed { band, level -> verify(target).setBandLevel(band.toShort(), level) }
        verify(target).usePreset(preset)
        verify(target).setEnabled(enabled)
    }

    private fun read(): Any? = FileUtil.deserialize<Serializable>(context, name)

    @Test
    fun `the real preset keeps its band levels, preset and enabled flag through the durable file`() {
        FileUtil.serialize(context, capture(equalizer(levels, 3, true)), name)

        assertTrue(current.exists())
        assertFalse(legacy.exists())
        assertRestored(read(), levels, 3, true)
    }

    @Test
    fun `a preset saved by the old version in the cache is migrated with the same values`() {
        legacy.parentFile!!.mkdirs()
        ObjectOutputStream(legacy.outputStream()).use { it.writeObject(capture(equalizer(levels, 2, false))) }

        assertRestored(read(), levels, 2, false)
        assertTrue("migrated to filesDir", current.exists())
        assertFalse("old copy removed after the new one was verified", legacy.exists())

        // running it again changes nothing
        assertRestored(read(), levels, 2, false)
    }

    @Test
    fun `the preset survives clearing the cache and a restart`() {
        FileUtil.serialize(context, capture(equalizer(levels, 1, true)), name)

        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()

        // "restart": nothing is held in memory, the file is read again from disk
        assertRestored(read(), levels, 1, true)
    }

    @Test
    fun `an interrupted save keeps the last good preset`() {
        FileUtil.serialize(context, capture(equalizer(levels, 4, true)), name)

        // the next save dies half way: started, partly written, never finished
        AtomicFile(current).startWrite().apply { write(byteArrayOf(1, 2, 3, 4)) }

        assertRestored(read(), levels, 4, true)
    }

    @Test
    fun `a truncated or missing preset restores nothing and never touches the equalizer`() {
        assertNull(read())

        FileUtil.serialize(context, capture(equalizer(levels, 4, true)), name)
        val bytes = current.readBytes()
        current.writeBytes(bytes.copyOf(bytes.size / 2))
        assertNull(read())

        val target = equalizer(ShortArray(5), -1, false)
        verify(target, never()).usePreset(any())
    }

    @Test
    fun `the controller still persists through the shared helper and the audited file name`() {
        // EqualizerController must not grow its own file handling (or fall back to cacheDir) again
        val source = File("src/main/kotlin/org/moire/ultrasonic/audiofx/EqualizerController.kt").readText()
        assertTrue(source.contains("import org.moire.ultrasonic.util.FileUtil.serialize"))
        assertTrue(source.contains("import org.moire.ultrasonic.util.FileUtil.deserialize"))
        assertEquals("both save and load use the same file", 2, Regex("\"equalizer.dat\"").findAll(source).count())
        assertFalse(source.contains("cacheDir"))
        assertFalse(source.contains("FileOutputStream"))
    }
}
