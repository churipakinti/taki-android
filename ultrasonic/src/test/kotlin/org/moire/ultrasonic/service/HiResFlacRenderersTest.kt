/*
 * HiResFlacRenderersTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

/** Issue #17: only hi-res FLAC is routed to the software decoder; everything else is untouched. */
@RunWith(RobolectricTestRunner::class)
class HiResFlacRenderersTest {

    private fun format(mime: String, rate: Int) =
        Format.Builder().setSampleMimeType(mime).setSampleRate(rate).build()

    @Test
    fun `hi-res flac rates need the software decoder`() {
        for (rate in listOf(88_200, 96_000, 176_400, 192_000)) {
            assertTrue("rate $rate", requiresSoftwareFlacDecoder(format(MimeTypes.AUDIO_FLAC, rate)))
        }
    }

    @Test
    fun `ordinary flac rates keep the platform decoder`() {
        for (rate in listOf(8_000, 44_100, 48_000)) {
            assertFalse("rate $rate", requiresSoftwareFlacDecoder(format(MimeTypes.AUDIO_FLAC, rate)))
        }
    }

    @Test
    fun `other codecs never use the software decoder even at hi-res rates`() {
        for (mime in listOf(MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_WAV)) {
            assertFalse(mime, requiresSoftwareFlacDecoder(format(mime, 192_000)))
        }
        assertFalse(requiresSoftwareFlacDecoder(null, 192_000))
    }

    @Test
    fun `the threshold sits just above 48 kHz and covers 88_2 kHz`() {
        assertEquals(88_200, HIRES_FLAC_MIN_SAMPLE_RATE_HZ)
        assertTrue(HIRES_FLAC_MIN_SAMPLE_RATE_HZ > 48_000)
    }

    private fun gated(delegateSupport: Int): Pair<HiResFlacOnlyRenderer, RendererCapabilities> {
        val capabilities = mock<RendererCapabilities> {
            on { supportsFormat(any()) } doReturn delegateSupport
            on { name } doReturn "FfmpegAudioRenderer"
            on { trackType } doReturn C.TRACK_TYPE_AUDIO
        }
        val delegate = mock<Renderer> { on { this.capabilities } doReturn capabilities }
        return HiResFlacOnlyRenderer(delegate) to capabilities
    }

    @Test
    fun `the gated renderer claims hi-res flac with the delegate's own support level`() {
        val support = RendererCapabilities.create(C.FORMAT_HANDLED)
        val (renderer, _) = gated(support)
        assertEquals(support, renderer.capabilities.supportsFormat(format(MimeTypes.AUDIO_FLAC, 192_000)))
    }

    @Test
    fun `the gated renderer refuses everything else and never asks the delegate`() {
        val (renderer, capabilities) = gated(RendererCapabilities.create(C.FORMAT_HANDLED))
        val unsupported = RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        assertEquals(unsupported, renderer.capabilities.supportsFormat(format(MimeTypes.AUDIO_FLAC, 44_100)))
        assertEquals(unsupported, renderer.capabilities.supportsFormat(format(MimeTypes.AUDIO_MPEG, 44_100)))
        assertEquals(unsupported, renderer.capabilities.supportsFormat(format(MimeTypes.AUDIO_OPUS, 48_000)))
        verify(capabilities, never()).supportsFormat(any())
    }

    @Test
    fun `the gated renderer keeps the delegate's name and track type`() {
        val (renderer, _) = gated(RendererCapabilities.create(C.FORMAT_HANDLED))
        assertEquals("FfmpegAudioRenderer", renderer.capabilities.name)
        assertEquals(C.TRACK_TYPE_AUDIO, renderer.capabilities.trackType)
    }

    @Test
    fun `when the native library cannot load the platform audio renderer is still there`() {
        // The JVM cannot load the FFmpeg .so, which is exactly the "library unavailable" case:
        // playback must behave as before, with no software renderer and no crash.
        val renderers = TakiRenderersFactory(ApplicationProvider.getApplicationContext())
            .createRenderers(
                Handler(Looper.getMainLooper()),
                mock<VideoRendererEventListener>(),
                mock<AudioRendererEventListener>(),
                mock<TextOutput>(),
                mock<MetadataOutput>()
            )
        assertTrue(renderers.any { it is MediaCodecAudioRenderer })
        assertFalse(renderers.any { it is HiResFlacOnlyRenderer })
    }
}
