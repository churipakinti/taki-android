/*
 * AudioContainerSnifferTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #39: a downloaded file is named after its real container, not the original suffix. */
class AudioContainerSnifferTest {

    private fun bytes(vararg parts: Any): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (p in parts) {
            when (p) {
                is String -> out.write(p.toByteArray(Charsets.ISO_8859_1))
                is Int -> out.write(p)
                is ByteArray -> out.write(p)
            }
        }
        return out.toByteArray().copyOf(AudioContainerSniffer.HEADER_BYTES)
    }

    private fun ogg(payload: String) = bytes("OggS", ByteArray(24), payload)

    @Test
    fun `flac stream marker is flac`() {
        assertEquals("flac", AudioContainerSniffer.detectExtension(bytes("fLaC", 0, 0, 0, 0x22)))
    }

    @Test
    fun `ogg carrying opus is opus`() {
        assertEquals("opus", AudioContainerSniffer.detectExtension(ogg("OpusHead")))
    }

    @Test
    fun `ogg carrying vorbis is ogg not opus`() {
        assertEquals("ogg", AudioContainerSniffer.detectExtension(ogg("\u0001vorbis")))
    }

    @Test
    fun `ogg carrying flac is ogg`() {
        assertEquals("ogg", AudioContainerSniffer.detectExtension(ogg("\u007fFLAC")))
    }

    @Test
    fun `id3 tag and mpeg layer 3 frame are mp3`() {
        assertEquals("mp3", AudioContainerSniffer.detectExtension(bytes("ID3", 4, 0)))
        assertEquals("mp3", AudioContainerSniffer.detectExtension(bytes(0xFF, 0xFB, 0x90, 0x64)))
    }

    @Test
    fun `adts frame is aac`() {
        assertEquals("aac", AudioContainerSniffer.detectExtension(bytes(0xFF, 0xF1, 0x50, 0x80)))
    }

    @Test
    fun `mp4 ftyp box is m4a and riff wave is wav`() {
        assertEquals("m4a", AudioContainerSniffer.detectExtension(bytes(0, 0, 0, 0x20, "ftypM4A ")))
        assertEquals("wav", AudioContainerSniffer.detectExtension(bytes("RIFF", 0, 0, 0, 0, "WAVE")))
    }

    @Test
    fun `unknown or error payloads are never labelled`() {
        assertNull(AudioContainerSniffer.detectExtension(bytes("{\"subsonic-response\":")))
        assertNull(AudioContainerSniffer.detectExtension(ByteArray(0)))
        assertNull(AudioContainerSniffer.detectExtension(ByteArray(64)))
    }

    @Test
    fun `only the bytes actually read are considered`() {
        val header = ogg("OpusHead")
        assertEquals("ogg", AudioContainerSniffer.detectExtension(header, 28))
    }

    @Test
    fun `extension helpers keep the stem and swap the suffix`() {
        assertEquals("/m/A/01-X.opus", FileUtil.withExtension("/m/A/01-X.flac", "opus"))
        assertEquals("/m/A/01-X.complete.opus", FileUtil.withExtension("/m/A/01-X.complete.flac", "opus"))
        val variants = FileUtil.fileVariants("/m/A/01-X.flac")
        assertEquals("/m/A/01-X.flac", variants.first())
        assertEquals(listOf("/m/A/01-X.opus", "/m/A/01-X.ogg", "/m/A/01-X.mp3", "/m/A/01-X.m4a"), variants.drop(1))
        assertEquals(false, FileUtil.fileVariants("/m/A/01-X.opus").drop(1).contains("/m/A/01-X.opus"))
    }
}
