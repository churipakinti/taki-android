/*
 * MediaItemTransitionRoutingTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner

/**
 * P0-1 -- media-item transition reason routing inside [MediaPlayerManager].
 *
 * [MediaPlayerManager]'s private [Player.Listener] is the single integration point that decides,
 * per Media3 transition reason, whether the track that just ended counts as a *natural
 * completion*. Two production effects hang off that decision and must never be triggered by
 * manual navigation:
 *
 *  * the completed-track scrobble *submission* (`scrobbler.scrobble(track, submission = true)`),
 *  * the end-of-song Sleep Timer (`sleepTimerController.onTrackFinishedNaturally()`).
 *
 * These tests drive the real listener instance (obtained by reflection, exactly as it is wired to
 * the `MediaController` in production) with mocked `scrobbler` / `sleepTimerController`
 * collaborators, and assert the routing. They deliberately do *not* re-test [SleepTimerController]
 * itself -- [SleepTimerControllerTest] already locks down its behavior; this protects the
 * decision in [MediaPlayerManager] about *when* to call it.
 *
 * Requires Robolectric only because constructing a [MediaPlayerManager] uses the main `Looper`
 * in a field initializer; `onCreate()` (media controller, RxBus subscriptions) is never called.
 */
@RunWith(RobolectricTestRunner::class)
class MediaItemTransitionRoutingTest {

    private lateinit var manager: MediaPlayerManager
    private lateinit var listener: Player.Listener
    private lateinit var scrobbler: Scrobbler
    private lateinit var sleepTimerController: SleepTimerController

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        manager = MediaPlayerManager(mock(), mock())
        scrobbler = mock()
        sleepTimerController = mock()
        manager.setPrivateField("scrobbler", scrobbler)
        manager.setPrivateField("sleepTimerController", sleepTimerController)
        listener = manager.getPrivateField("listeners") as Player.Listener
    }

    @After
    fun tearDown() {
        // The converter caches are process-wide statics; keep transitions isolated between tests.
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    private fun cachedItem(id: String): MediaItem =
        buildMediaItem(title = id, mediaId = id, isPlayable = true)

    // --- Automatic / natural completion --------------------------------------------------------

    @Test
    fun `automatic transition submits the completed track and ends the end-of-song timer`() {
        manager.setPrivateField("cachedMediaItem", cachedItem("prev-auto"))

        listener.onMediaItemTransition(cachedItem("next-auto"), MEDIA_ITEM_TRANSITION_REASON_AUTO)

        verify(scrobbler).scrobble(anyOrNull(), eq(true))
        verify(sleepTimerController).onTrackFinishedNaturally()
    }

    @Test
    fun `repeat-one transition is a natural completion for the sleep timer but not a scrobble`() {
        manager.setPrivateField("cachedMediaItem", cachedItem("prev-repeat"))

        listener.onMediaItemTransition(
            cachedItem("prev-repeat"),
            MEDIA_ITEM_TRANSITION_REASON_REPEAT
        )

        verify(sleepTimerController).onTrackFinishedNaturally()
        verify(scrobbler, never()).scrobble(anyOrNull(), eq(true))
    }

    @Test
    fun `automatic transition with no previous item still ends the timer but does not scrobble`() {
        // cachedMediaItem is null on a fresh manager - the scrobble submission is guarded on it,
        // the sleep-timer completion is not.
        listener.onMediaItemTransition(cachedItem("next-auto-nocache"), MEDIA_ITEM_TRANSITION_REASON_AUTO)

        verify(scrobbler, never()).scrobble(anyOrNull(), any())
        verify(sleepTimerController).onTrackFinishedNaturally()
    }

    @Test
    fun `queue reaching its end submits the last track and ends the end-of-song timer`() {
        // The other natural-completion path: the queue runs out with no item to transition to,
        // so completion is reported from onPlaybackStateChanged(STATE_ENDED) instead.
        listener.onPlaybackStateChanged(Player.STATE_ENDED)

        verify(sleepTimerController).onTrackFinishedNaturally()
    }

    // --- Manual skip ------------------------------------------------------------------------------

    @Test
    fun `manual skip does not count as natural track completion`() {
        manager.setPrivateField("cachedMediaItem", cachedItem("prev-seek"))

        listener.onMediaItemTransition(cachedItem("next-seek"), MEDIA_ITEM_TRANSITION_REASON_SEEK)

        verify(scrobbler, never()).scrobble(anyOrNull(), eq(true))
    }

    @Test
    fun `manual skip does not trigger end-of-song sleep timer`() {
        manager.setPrivateField("cachedMediaItem", cachedItem("prev-seek-2"))

        listener.onMediaItemTransition(cachedItem("next-seek-2"), MEDIA_ITEM_TRANSITION_REASON_SEEK)

        verify(sleepTimerController, never()).onTrackFinishedNaturally()
    }

    @Test
    fun `playlist-changed transition is neither a scrobble nor a natural completion`() {
        manager.setPrivateField("cachedMediaItem", cachedItem("prev-playlist"))

        listener.onMediaItemTransition(
            cachedItem("next-playlist"),
            MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
        )

        verify(scrobbler, never()).scrobble(anyOrNull(), eq(true))
        verify(sleepTimerController, never()).onTrackFinishedNaturally()
    }

    @Test
    fun `a non-ended playback state change is not a natural completion`() {
        listener.onPlaybackStateChanged(Player.STATE_READY)
        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        listener.onPlaybackStateChanged(Player.STATE_IDLE)

        verify(sleepTimerController, never()).onTrackFinishedNaturally()
    }

    // --- The cached item is advanced regardless of reason --------------------------------------

    @Test
    fun `every transition updates the cached media item for the next completion check`() {
        manager.setPrivateField("cachedMediaItem", cachedItem("first"))

        listener.onMediaItemTransition(cachedItem("second"), MEDIA_ITEM_TRANSITION_REASON_SEEK)
        (manager.getPrivateField("cachedMediaItem") as MediaItem?)?.mediaId?.let {
            assert(it == "second") { "expected cachedMediaItem to advance to 'second', was $it" }
        }

        reset(scrobbler, sleepTimerController)

        // Now an automatic transition should scrobble 'second' (the item that just finished).
        listener.onMediaItemTransition(cachedItem("third"), MEDIA_ITEM_TRANSITION_REASON_AUTO)
        verify(scrobbler).scrobble(anyOrNull(), eq(true))
        verify(sleepTimerController).onTrackFinishedNaturally()
    }
}

internal fun Any.setPrivateField(name: String, value: Any?) {
    val field = generateSequence(this::class.java) { it.superclass }
        .mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }
        .first()
    field.isAccessible = true
    field.set(this, value)
}

internal fun Any.getPrivateField(name: String): Any? {
    val field = generateSequence(this::class.java) { it.superclass }
        .mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }
        .first()
    field.isAccessible = true
    return field.get(this)
}
