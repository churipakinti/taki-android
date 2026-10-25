/*
 * PlaybackSourcePolicyTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.net.Uri
import android.os.Looper
import android.os.Process
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * #46. A playback item's source is Taki's internal "id|bitrate|path" string, and the data source opens
 * its `path` as a local file, so only Taki's own process may choose it. Any other controller, trusted
 * or not, is resolved by media id alone (the Android Auto path) and a `requestMetadata.mediaUri` it
 * supplies must never reach the player, through either `onSetMediaItems` or `onAddMediaItems`.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackSourcePolicyTest {

    private val lifecycleSupport: MediaPlayerLifecycleSupport = mock()
    private val session: MediaSession = mock()
    private val foreign: MediaSession.ControllerInfo = mock()
    private val foreignTrusted: MediaSession.ControllerInfo = mock()
    private val own: MediaSession.ControllerInfo = mock()
    private lateinit var callback: MediaLibrarySessionCallback
    private val resolvedIds = mutableListOf<String>()

    private val internalSource = "x|128|/data/data/some.app/files/private.wav"

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        startKoin {
            modules(
                module {
                    single { lifecycleSupport }
                    single { mock<MediaPlayerManager>() }
                    single { mock<ActiveServerProvider>() }
                }
            )
        }
        doAnswer { it.getArgument<Runnable?>(0)?.run() }
            .whenever(lifecycleSupport).ensureCreatedForPlayback(anyOrNull())
        whenever(foreign.uid).thenReturn(Process.myUid() + 1)
        whenever(foreign.isTrusted).thenReturn(false)
        whenever(foreignTrusted.uid).thenReturn(Process.myUid() + 2)
        whenever(foreignTrusted.isTrusted).thenReturn(true)
        whenever(own.uid).thenReturn(Process.myUid())
        whenever(own.isTrusted).thenReturn(true)

        callback = MediaLibrarySessionCallback()
        callback.activeServerId = { 1 }
        callback.mediaIdResolver = { id ->
            resolvedIds += id
            if (id == "MEDIA_SEARCH_SONG_ITEM|S1") listOf(track("S1")) else emptyList()
        }
    }

    @After
    fun tearDown() {
        callback.release()
        stopKoin()
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    private fun track(id: String): Track {
        MediaItemConverter.addToCache(id, buildMediaItem(title = "Title $id", mediaId = id, isPlayable = true))
        return Track(id = id, title = "Title $id")
    }

    private fun item(mediaId: String, uri: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().apply { if (uri != null) setMediaUri(Uri.parse(uri)) }.build()
            )
            .build()

    private fun set(controller: MediaSession.ControllerInfo, vararg items: MediaItem) =
        callback.onSetMediaItems(session, controller, items.toMutableList(), C.INDEX_UNSET, C.TIME_UNSET)
            .also { shadowOf(Looper.getMainLooper()).idle() }

    private fun add(controller: MediaSession.ControllerInfo, vararg items: MediaItem) =
        callback.onAddMediaItems(session, controller, items.toMutableList())
            .also { shadowOf(Looper.getMainLooper()).idle() }

    private fun ListenableFuture<*>.failed(): Boolean =
        try {
            get()
            false
        } catch (_: ExecutionException) {
            true
        }

    private fun sources(items: List<MediaItem>) = items.map { it.localConfiguration?.uri?.toString() }

    private val valid = "MEDIA_SEARCH_SONG_ITEM|S1"

    // ---- Taki's own controller keeps the internal source ----

    @Test
    fun `Taki's own controller keeps supplying the internal source`() {
        val result = set(own, item("S1", internalSource)).get()

        assertEquals(listOf(internalSource), sources(result.mediaItems))
        assertTrue("an own item is not resolved against the server", resolvedIds.isEmpty())
    }

    @Test
    fun `Taki's own controller can append an internal source too`() {
        assertEquals(listOf(internalSource), sources(add(own, item("S1", internalSource)).get()))
    }

    // ---- everyone else is resolved by media id ----

    @Test
    fun `a foreign controller's source uri is ignored and its media id is resolved instead`() {
        for (controller in listOf(foreign, foreignTrusted)) {
            resolvedIds.clear()
            val result = set(controller, item(valid, internalSource)).get()

            assertEquals(listOf(valid), resolvedIds)
            assertEquals(listOf("S1"), result.mediaItems.map { it.mediaId })
            assertEquals(listOf<String?>(null), sources(result.mediaItems))
        }
    }

    @Test
    fun `the same holds for addMediaItems`() {
        val result = add(foreign, item(valid, internalSource)).get()

        assertEquals(listOf(valid), resolvedIds)
        assertEquals(listOf<String?>(null), sources(result))
    }

    @Test
    fun `a foreign controller with only a source uri gets nothing played`() {
        assertTrue(set(foreign, item("unknown-id", internalSource)).failed())
        assertTrue(add(foreign, item("unknown-id", internalSource)).failed())
        assertEquals("the uri is never interpreted as an id", listOf("unknown-id", "unknown-id"), resolvedIds)
    }

    @Test
    fun `no uri scheme or shape from a foreign controller ever reaches the queue`() {
        val uris = listOf(
            "http://127.0.0.1:8099/a.wav",
            "https://example.test/a.wav",
            "file:///data/local/tmp/fx.wav",
            "content://some.provider/fx",
            "ftp://127.0.0.1/a.wav",
            "http://[bad",
            "x|128|/nonexistent/none.mp3",
            "x|abc|/n",
            "http://127.0.0.1:8099/" + "a".repeat(200_000),
            ""
        )
        for (uri in uris) {
            val viaSet = set(foreign, item(valid, uri)).get().mediaItems
            val viaAdd = add(foreign, item(valid, uri)).get()
            assertEquals("set, uri ${uri.take(40)}", listOf<String?>(null), sources(viaSet))
            assertEquals("add, uri ${uri.take(40)}", listOf<String?>(null), sources(viaAdd))
        }
    }

    @Test
    fun `a mixed queue from a foreign controller never carries a supplied source`() {
        val result = set(foreign, item(valid, null), item("evil", internalSource), item("evil2", "file:///x")).get()

        assertTrue(sources(result.mediaItems).all { it == null })
        assertFalse(result.mediaItems.any { it.mediaId.startsWith("evil") })
    }

    @Test
    fun `rejecting or resolving a foreign request never touches the session player`() {
        set(foreign, item(valid, internalSource))
        set(foreign, item("unknown", internalSource)).failed()

        verifyNoInteractions(session)
    }

    @Test
    fun `an item without a source uri is unchanged for every controller (Android Auto path)`() {
        for (controller in listOf(own, foreign, foreignTrusted)) {
            val result = set(controller, item(valid, null)).get()
            assertEquals(listOf("S1"), result.mediaItems.map { it.mediaId })
            assertNull(result.mediaItems.single().localConfiguration)
        }
    }
}
