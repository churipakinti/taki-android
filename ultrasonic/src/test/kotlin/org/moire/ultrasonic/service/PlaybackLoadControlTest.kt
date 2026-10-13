/*
 * PlaybackLoadControlTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Issue #35: Taki's scheme-less "id|bitrate|path" media-item URIs are classified by Media3 as
 * local playback, so [createPlaybackLoadControl] configures the thresholds that actually apply.
 * These tests drive the real [DefaultLoadControl] decision logic with each URI shape.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackLoadControlTest {

    private val periodUid = Any()

    private val streamedUri = "4f2a|320|/storage/music/Artist/Album/01-Track.mp3"
    private val downloadedUri = "file:///storage/music/Artist/Album/01-Track.flac"
    private val httpUri = "https://example.invalid/rest/stream"

    private fun timelineFor(uri: String) = object : Timeline() {
        private val item = MediaItem.Builder().setUri(Uri.parse(uri)).build()
        override fun getWindowCount() = 1
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long) =
            window.set(
                Window.SINGLE_WINDOW_UID, item, null, 0L, 0L, 0L, true, false, null, 0L, 0L, 0, 0, 0L
            )
        override fun getPeriodCount() = 1
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean) =
            period.set(periodUid, periodUid, 0, 0L, 0L)
        override fun getIndexOfPeriod(uid: Any) = if (uid == periodUid) 0 else C_INDEX_UNSET
        override fun getUidOfPeriod(periodIndex: Int): Any = periodUid
    }

    private fun params(uri: String, bufferedSeconds: Long, rebuffering: Boolean = false) =
        LoadControl.Parameters(
            PlayerId.UNSET,
            timelineFor(uri),
            MediaSource.MediaPeriodId(periodUid),
            0L,
            bufferedSeconds * 1_000_000L,
            1f,
            true,
            rebuffering,
            androidx.media3.common.C.TIME_UNSET,
            androidx.media3.common.C.TIME_UNSET
        )

    @Test
    fun `premise - default Media3 control stops loading a scheme-less item at 20s ahead`() {
        // Documents the defect: with stock thresholds the 20 s mark is not topped up, the loader
        // sits idle until the buffer is nearly drained.
        prepared(DefaultLoadControl()).shouldContinueLoading(params(streamedUri, 20)) shouldBe false
    }

    @Test
    fun `streamed scheme-less item keeps loading while below the minimum`() {
        val control = prepared(createPlaybackLoadControl())
        for (seconds in listOf(1L, 5L, 20L, 29L)) {
            assertTrue("should load at ${seconds}s", control.shouldContinueLoading(params(streamedUri, seconds)))
        }
    }

    @Test
    fun `streamed item stops loading once the maximum is reached`() {
        val control = prepared(createPlaybackLoadControl())
        assertFalse(control.shouldContinueLoading(params(streamedUri, 51)))
    }

    @Test
    fun `downloaded file URI gets the same bounded policy`() {
        val control = prepared(createPlaybackLoadControl())
        assertTrue(control.shouldContinueLoading(params(downloadedUri, 20)))
        assertFalse(control.shouldContinueLoading(params(downloadedUri, 51)))
    }

    @Test
    fun `remote http URI keeps the default streaming policy`() {
        // Not used by Taki today, but must not have been altered by the local override.
        val control = prepared(createPlaybackLoadControl())
        assertTrue(control.shouldContinueLoading(params(httpUri, 20)))
        assertFalse(control.shouldContinueLoading(params(httpUri, 51)))
    }

    @Test
    fun `playback still starts after about one second of audio`() {
        val control = prepared(createPlaybackLoadControl())
        assertTrue(control.shouldStartPlayback(params(streamedUri, 2)))
        assertFalse(control.shouldStartPlayback(params(streamedUri, 0)))
    }

    @Test
    fun `rebuffer recovery resumes after about one second of audio`() {
        val control = prepared(createPlaybackLoadControl())
        assertTrue(control.shouldStartPlayback(params(streamedUri, 2, rebuffering = true)))
        assertFalse(control.shouldStartPlayback(params(streamedUri, 0, rebuffering = true)))
    }

    @Test
    fun `thresholds are ordered and bounded`() {
        assertTrue(PLAYBACK_START_BUFFER_MS <= PLAYBACK_MIN_BUFFER_MS)
        assertTrue(PLAYBACK_REBUFFER_RESUME_MS <= PLAYBACK_MIN_BUFFER_MS)
        assertTrue(PLAYBACK_MIN_BUFFER_MS <= PLAYBACK_MAX_BUFFER_MS)
        // The maximum is the unchanged Media3 default: this change must not grow memory use.
        assertEquals(DefaultLoadControl.DEFAULT_MAX_BUFFER_FOR_LOCAL_PLAYBACK_MS, PLAYBACK_MAX_BUFFER_MS)
        assertEquals(DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_FOR_LOCAL_PLAYBACK_MS, PLAYBACK_START_BUFFER_MS)
    }

    // DefaultLoadControl tracks per-player loading state; the player registers via onPrepared.
    private fun prepared(control: DefaultLoadControl) = control.also { it.onPrepared(PlayerId.UNSET) }

    private infix fun Boolean.shouldBe(expected: Boolean) = assertEquals(expected, this)

    private companion object {
        const val C_INDEX_UNSET = -1
    }
}
