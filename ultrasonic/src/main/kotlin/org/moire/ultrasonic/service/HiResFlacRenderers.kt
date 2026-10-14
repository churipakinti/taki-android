/*
 * HiResFlacRenderers.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import timber.log.Timber

// Issue #17. Android's platform FLAC decoder (c2.android.flac.decoder) silently stops emitting
// samples on some 24-bit/192 kHz files (observed: a frame at 2:34 of a 24/192 file, no
// onPlayerError, ever). Media3's DefaultRenderersFactory would pick it for every FLAC file, so
// hi-res FLAC is routed to an FFmpeg software decoder instead. Everything else - MP3, AAC, Opus
// and ordinary 44.1/48 kHz FLAC - keeps the platform decoder (and its hardware offload path).

/** FLAC at or above this sample rate (88.2 kHz, i.e. 88.2/96/176.4/192) uses the software decoder. */
internal const val HIRES_FLAC_MIN_SAMPLE_RATE_HZ = 88_200

/** True for the files the platform FLAC decoder is not trusted with. */
internal fun requiresSoftwareFlacDecoder(mimeType: String?, sampleRateHz: Int): Boolean =
    mimeType == MimeTypes.AUDIO_FLAC && sampleRateHz >= HIRES_FLAC_MIN_SAMPLE_RATE_HZ

@UnstableApi
internal fun requiresSoftwareFlacDecoder(format: Format): Boolean =
    requiresSoftwareFlacDecoder(format.sampleMimeType, format.sampleRate)

/**
 * Wraps the FFmpeg audio renderer so it claims only hi-res FLAC (see
 * [requiresSoftwareFlacDecoder]); every other format is reported unsupported and falls through to
 * the platform [MediaCodecAudioRenderer] exactly as before.
 */
@UnstableApi
internal class HiResFlacOnlyRenderer(delegate: Renderer) : ForwardingRenderer(delegate) {
    private val gatedCapabilities = object : RendererCapabilities by delegate.capabilities {
        override fun supportsFormat(format: Format): Int =
            if (requiresSoftwareFlacDecoder(format)) {
                delegate.capabilities.supportsFormat(format)
            } else {
                RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
            }
    }

    override fun getCapabilities(): RendererCapabilities = gatedCapabilities
}

/**
 * The player's renderers: Media3's defaults plus, ahead of the platform audio renderer, the
 * hi-res-FLAC-only FFmpeg renderer. If the native library cannot load, nothing is added and
 * playback behaves exactly as it did before this class existed.
 */
@UnstableApi
internal class TakiRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        super.buildAudioRenderers(
            context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
            audioSink, eventHandler, eventListener, out
        )
        val softwareAvailable = runCatching { FfmpegLibrary.isAvailable() }.getOrDefault(false)
        if (!softwareAvailable) {
            Timber.w("FFmpeg library unavailable; hi-res FLAC will use the platform decoder")
            return
        }
        val renderer = HiResFlacOnlyRenderer(
            FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
        )
        // Renderer order is preference order: ahead of the platform audio renderer.
        val platformIndex = out.indexOfFirst { it is MediaCodecAudioRenderer }
        out.add(if (platformIndex >= 0) platformIndex else out.size, renderer)
    }
}
