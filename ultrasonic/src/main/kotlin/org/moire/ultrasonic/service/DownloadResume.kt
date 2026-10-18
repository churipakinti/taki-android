/*
 * DownloadResume.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.io.InputStream

/**
 * A download response together with the headers needed to decide whether it may be appended to
 * an existing partial file (issue #40).
 */
class DownloadStream(
    val stream: InputStream?,
    val httpCode: Int,
    val contentType: String? = null,
    val contentRange: String? = null,
    val contentLength: Long = -1L,
    val etag: String? = null
) {
    val isPartial: Boolean get() = httpCode == HTTP_PARTIAL_CONTENT

    companion object {
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}

/** What a download request asks the server for; two partials are only related if these match. */
internal data class RequestIdentity(
    val trackId: String,
    val endpoint: String,
    val maxBitRate: Int
) {
    /**
     * Only an untransformed source file is a stable byte sequence a later request can continue.
     * A transcoded response is never assumed to be byte-identical across requests.
     */
    val isOriginal: Boolean get() = maxBitRate <= 0
}

/** The identity and response facts stored next to a partial file when its transfer started. */
internal data class ResumeRecord(
    val identity: RequestIdentity,
    val contentType: String?,
    val totalLength: Long,
    val etag: String?
) {
    fun serialize(): String = listOf(
        VERSION,
        identity.trackId,
        identity.endpoint,
        identity.maxBitRate.toString(),
        contentType.orEmpty(),
        totalLength.toString(),
        etag.orEmpty()
    ).joinToString("\n")

    companion object {
        private const val VERSION = "v1"
        private const val FIELDS = 7

        fun parse(text: String?): ResumeRecord? {
            val lines = text?.split('\n')?.takeIf { it.size == FIELDS && it[0] == VERSION }
                ?: return null
            val fields = lines.iterator().also { it.next() }
            val trackId = fields.next()
            val endpoint = fields.next()
            val bitRate = fields.next().toIntOrNull()
            val contentType = fields.next().ifEmpty { null }
            val total = fields.next().toLongOrNull()
            val etag = fields.next().ifEmpty { null }
            return if (bitRate == null || total == null) {
                null
            } else {
                ResumeRecord(RequestIdentity(trackId, endpoint, bitRate), contentType, total, etag)
            }
        }
    }
}

/**
 * Decides whether bytes may be appended to a partial file. When compatibility cannot be proven
 * the transfer restarts from zero: restarting wastes bandwidth, appending can corrupt audio.
 */
internal object DownloadResume {

    /** Whether a Range request is worth sending at all. */
    fun canAttemptResume(
        partialLength: Long,
        record: ResumeRecord?,
        requested: RequestIdentity
    ): Boolean = partialLength > 0 &&
        record != null &&
        requested.isOriginal &&
        record.identity == requested

    /** Whether [response] provably continues the partial file at [partialLength]. */
    fun isValidResume(
        partialLength: Long,
        record: ResumeRecord,
        response: DownloadStream
    ): Boolean {
        val range = parseContentRange(response.contentRange)
        return response.isPartial && range != null &&
            rangeContinues(partialLength, record, response, range) &&
            sameRepresentation(record, response)
    }

    private fun rangeContinues(
        partialLength: Long,
        record: ResumeRecord,
        response: DownloadStream,
        range: ByteRange
    ): Boolean {
        val totalMatches = record.totalLength < 0 || range.total == record.totalLength
        val lengthMatches = range.total < 0 || response.contentLength < 0 ||
            response.contentLength == range.total - range.start
        return range.start == partialLength && totalMatches && lengthMatches
    }

    private fun sameRepresentation(record: ResumeRecord, response: DownloadStream): Boolean {
        val typeMatches = record.contentType == null ||
            mediaType(response.contentType) == mediaType(record.contentType)
        return typeMatches && (record.etag == null || record.etag == response.etag)
    }

    /** The response to write and whether it continues the existing partial file. */
    class Outcome(val response: DownloadStream, val resumed: Boolean)

    /**
     * Issues the download request(s) for a partial file of [partialLength] bytes and returns the
     * response that may be written. [discardPartial] must remove the partial file and its record;
     * it is invoked whenever the existing bytes cannot be proven to belong to the new response.
     */
    fun open(
        partialLength: Long,
        record: ResumeRecord?,
        identity: RequestIdentity,
        discardPartial: () -> Unit,
        request: (offset: Long) -> DownloadStream
    ): Outcome {
        var offset = 0L
        if (partialLength > 0) {
            if (canAttemptResume(partialLength, record, identity)) {
                offset = partialLength
            } else {
                discardPartial()
            }
        }

        var response = request(offset)
        val resumed = offset > 0 && isValidResume(offset, record!!, response)
        val unusable = response.isPartial ||
            response.httpCode == DownloadStream.HTTP_RANGE_NOT_SATISFIABLE
        if (offset > 0 && !resumed && unusable) {
            // A 206 that does not continue our bytes, or a 416 (not proof of completeness):
            // never append, start over from zero.
            response.stream?.close()
            discardPartial()
            response = request(0)
        }
        return Outcome(response, resumed)
    }

    /** The facts to persist when a transfer starts from the beginning. */
    fun recordFor(identity: RequestIdentity, response: DownloadStream): ResumeRecord {
        val total = parseContentRange(response.contentRange)?.total
            ?: response.contentLength
        return ResumeRecord(identity, response.contentType, total, response.etag)
    }

    class ByteRange(val start: Long, val total: Long)

    /** Parses `bytes <start>-<end>/<total|*>`; null when malformed. Total is -1 when `*`. */
    fun parseContentRange(header: String?): ByteRange? {
        val match = CONTENT_RANGE.matchEntire(header?.trim().orEmpty())
        val start = match?.groupValues?.get(1)?.toLongOrNull()
        val end = match?.groupValues?.get(2)?.toLongOrNull()
        val totalText = match?.groupValues?.get(3)
        val total = if (totalText == "*") -1L else totalText?.toLongOrNull()
        val present = start != null && end != null && total != null
        val consistent = present && end!! >= start!! && (total!! < 0 || total > end)
        return if (consistent) ByteRange(start!!, total!!) else null
    }

    private val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)

    private fun mediaType(contentType: String?): String? =
        contentType?.substringBefore(';')?.trim()?.lowercase()
}
