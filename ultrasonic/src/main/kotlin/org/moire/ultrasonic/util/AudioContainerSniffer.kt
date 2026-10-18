/*
 * AudioContainerSniffer.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

/**
 * Identifies the container of a downloaded audio file from its first bytes, so the stored file name
 * describes the bytes that were actually received (issue #39). The server may transcode a download
 * (e.g. FLAC -> Ogg/Opus), so the original track suffix is not evidence of the downloaded format.
 */
object AudioContainerSniffer {
    /** Number of leading bytes [detectExtension] needs to look at. */
    const val HEADER_BYTES = 64

    private const val OGG_PAGE_HEADER_END = 28
    private const val FTYP_OFFSET = 4
    private const val RIFF_FORMAT_OFFSET = 8
    private const val SYNC_MASK = 0xE0
    private const val LAYER_MASK = 0x06

    /**
     * @return the file extension matching the container (`flac`, `opus`, `ogg`, `mp3`, `aac`,
     * `m4a`, `wav`), or null when the bytes are not recognised. Unknown data is never labelled.
     */
    fun detectExtension(header: ByteArray, length: Int = header.size): String? {
        val n = minOf(length, header.size)
        return when {
            startsWith(header, n, "fLaC") -> "flac"
            startsWith(header, n, "OggS") -> detectOgg(header, n)
            startsWith(header, n, "ID3") -> "mp3"
            at(header, n, FTYP_OFFSET, "ftyp") -> "m4a"
            startsWith(header, n, "RIFF") && at(header, n, RIFF_FORMAT_OFFSET, "WAVE") -> "wav"
            isMpegFrame(header, n) -> mpegExtension(header)
            else -> null
        }
    }

    // An Ogg container does not imply Opus: it may carry Vorbis, FLAC or other codecs.
    private fun detectOgg(header: ByteArray, n: Int): String {
        for (i in OGG_PAGE_HEADER_END until n) {
            if (at(header, n, i, "OpusHead")) return "opus"
        }
        return "ogg"
    }

    private fun isMpegFrame(header: ByteArray, n: Int): Boolean =
        n >= 2 && header[0] == 0xFF.toByte() && (header[1].toInt() and SYNC_MASK) == SYNC_MASK

    // Layer bits 00 mean ADTS (AAC); otherwise an MPEG audio frame (MP3).
    private fun mpegExtension(header: ByteArray): String =
        if ((header[1].toInt() and LAYER_MASK) == 0) "aac" else "mp3"

    private fun startsWith(b: ByteArray, n: Int, s: String) = at(b, n, 0, s)

    private fun at(b: ByteArray, n: Int, offset: Int, s: String): Boolean {
        if (offset + s.length > n) return false
        return s.indices.all { b[offset + it] == s[it].code.toByte() }
    }
}
