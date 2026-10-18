/*
 * DownloadResumeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #40: bytes may only be appended to a partial file that provably belongs to the same
 * representation. A tiny in-memory server and partial file stand in for Navidrome and Storage so
 * every scenario can assert the FINAL BYTES, not just a file name: a file that is an Opus prefix
 * followed by FLAC bytes fails these tests even though it has a valid-looking header.
 */
class DownloadResumeTest {

    private fun pattern(seed: Int, size: Int) =
        ByteArray(size) { ((it * 31 + seed) and 0xFF).toByte() }

    private val flac = "fLaC".toByteArray() + pattern(7, 996)
    private val opus = "OggS".toByteArray() + pattern(99, 596)

    private val original = RequestIdentity("t1", "download", 0)
    private val transcoded320 = RequestIdentity("t1", "stream", 320)

    /** What the server returns for a request; `rangeable` models Accept-Ranges: bytes. */
    private class Rep(
        val bytes: ByteArray,
        val contentType: String,
        val rangeable: Boolean,
        val etag: String? = null
    )

    private class Server(val reps: Map<RequestIdentity, Rep>, var identity: RequestIdentity) {
        val offsets = mutableListOf<Long>()

        fun request(offset: Long): DownloadStream {
            offsets += offset
            val rep = reps.getValue(identity)
            return if (offset > 0 && rep.rangeable) {
                if (offset >= rep.bytes.size) return DownloadStream(null, 416)
                val body = rep.bytes.copyOfRange(offset.toInt(), rep.bytes.size)
                DownloadStream(
                    ByteArrayInputStream(body),
                    206,
                    rep.contentType,
                    "bytes $offset-${rep.bytes.size - 1}/${rep.bytes.size}",
                    body.size.toLong(),
                    rep.etag
                )
            } else {
                DownloadStream(
                    ByteArrayInputStream(rep.bytes),
                    200,
                    rep.contentType,
                    null,
                    if (rep.rangeable) rep.bytes.size.toLong() else -1L,
                    rep.etag
                )
            }
        }
    }

    /** The partial file + resume record, written the way DownloadTask writes them. */
    private class Partial(var bytes: ByteArray = ByteArray(0), var record: ResumeRecord? = null) {
        var discards = 0
        fun discard() {
            bytes = ByteArray(0)
            record = null
            discards++
        }
    }

    /** Runs one transfer attempt; [limit] simulates the connection dropping after that many bytes. */
    private fun transfer(
        partial: Partial,
        id: RequestIdentity,
        server: Server,
        limit: Int = Int.MAX_VALUE
    ) {
        server.identity = id
        val outcome = DownloadResume.open(
            partial.bytes.size.toLong(),
            partial.record,
            id,
            partial::discard,
            server::request
        )
        val r = outcome.response
        if (!outcome.resumed) partial.record = DownloadResume.recordFor(id, r)
        val body = r.stream!!.readBytes().let { if (it.size > limit) it.copyOf(limit) else it }
        partial.bytes = if (r.isPartial) partial.bytes + body else body
    }

    private fun server(id: RequestIdentity = original, etag: String? = null) = Server(
        mapOf(
            original to Rep(flac, "audio/flac", true, etag),
            transcoded320 to Rep(opus, "audio/ogg", false)
        ),
        id
    )

    @Test
    fun `same representation resumes and the file equals the full original`() {
        val s = server()
        val p = Partial()
        transfer(p, original, s, limit = 400)
        assertEquals(400, p.bytes.size)
        transfer(p, original, s)
        assertArrayEquals(flac, p.bytes)
        assertEquals("the second request continued at the partial length", listOf(0L, 400L), s.offsets)
        assertEquals(0, p.discards)
    }

    @Test
    fun `transcoded partial is never extended with original bytes - the reproduced corruption`() {
        val s = server()
        val p = Partial()
        transfer(p, transcoded320, s, limit = 300)
        assertEquals(300, p.bytes.size)
        // The user switches to original quality; the server would happily answer a Range with 206.
        transfer(p, original, s)
        assertArrayEquals("must be exactly the FLAC, not an Opus prefix + FLAC tail", flac, p.bytes)
        assertEquals("no Range was attempted against the foreign partial", listOf(0L, 0L), s.offsets)
        assertEquals(1, p.discards)
    }

    @Test
    fun `original partial followed by a transcode request restarts and ends as the opus`() {
        val s = server()
        val p = Partial()
        transfer(p, original, s, limit = 500)
        transfer(p, transcoded320, s)
        assertArrayEquals(opus, p.bytes)
        assertEquals(1, p.discards)
    }

    @Test
    fun `a transcoded transfer is never resumed even with an identical request and a 206 server`() {
        val s = Server(
            mapOf(
                original to Rep(flac, "audio/flac", true),
                transcoded320 to Rep(opus, "audio/ogg", true)
            ),
            transcoded320
        )
        val p = Partial()
        transfer(p, transcoded320, s, limit = 200)
        transfer(p, transcoded320, s)
        assertArrayEquals(opus, p.bytes)
        assertEquals(listOf(0L, 0L), s.offsets)
    }

    @Test
    fun `a legacy partial without a record is restarted`() {
        val s = server()
        val p = Partial(bytes = flac.copyOf(300), record = null)
        transfer(p, original, s)
        assertArrayEquals(flac, p.bytes)
        assertEquals(listOf(0L), s.offsets)
        assertEquals(1, p.discards)
    }

    @Test
    fun `a 200 answer to a Range request replaces the partial instead of appending`() {
        val s = Server(mapOf(original to Rep(flac, "audio/flac", false)), original)
        val p = Partial(
            bytes = flac.copyOf(300),
            record = ResumeRecord(original, "audio/flac", flac.size.toLong(), null)
        )
        transfer(p, original, s)
        assertArrayEquals(flac, p.bytes)
        assertEquals(listOf(300L), s.offsets)
    }

    private fun resumeResponse(
        start: Long = 300,
        total: Long = 1000,
        type: String? = "audio/flac",
        etag: String? = null,
        length: Long = total - start
    ) = DownloadStream(
        ByteArrayInputStream(ByteArray(0)),
        206,
        type,
        "bytes $start-${total - 1}/$total",
        length,
        etag
    )

    private val record = ResumeRecord(original, "audio/flac", 1000, "\"abc\"")

    @Test
    fun `a valid 206 with matching validators is accepted`() {
        assertTrue(DownloadResume.isValidResume(300, record, resumeResponse(etag = "\"abc\"")))
    }

    @Test
    fun `missing validators on both sides still resume when range and length agree`() {
        val noValidators = ResumeRecord(original, null, -1, null)
        assertTrue(DownloadResume.isValidResume(300, noValidators, resumeResponse(type = null)))
    }

    @Test
    fun `wrong start, total, length, etag or type are all rejected`() {
        val tag = "\"abc\""
        assertFalse(DownloadResume.isValidResume(300, record, resumeResponse(start = 0, etag = tag)))
        assertFalse(DownloadResume.isValidResume(300, record, resumeResponse(total = 1200, etag = tag)))
        assertFalse(DownloadResume.isValidResume(300, record, resumeResponse(length = 5, etag = tag)))
        assertFalse(DownloadResume.isValidResume(300, record, resumeResponse(etag = "\"changed\"")))
        assertFalse(DownloadResume.isValidResume(300, record, resumeResponse(etag = null)))
        assertFalse(DownloadResume.isValidResume(300, record, resumeResponse(type = null, etag = tag)))
        assertFalse(DownloadResume.isValidResume(300, record, resumeResponse(type = "audio/ogg", etag = tag)))
        assertFalse(DownloadResume.isValidResume(300, record, DownloadStream(null, 200, "audio/flac")))
    }

    @Test
    fun `content type parameters are ignored when comparing`() {
        assertTrue(
            DownloadResume.isValidResume(
                300,
                record,
                resumeResponse(type = "Audio/FLAC; charset=x", etag = "\"abc\"")
            )
        )
    }

    @Test
    fun `a server whose representation changed under the same request restarts`() {
        val s = Server(mapOf(original to Rep(flac, "audio/flac", true, "\"v2\"")), original)
        val p = Partial(
            bytes = flac.copyOf(300),
            record = ResumeRecord(original, "audio/flac", 1000, "\"v1\"")
        )
        transfer(p, original, s)
        assertArrayEquals(flac, p.bytes)
        assertEquals("tried the range, then restarted from zero", listOf(300L, 0L), s.offsets)
        assertEquals(1, p.discards)
    }

    @Test
    fun `a 416 is not treated as completion and restarts`() {
        val s = server()
        val p = Partial(
            bytes = flac + byteArrayOf(1, 2, 3),
            record = ResumeRecord(original, "audio/flac", flac.size.toLong(), null)
        )
        transfer(p, original, s)
        assertArrayEquals(flac, p.bytes)
        assertEquals(listOf(flac.size + 3L, 0L), s.offsets)
    }

    @Test
    fun `an interrupted restart leaves a resumable partial and the next attempt completes it`() {
        val s = server()
        val p = Partial()
        transfer(p, transcoded320, s, limit = 300)
        transfer(p, original, s, limit = 250)
        assertEquals(250, p.bytes.size)
        assertEquals(original, p.record!!.identity)
        transfer(p, original, s)
        assertArrayEquals(flac, p.bytes)
        assertEquals(listOf(0L, 0L, 250L), s.offsets)
    }

    @Test
    fun `resume is only attempted for an original with a matching record`() {
        val rec = ResumeRecord(original, "audio/flac", 1000, null)
        assertTrue(DownloadResume.canAttemptResume(10, rec, original))
        assertFalse(DownloadResume.canAttemptResume(0, rec, original))
        assertFalse(DownloadResume.canAttemptResume(10, null, original))
        assertFalse(DownloadResume.canAttemptResume(10, rec, original.copy(trackId = "other")))
        assertFalse(DownloadResume.canAttemptResume(10, rec, original.copy(endpoint = "stream")))
        assertFalse(DownloadResume.canAttemptResume(10, rec, transcoded320))
        val tRec = ResumeRecord(transcoded320, "audio/ogg", 600, null)
        assertFalse(DownloadResume.canAttemptResume(10, tRec, transcoded320))
    }

    @Test
    fun `resume records round-trip and malformed ones are rejected`() {
        val rec = ResumeRecord(original, "audio/flac", 1000, "\"abc\"")
        assertEquals(rec, ResumeRecord.parse(rec.serialize()))
        val bare = ResumeRecord(transcoded320, null, -1, null)
        assertEquals(bare, ResumeRecord.parse(bare.serialize()))
        assertNull(ResumeRecord.parse(null))
        assertNull(ResumeRecord.parse(""))
        assertNull(ResumeRecord.parse("v2\na\nb\n0\n\n1\n"))
        assertNull(ResumeRecord.parse("v1\na\nb\nx\n\n1\n"))
    }

    @Test
    fun `content range parsing`() {
        val r = DownloadResume.parseContentRange("bytes 300-999/1000")!!
        assertEquals(300, r.start)
        assertEquals(1000, r.total)
        assertEquals(-1, DownloadResume.parseContentRange("bytes 0-9/*")!!.total)
        assertNull(DownloadResume.parseContentRange(null))
        assertNull(DownloadResume.parseContentRange("bytes */1000"))
        assertNull(DownloadResume.parseContentRange("bytes 9-3/10"))
        assertNull(DownloadResume.parseContentRange("bytes 0-20/10"))
        assertNull(DownloadResume.parseContentRange("garbage"))
    }

    @Test
    fun `a fresh record takes its total from Content-Range or Content-Length`() {
        assertEquals(1000, DownloadResume.recordFor(original, resumeResponse()).totalLength)
        assertEquals(
            600,
            DownloadResume.recordFor(original, DownloadStream(null, 200, "audio/ogg", null, 600)).totalLength
        )
        assertEquals(-1, DownloadResume.recordFor(transcoded320, DownloadStream(null, 200)).totalLength)
    }
}
