/*
 * MediaLibrarySessionCallback.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MIXED
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_PLAYLIST
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.StarRating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.RESULT_SUCCESS
import com.google.common.collect.ImmutableList
import android.os.Handler
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.buildMediaItem
import org.moire.ultrasonic.util.setStarred
import org.moire.ultrasonic.util.toMediaItem
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

private const val MEDIA_ROOT_ID = "MEDIA_ROOT_ID"
private const val MEDIA_ALBUM_ID = "MEDIA_ALBUM_ID"
private const val MEDIA_ALBUM_PAGE_ID = "MEDIA_ALBUM_PAGE_ID"
private const val MEDIA_ALBUM_NEWEST_ID = "MEDIA_ALBUM_NEWEST_ID"
private const val MEDIA_ALBUM_RECENT_ID = "MEDIA_ALBUM_RECENT_ID"
private const val MEDIA_ALBUM_FREQUENT_ID = "MEDIA_ALBUM_FREQUENT_ID"
private const val MEDIA_ALBUM_RANDOM_ID = "MEDIA_ALBUM_RANDOM_ID"
private const val MEDIA_ALBUM_STARRED_ID = "MEDIA_ALBUM_STARRED_ID"
private const val MEDIA_SONG_RANDOM_ID = "MEDIA_SONG_RANDOM_ID"
private const val MEDIA_SONG_STARRED_ID = "MEDIA_SONG_STARRED_ID"
private const val MEDIA_ARTIST_ID = "MEDIA_ARTIST_ID"
private const val MEDIA_LIBRARY_ID = "MEDIA_LIBRARY_ID"
private const val MEDIA_PLAYLIST_ID = "MEDIA_PLAYLIST_ID"
private const val MEDIA_ALBUM_ITEM = "MEDIA_ALBUM_ITEM"
private const val MEDIA_PLAYLIST_SONG_ITEM = "MEDIA_PLAYLIST_SONG_ITEM"
private const val MEDIA_PLAYLIST_ITEM = "MEDIA_PLAYLIST_ITEM"
private const val MEDIA_ARTIST_ITEM = "MEDIA_ARTIST_ITEM"
private const val MEDIA_ARTIST_SECTION = "MEDIA_ARTIST_SECTION"
private const val MEDIA_ALBUM_SONG_ITEM = "MEDIA_ALBUM_SONG_ITEM"
private const val MEDIA_SONG_STARRED_ITEM = "MEDIA_SONG_STARRED_ITEM"
private const val MEDIA_SONG_RANDOM_ITEM = "MEDIA_SONG_RANDOM_ITEM"
private const val MEDIA_SEARCH_SONG_ITEM = "MEDIA_SEARCH_SONG_ITEM"

// MEDIA_PLAYLIST_ITEM|<id>|<name>
private const val PLAYLIST_NODE_ID_PARTS = 3

// Currently the display limit for long lists is 100 items
private const val DISPLAY_LIMIT = 100
private const val SEARCH_LIMIT = 10

// List of available custom SessionCommands
const val PLAY_COMMAND = "play "

/**
 * MediaBrowserService implementation for e.g. Android Auto
 */
@Suppress("TooManyFunctions", "LargeClass", "UnusedPrivateMember")
class MediaLibrarySessionCallback :
    MediaLibraryService.MediaLibrarySession.Callback,
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()
    private val lifecycleSupport: MediaPlayerLifecycleSupport by inject()
    private val mediaPlayerManager: MediaPlayerManager by inject()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val mainJob = SupervisorJob()
    private val mainScope = CoroutineScope(mainJob + Dispatchers.Main)

    // Seams for tests (the defaults are the production behaviour).
    internal var activeServerId: () -> Int = { ActiveServerProvider.getActiveServerId() }
    internal var resolutionTimeoutMs: Long = RESOLUTION_TIMEOUT_MS
    internal var mediaIdResolver: suspend (String) -> List<Track>? = { tracksFromMediaId(it) }

    // The playback request currently being resolved; a newer one supersedes (cancels) it, so a late
    // result for an older tap can never replace the queue the user asked for last.
    private var pendingPlaybackRequest: ListenableFuture<*>? = null

    /** Cancels everything still in flight; called when the owning service is destroyed. */
    fun release() {
        serviceJob.cancel()
        mainJob.cancel()
        pendingPlaybackRequest = null
    }

    // What the user was last shown (browse) so that a tap plays that very list, even when the list
    // is random or changes on the server meanwhile. Keyed by server AND list identity, immutable
    // values, written only by a browse that succeeded; playback resolution never writes it.
    private val shownLists = ShownLists()
    private val searchTracks = SearchTracks()
    private val searchSnapshots = SearchSnapshots()

    internal var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
    internal var musicServiceProvider: () -> MusicService = { MusicServiceFactory.getMusicService() }
    private val musicService get() = musicServiceProvider()
    private val isOffline get() = ActiveServerProvider.isOffline()
    private val musicFolderId get() = activeServerProvider.getActiveServer().musicFolderId

    private val placeholderButton = getPlaceholderButton()

    private var heartIsCurrentlyOn = false

    // This button is used for an unstarred track, and its action will star the track
    private val heartButtonToggleOn =
        getHeartCommandButton(
            SessionCommand(
                PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_ON,
                Bundle.EMPTY
            ),
            willHeart = true
        )

    // This button is used for an starred track, and its action will star the track
    private val heartButtonToggleOff =
        getHeartCommandButton(
            SessionCommand(
                PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_OFF,
                Bundle.EMPTY
            ),
            willHeart = false
        )

    private val shuffleButton: CommandButton

    private val repeatOffButton: CommandButton
    private val repeatOneButton: CommandButton
    private val repeatAllButton: CommandButton

    private val allCustomCommands: List<CommandButton>

    val defaultCustomCommands: List<CommandButton>

    init {
        val shuffleCommand = SessionCommand(PlaybackService.CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY)
        shuffleButton = getShuffleCommandButton(shuffleCommand)

        val repeatCommand = SessionCommand(PlaybackService.CUSTOM_COMMAND_REPEAT_MODE, Bundle.EMPTY)
        repeatOffButton = getRepeatModeButton(repeatCommand, Player.REPEAT_MODE_OFF)
        repeatOneButton = getRepeatModeButton(repeatCommand, Player.REPEAT_MODE_ONE)
        repeatAllButton = getRepeatModeButton(repeatCommand, Player.REPEAT_MODE_ALL)

        allCustomCommands = listOf(
            heartButtonToggleOn,
            heartButtonToggleOff,
            shuffleButton,
            repeatOffButton,
            repeatOneButton,
            repeatAllButton
        )

        defaultCustomCommands = listOf(heartButtonToggleOn, shuffleButton, repeatOffButton)
    }

    /**
     * Called when a {@link MediaBrowser} requests the root {@link MediaItem} by {@link
     * MediaBrowser#getLibraryRoot(LibraryParams)}.
     *
     * <p>Return a {@link ListenableFuture} to send a {@link LibraryResult} back to the browser
     * asynchronously. You can also return a {@link LibraryResult} directly by using Guava's
     * {@link Futures#immediateFuture(Object)}.
     *
     * <p>The {@link LibraryResult#params} may differ from the given {@link LibraryParams params}
     * if the session can't provide a root that matches with the {@code params}.
     *
     * <p>To allow browsing the media library, return a {@link LibraryResult} with {@link
     * LibraryResult#RESULT_SUCCESS} and a root {@link MediaItem} with a valid {@link
     * MediaItem#mediaId}. The media id is required for the browser to get the children under the
     * root.
     *
     * <p>Interoperability: If this callback is called because a legacy {@link
     * android.support.v4.media.MediaBrowserCompat} has requested a {@link
     * androidx.media.MediaBrowserServiceCompat.BrowserRoot}, then the main thread may be blocked
     * until the returned future is done. If your service may be queried by a legacy {@link
     * android.support.v4.media.MediaBrowserCompat}, you should ensure that the future completes
     * quickly to avoid blocking the main thread for a long period of time.
     *
     * @param session The session for this event.
     * @param browser The browser information.
     * @param params The optional parameters passed by the browser.
     * @return A pending result that will be resolved with a root media item.
     * @see SessionCommand#COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT
     */
    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        Timber.i("onGetLibraryRoot")
        return Futures.immediateFuture(
            LibraryResult.ofItem(
                buildMediaItem(
                    "Root Folder",
                    MEDIA_ROOT_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MEDIA_TYPE_FOLDER_MIXED
                ),
                params
            )
        )
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        Timber.i("onConnect")

        val connectionResult = super.onConnect(session, controller)
        val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()

        for (commandButton in allCustomCommands) {
            // Add custom command to available session commands.
            commandButton.sessionCommand?.let { availableSessionCommands.add(it) }
        }

        // Connecting a controller (Android Auto, the notification, the phone UI) never touches the
        // player's repeat mode: it belongs to the user. Android Auto used to force REPEAT_MODE_ALL
        // here and later via a CarConnection observer; see AA-03/AA-04 in the Android Auto audit.

        return MediaSession.ConnectionResult.accept(
            availableSessionCommands.build(),
            connectionResult.availablePlayerCommands
        )
    }

    /**
     * Media3's playback-resumption contract (issue #37): the returned future MUST complete with the
     * queue to resume (or fail), otherwise the resumption request - a media-button / System UI
     * resume tap after the process was killed - never finishes and Android is left with a stale
     * resumption card. This used to return a future that was never completed. It now completes
     * with the restored queue once [lifecycleSupport] has really put it in the player, fails when
     * there is nothing to resume, and can never hang (timeout).
     */
    @OptIn(UnstableApi::class)
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        serviceScope.launch {
            lifecycleSupport.onCreate(isForPlayback) {
                // Player access must happen on its (main) application looper.
                Handler(mediaSession.player.applicationLooper).post {
                    val resumption = resumptionFromPlayer(mediaSession.player)
                    if (resumption != null) {
                        result.set(resumption)
                    } else {
                        result.setException(IllegalStateException("Nothing to resume"))
                    }
                }
            }
        }
        serviceScope.launch {
            delay(RESUMPTION_TIMEOUT_MS)
            // No-op when already completed; guarantees the request never hangs.
            result.setException(IllegalStateException("Playback resumption timed out"))
        }
        return result
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (controller.controllerVersion != 0) {
            // Let Media3 controller (for instance the MediaNotificationProvider)
            // know about the custom layout right after it connected.
            with(session) {
                setCustomLayout(session.buildCustomCommands(canShuffle = canShuffle()))
            }
        }
    }

    private fun getHeartCommandButton(sessionCommand: SessionCommand, willHeart: Boolean) =
        CommandButton.Builder()
            .setDisplayName(
                if (willHeart) {
                    "Love"
                } else {
                    "Dislike"
                }
            )
            .setIconResId(
                // Was rating_star_hollow/rating_star_full - a leftover from before the app
                // dropped 5-star rating for a single heart (see HANDOFF.md). The button's
                // display name ("Love"/"Dislike") already implied a heart, but the actual icon
                // shown by the system media notification, the Quick Settings media card, and
                // Android Auto was a star - found on-device (2026-08-10) by comparing against
                // Spotify/Symfonium, which both show a heart in the same slot.
                if (willHeart) {
                    R.drawable.rating_heart_hollow
                } else {
                    R.drawable.rating_heart_full
                }
            )
            .setSessionCommand(sessionCommand)
            .setEnabled(true)
            .build()

    private fun getShuffleCommandButton(sessionCommand: SessionCommand) = CommandButton.Builder()
        .setDisplayName("Shuffle")
        .setIconResId(R.drawable.media_shuffle)
        .setSessionCommand(sessionCommand)
        .setEnabled(true)
        .build()

    private fun getPlaceholderButton() = CommandButton.Builder()
        .setDisplayName("Placeholder")
        .setIconResId(android.R.color.transparent)
        .setSessionCommand(
            SessionCommand(
                PlaybackService.CUSTOM_COMMAND_PLACEHOLDER,
                Bundle.EMPTY
            )
        )
        .setEnabled(false)
        .build()

    private fun getRepeatModeButton(sessionCommand: SessionCommand, repeatMode: Int) =
        CommandButton.Builder()
            .setDisplayName(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat One"
                    Player.REPEAT_MODE_ALL -> "Repeat All"
                    else -> "Repeat None"
                }
            )
            .setIconResId(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.media_repeat_one
                    Player.REPEAT_MODE_ALL -> R.drawable.media_repeat_all
                    else -> R.drawable.media_repeat_off
                }
            )
            .setSessionCommand(sessionCommand)
            .setEnabled(true)
            .build()

    @OptIn(UnstableApi::class)
    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        Timber.i("onGetItem")

        // A playlist node is described by its own id and name; fetching the whole playlist (once per
        // row Android Auto asks about) just to answer with one of its tracks cost a request per
        // playlist every time the list was shown, and answered with a track for a folder id.
        val playlistParts = mediaId.split('|')
        if (playlistParts.first() == MEDIA_PLAYLIST_ITEM && playlistParts.size >= PLAYLIST_NODE_ID_PARTS) {
            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    buildMediaItem(
                        playlistParts[2],
                        mediaId,
                        isPlayable = false,
                        mediaType = MEDIA_TYPE_PLAYLIST
                    ),
                    null
                )
            )
        }

        // Resolving an item can need the server (an album or playlist that is not cached), so it must
        // not run on the session's application looper: resolve off it and complete on the main thread.
        return mainScope.future {
            val mediaItem = try {
                withTimeout(resolutionTimeoutMs) { mediaIdResolver(mediaId) }
                    ?.let { tracks -> tracks.getOrNull(selectedIndexOf(mediaId, tracks)) }
                    ?.toMediaItem()
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "onGetItem timed out for %s", mediaId.substringBefore('|'))
                null
            }
            // TODO:
            // Create LRU Cache of MediaItems, fill it in the other calls
            // and retrieve it here.
            if (mediaItem != null) {
                LibraryResult.ofItem(mediaItem, null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.i("onLoadChildren")
        return guardedBrowse { onLoadChildren(parentId) }
    }

    /**
     * A browse request is bounded and honest (AA-10): it fails with [SessionError.ERROR_IO] when the server
     * failed or did not answer within [resolutionTimeoutMs] (a failure is never reported as an empty
     * folder), with [SessionError.ERROR_INVALID_STATE] when the active server changed while it was loading
     * (the old server's answer is never published for the new one) and with [SessionError.ERROR_BAD_VALUE]
     * for an id Taki did not create. A valid empty collection stays a successful empty result.
     */
    @OptIn(UnstableApi::class)
    @Suppress("TooGenericExceptionCaught")
    private fun guardedBrowse(
        load: () -> ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val serverAtRequest = activeServerId()
        return mainScope.future {
            try {
                val result = withTimeout(resolutionTimeoutMs) { load().await() }
                if (activeServerId() != serverAtRequest) {
                    LibraryResult.ofError(SessionError.ERROR_INVALID_STATE)
                } else {
                    result
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "Browse timed out")
                LibraryResult.ofError(SessionError.ERROR_IO)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BrowseFailure) {
                Timber.i(e, "Browse failed on the server")
                LibraryResult.ofError(SessionError.ERROR_IO)
            } catch (e: IndexOutOfBoundsException) {
                Timber.w(e, "Malformed browse id")
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            } catch (e: NumberFormatException) {
                Timber.w(e, "Malformed browse id")
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            } catch (e: Exception) {
                Timber.w(e, "Browse failed")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        Timber.i("onCustomCommand %s", customCommand.customAction)
        var customCommandFuture: ListenableFuture<SessionResult>? = null

        when (customCommand.customAction) {
            PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_ON -> {
                customCommandFuture = onSetRating(session, controller, HeartRating(true))
                updateCustomHeartButton(session, isHeart = true)
            }

            PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_OFF -> {
                customCommandFuture = onSetRating(session, controller, HeartRating(false))
                updateCustomHeartButton(session, isHeart = false)
            }

            PlaybackService.CUSTOM_COMMAND_SHUFFLE -> {
                customCommandFuture = Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
                shuffleCurrentPlaylist(session.player)
            }

            PlaybackService.CUSTOM_COMMAND_REPEAT_MODE -> {
                customCommandFuture = Futures.immediateFuture(SessionResult(RESULT_SUCCESS))

                session.player.setNextRepeatMode()
                session.updateCustomCommands()
            }

            else -> {
                Timber.d(
                    "CustomCommand not recognized %s with extra %s",
                    customCommand.customAction,
                    customCommand.customExtras.toString()
                )
            }
        }

        return customCommandFuture
            ?: super.onCustomCommand(
                session,
                controller,
                customCommand,
                args
            )
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        val mediaItem = session.player.currentMediaItem

        if (mediaItem != null) {
            if (rating is HeartRating) {
                // setStarred() patches the MediaItem's own "starred" extra (and drops the stale
                // converter-cache entries), so a like/unlike from the notification or Android
                // Auto survives the next MediaItem.toTrack() readback - not just until the
                // converter cache expires, which mutating the derived Track would (issue #1).
                mediaItem.setStarred(rating.isHeart)
            } else if (rating is StarRating) {
                mediaItem.toTrack().userRating = rating.starRating.toInt()
            }
            return onSetRating(
                session,
                controller,
                mediaItem.mediaId,
                rating
            )
        }

        return super.onSetRating(session, controller, rating)
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaId: String,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        // TODO: Through this methods it is possible to set a rating on an arbitrary MediaItem.
        // Right now the ratings are submitted, yet the underlying track is only updated when
        // coming from the other onSetRating(session, controller, rating)
        return serviceScope.future {
            Timber.i(controller.packageName)
            // This function even though its declared in AutoMediaBrowserCallback.kt is
            // actually called every time we set the rating on an MediaItem.
            // To avoid an event loop it does not emit a RatingUpdate event,
            // but calls the Manager directly
            RatingManager.instance.submitRating(
                RatingUpdate(
                    id = mediaId,
                    rating = rating
                )
            )
            return@future SessionResult(RESULT_SUCCESS)
        }
    }

    /*
     * For some reason the LocalConfiguration of MediaItem are stripped somewhere in ExoPlayer,
     * and thereby customarily it is required to rebuild it..
     * See also: https://stackoverflow.com/questions/70096715/adding-mediaitem-when-using-the-media3-library-caused-an-error
     */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        Timber.i("onAddMediaItems")

        if (mediaItems.isEmpty()) return Futures.immediateFuture(mediaItems)

        // Appending a song (as opposed to starting playback of it) adds that song only.
        return supersedePending(
            Futures.transform(
                requestPlayback(mediaItems),
                { it.itemsToAdd() },
                MoreExecutors.directExecutor()
            )
        )
    }

    /**
     * Starting playback (AA-07): a song picked from an album, playlist, favorites or random list
     * starts inside its list, at its own position. Android Auto sends only the media id of the song;
     * the whole list and the index of that song are returned, so Media3 queues the list and begins
     * at the selected item instead of playing it alone.
     */
    @OptIn(UnstableApi::class)
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        Timber.i("onSetMediaItems")

        if (mediaItems.isEmpty()) {
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }

        return supersedePending(
            Futures.transform(
                requestPlayback(mediaItems),
                {
                    // Items the controller built itself keep the start it asked for.
                    MediaSession.MediaItemsWithStartPosition(
                        it.items,
                        it.startIndex ?: startIndex,
                        if (it.startIndex != null) C.TIME_UNSET else startPositionMs
                    )
                },
                MoreExecutors.directExecutor()
            )
        )
    }

    // The newest playback request wins: supersede one that is still resolving.
    private fun <T> supersedePending(request: ListenableFuture<T>): ListenableFuture<T> {
        pendingPlaybackRequest?.takeIf { !it.isDone }?.cancel(true)
        pendingPlaybackRequest = request
        return request
    }

    private fun requestPlayback(mediaItems: MutableList<MediaItem>): ListenableFuture<ResolvedPlayback> {
        // A playback request is the moment the playback infrastructure (MediaPlayerManager and its
        // controller: stall watchdog, network recovery, scrobbling, queue persistence, error
        // handling) is needed. Browsing and connecting do not start it. Without this, a cold,
        // service-only Android Auto session played on the bare player (issue #43, AA-01).
        val ready = SettableFuture.create<Unit>()
        lifecycleSupport.ensureCreatedForPlayback { ready.set(Unit) }
        // Playback must never wait indefinitely for it.
        if (!ready.isDone) {
            serviceScope.launch {
                delay(PLAYBACK_INFRASTRUCTURE_TIMEOUT_MS)
                ready.set(Unit)
            }
        }
        return Futures.transformAsync(
            ready,
            { resolveMediaItems(mediaItems) },
            MoreExecutors.directExecutor()
        )
    }

    private fun resolveMediaItems(
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<ResolvedPlayback> {
        // Return early if its a search
        if (mediaItems[0].requestMetadata.searchQuery != null) {
            return playFromSearch(mediaItems[0].requestMetadata.searchQuery!!)
        }

        val updatedMediaItems: List<MediaItem> =
            mediaItems.mapNotNull { mediaItem ->
                if (mediaItem.requestMetadata.mediaUri != null) {
                    mediaItem.buildUpon()
                        .setUri(mediaItem.requestMetadata.mediaUri)
                        .build()
                } else {
                    null
                }
            }

        return if (updatedMediaItems.isNotEmpty()) {
            Futures.immediateFuture(ResolvedPlayback(updatedMediaItems, startIndex = null))
        } else {
            // Android Auto devices still only use the MediaId to identify the selected Items
            // They also only select a single item at once
            onAddLegacyAutoItems(mediaItems)
        }
    }

    /**
     * Resolves a legacy, media-id-only selection off the main thread (it may need the server) and
     * completes on the main thread. Fails (never hangs): after [resolutionTimeoutMs], when nothing
     * could be resolved, when the media id is malformed, or when the active server changed meanwhile,
     * so a late response can not put another server's tracks into the queue.
     */
    @Suppress("TooGenericExceptionCaught") // a malformed id surfaces as IndexOutOfBounds
    private fun onAddLegacyAutoItems(
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<ResolvedPlayback> {
        val mediaId = mediaItems.first().mediaId
        Timber.i("onAddLegacyAutoItems %s", mediaId.substringBefore('|'))

        val serverAtRequest = activeServerId()
        return mainScope.future {
            val tracks = try {
                withTimeout(resolutionTimeoutMs) { mediaIdResolver(mediaId) }
            } catch (e: TimeoutCancellationException) {
                throw IllegalStateException("Resolving the selected item timed out", e)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException("Malformed media id", e)
            }
            check(activeServerId() == serverAtRequest) {
                "The server changed while the item was resolving"
            }
            check(!tracks.isNullOrEmpty()) { "Nothing to play for the selected item" }
            val selected = selectedIndexOf(mediaId, tracks)
            check(selected != NOT_FOUND) { "The selected song is not in its list" }
            ResolvedPlayback(
                tracks.map { track -> track.toMediaItem() },
                startIndex = selected,
                isSongSelection = isSongSelection(mediaId)
            )
        }
    }

    @OptIn(UnstableApi::class)
    @Suppress("ReturnCount", "ComplexMethod")
    private fun onLoadChildren(
        parentId: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.d("AutoMediaBrowserService onLoadChildren called. ParentId: %s", parentId)

        val parentIdParts = parentId.split('|')

        return when (parentIdParts.first()) {
            MEDIA_ROOT_ID -> getRootItems()

            MEDIA_LIBRARY_ID -> getLibrary()

            MEDIA_ARTIST_ID -> getArtists()

            MEDIA_ARTIST_SECTION -> getArtists(parentIdParts[1])

            MEDIA_ALBUM_ID -> getAlbums(AlbumListType.SORTED_BY_NAME)

            MEDIA_ALBUM_PAGE_ID -> getAlbums(
                AlbumListType.fromName(parentIdParts[1]),
                parentIdParts[2].toInt()
            )

            MEDIA_PLAYLIST_ID -> getPlaylists()

            MEDIA_ALBUM_FREQUENT_ID -> getAlbums(AlbumListType.FREQUENT)

            MEDIA_ALBUM_NEWEST_ID -> getAlbums(AlbumListType.NEWEST)

            MEDIA_ALBUM_RECENT_ID -> getAlbums(AlbumListType.RECENT)

            MEDIA_ALBUM_RANDOM_ID -> getAlbums(AlbumListType.RANDOM)

            MEDIA_ALBUM_STARRED_ID -> getAlbums(AlbumListType.STARRED)

            MEDIA_SONG_RANDOM_ID -> getRandomSongs()

            MEDIA_SONG_STARRED_ID -> getStarredSongs()

            MEDIA_PLAYLIST_ITEM -> getPlaylist(parentIdParts[1], parentIdParts[2])

            MEDIA_ARTIST_ITEM -> getAlbumsForArtist(
                parentIdParts[1],
                parentIdParts[2]
            )

            MEDIA_ALBUM_ITEM -> getSongsForAlbum(parentIdParts[1], parentIdParts[2])

            else -> Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }
    }

    /**
     * Voice play-from-search (`requestMetadata.searchQuery`): resolves one playable result on the active
     * server, off the main thread, bounded by [resolutionTimeoutMs]. Policy (server order is kept):
     * exact song title, else exact album name (the whole album), else the server's top song, else its top
     * album. Fails in a controlled way (never plays something unrelated) when the query is blank, nothing
     * matches, the server fails or answers after the server changed.
     */
    private fun playFromSearch(query: String): ListenableFuture<ResolvedPlayback> {
        Timber.i("AutoMediaBrowserService play from search")
        val term = searchTerm(query)
        val serverAtRequest = activeServerId()

        return mainScope.future {
            check(term != null) { "Empty search query" }
            val tracks = try {
                withTimeout(resolutionTimeoutMs) { tracksForVoiceSearch(term) }
            } catch (e: TimeoutCancellationException) {
                throw IllegalStateException("The search timed out", e)
            }
            check(activeServerId() == serverAtRequest) { "The server changed while searching" }
            check(!tracks.isNullOrEmpty()) { "Nothing found for the search" }
            ResolvedPlayback(tracks.map { it.toMediaItem() }, startIndex = null)
        }
    }

    private suspend fun tracksForVoiceSearch(term: String): List<Track>? {
        val result = searchLibrary(term) ?: return null
        result.songs.firstOrNull { it.title.equals(term, ignoreCase = true) }?.let { return listOf(it) }
        result.albums.firstOrNull {
            (it.title ?: it.name).equals(term, ignoreCase = true)
        }?.let { return playAlbum(it.id, it.name) }
        result.songs.firstOrNull()?.let { return listOf(it) }
        return result.albums.firstOrNull()?.let { playAlbum(it.id, it.name) }
    }

    /** The query without Android Auto's "play" prefix, or null when nothing is left to search for. */
    private fun searchTerm(query: String): String? {
        val term = if (query.startsWith(PLAY_COMMAND, ignoreCase = true)) {
            query.drop(PLAY_COMMAND.length)
        } else {
            query
        }
        return term.trim().takeIf { it.isNotEmpty() }
    }

    /** One server search; remembers the found songs by (server, id) so a tapped row needs no new request. */
    private suspend fun searchLibrary(term: String): SearchResult? {
        val server = activeServerId()
        val result = withContext(ioDispatcher) {
            callWithErrorHandling {
                musicService.search(SearchCriteria(term, SEARCH_LIMIT, SEARCH_LIMIT, SEARCH_LIMIT))
            }
        } ?: return null
        result.songs.forEach { searchTracks.put(server, it) }
        return result
    }

    /** Media3 browse search: runs the search, then tells the controller how many results there are. */
    @OptIn(UnstableApi::class)
    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        Timber.i("onSearch")
        return mainScope.future {
            val items = searchItems(query)
            when {
                items == null -> LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                items.isError -> LibraryResult.ofError(SessionError.ERROR_IO)
                else -> {
                    session.notifySearchResultChanged(browser, query, items.list.size, params)
                    LibraryResult.ofVoid()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Timber.i("onGetSearchResult")
        return mainScope.future {
            val items = searchItems(query)
            when {
                items == null -> LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                items.isError -> LibraryResult.ofError(SessionError.ERROR_IO)
                else -> {
                    val size = items.list.size.toLong()
                    val from = (page.toLong() * pageSize).coerceAtMost(size).toInt()
                    val to = (from.toLong() + pageSize).coerceAtMost(size).toInt()
                    LibraryResult.ofItemList(ImmutableList.copyOf(items.list.subList(from, to)), params)
                }
            }
        }
    }

    private class SearchItems(val list: List<MediaItem>, val isError: Boolean = false)

    /**
     * The result rows (songs, then albums, in server order) for [query] on the active server: the
     * remembered snapshot of this very query, else a bounded server search. Null for a blank query.
     * Searching never touches the player.
     */
    private suspend fun searchItems(query: String): SearchItems? {
        val term = searchTerm(query) ?: return null
        val server = activeServerId()
        searchSnapshots.get(server, term)?.let { return SearchItems(it) }

        val result = try {
            withTimeout(resolutionTimeoutMs) { searchLibrary(term) }
        } catch (e: TimeoutCancellationException) {
            Timber.w(e, "Search timed out")
            null
        }
        if (result == null || activeServerId() != server) return SearchItems(emptyList(), isError = true)

        val rows = ArrayList<MediaItem>()
        result.songs.forEach { song ->
            rows.add(song.toMediaItem(listOf(MEDIA_SEARCH_SONG_ITEM, song.id).joinToString("|")))
        }
        result.albums.forEach { album ->
            rows.add(
                buildMediaItem(
                    album.title ?: "",
                    listOf(MEDIA_ALBUM_ITEM, album.id, album.name).joinToString("|"),
                    isPlayable = false
                )
            )
        }
        searchSnapshots.put(server, term, rows)
        return SearchItems(rows)
    }

    @Suppress("MagicNumber", "ComplexMethod")
    private suspend fun tracksFromMediaId(mediaId: String?): List<Track>? {
        Timber.d(
            "AutoMediaBrowserService onPlayFromMediaIdRequested called. mediaId: %s",
            mediaId
        )

        if (mediaId == null) return null
        val mediaIdParts = mediaId.split('|')

        // TODO Media Artist item is missing!!!
        return when (mediaIdParts.first()) {
            MEDIA_PLAYLIST_ITEM -> playPlaylist(mediaIdParts[1], mediaIdParts[2])

            MEDIA_PLAYLIST_SONG_ITEM -> playPlaylistSong(
                mediaIdParts[1],
                mediaIdParts[2],
                mediaIdParts[3]
            )

            MEDIA_ALBUM_ITEM -> playAlbum(mediaIdParts[1], mediaIdParts[2])

            MEDIA_ALBUM_SONG_ITEM -> playAlbumSong(
                mediaIdParts[1],
                mediaIdParts[2],
                mediaIdParts[3]
            )

            MEDIA_SONG_STARRED_ID -> playStarredSongs()

            MEDIA_SONG_STARRED_ITEM -> playStarredSong(mediaIdParts[1])

            MEDIA_SONG_RANDOM_ID -> playRandomSongs()

            MEDIA_SONG_RANDOM_ITEM -> playRandomSong(mediaIdParts[1])

            MEDIA_SEARCH_SONG_ITEM -> playSearch(mediaIdParts[1])

            else -> {
                listOf()
            }
        }
    }

    /** A search-result row is the song with that id on the active server (no "latest search" state). */
    private suspend fun playSearch(id: String): List<Track>? {
        val server = activeServerId()
        searchTracks.get(server, id)?.let { return listOf(it) }
        val song = withContext(ioDispatcher) { callWithErrorHandling { musicService.getSong(id) } }
        return song?.also { searchTracks.put(server, it) }?.let { listOf(it) }
    }

    private fun getRootItems(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        if (!isOffline) {
            mediaItems.add(
                R.string.music_library_label,
                MEDIA_LIBRARY_ID,
                null,
                isBrowsable = true,
                mediaType = MEDIA_TYPE_FOLDER_MIXED,
                icon = R.drawable.ic_library
            )
        }

        mediaItems.add(
            R.string.main_artists_title,
            MEDIA_ARTIST_ID,
            null,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_FOLDER_ARTISTS,
            icon = R.drawable.ic_artist
        )

        if (!isOffline) {
            mediaItems.add(
                R.string.main_albums_title,
                MEDIA_ALBUM_ID,
                null,
                isBrowsable = true,
                mediaType = MEDIA_TYPE_FOLDER_ALBUMS,
                icon = R.drawable.ic_menu_browse
            )
        }

        mediaItems.add(
            R.string.playlist_label,
            MEDIA_PLAYLIST_ID,
            null,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_FOLDER_PLAYLISTS,
            icon = R.drawable.ic_menu_playlists
        )

        return Futures.immediateFuture(LibraryResult.ofItemList(mediaItems, null))
    }

    private fun getLibrary(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        // Songs
        mediaItems.add(
            R.string.main_songs_random,
            MEDIA_SONG_RANDOM_ID,
            R.string.main_songs_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )

        mediaItems.add(
            R.string.main_songs_starred,
            MEDIA_SONG_STARRED_ID,
            R.string.main_songs_title,
            isBrowsable = true,
            mediaType = MEDIA_TYPE_PLAYLIST
        )

        // Albums
        mediaItems.add(
            R.string.main_albums_newest,
            MEDIA_ALBUM_NEWEST_ID,
            R.string.main_albums_title
        )

        mediaItems.add(
            R.string.main_albums_recent,
            MEDIA_ALBUM_RECENT_ID,
            R.string.main_albums_title,
            mediaType = MEDIA_TYPE_FOLDER_ALBUMS
        )

        mediaItems.add(
            R.string.main_albums_frequent,
            MEDIA_ALBUM_FREQUENT_ID,
            R.string.main_albums_title,
            mediaType = MEDIA_TYPE_FOLDER_ALBUMS
        )

        mediaItems.add(
            R.string.main_albums_random,
            MEDIA_ALBUM_RANDOM_ID,
            R.string.main_albums_title,
            mediaType = MEDIA_TYPE_FOLDER_ALBUMS
        )

        mediaItems.add(
            R.string.main_albums_starred,
            MEDIA_ALBUM_STARRED_ID,
            R.string.main_albums_title,
            mediaType = MEDIA_TYPE_FOLDER_ALBUMS
        )

        return Futures.immediateFuture(LibraryResult.ofItemList(mediaItems, null))
    }

    private fun getArtists(
        section: String? = null
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        // It seems double scoping is required: Media3 requires the Main thread, network operations with musicService forbid the Main thread...
        return mainScope.future {
            var childMediaId: String = MEDIA_ARTIST_ITEM

            var artists = serviceScope.future {
                if (ActiveServerProvider.shouldUseId3Tags()) {
                    // TODO this list can be big so we're not refreshing.
                    //  Maybe a refresh menu item can be added
                    callWithErrorHandling { musicService.getArtists(false) }
                } else {
                    // This will be handled at getSongsForAlbum, which supports navigation
                    childMediaId = MEDIA_ALBUM_ITEM
                    callWithErrorHandling { musicService.getIndexes(musicFolderId, false) }
                }
            }.await()

            if (artists != null) {
                if (section != null) {
                    artists = artists.filter { artist ->
                        getSectionFromName(artist.name ?: "") == section
                    }
                }

                // If there are too many artists, create alphabetic index of them
                if (section == null && artists.count() > DISPLAY_LIMIT) {
                    val index = mutableListOf<String>()
                    // TODO This sort should use ignoredArticles somehow...
                    artists = artists.sortedBy { artist -> artist.name }
                    artists.map { artist ->
                        val currentSection = getSectionFromName(artist.name ?: "")
                        if (!index.contains(currentSection)) {
                            index.add(currentSection)
                            mediaItems.add(
                                currentSection,
                                listOf(MEDIA_ARTIST_SECTION, currentSection).joinToString("|")
                            )
                        }
                    }
                } else {
                    artists.map { artist ->
                        mediaItems.add(
                            artist.name ?: "",
                            listOf(childMediaId, artist.id, artist.name).joinToString("|")
                        )
                    }
                }
            }
            if (artists == null) throw BrowseFailure()
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getAlbumsForArtist(
        id: String,
        name: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val albums = serviceScope.future {
                if (ActiveServerProvider.shouldUseId3Tags()) {
                    callWithErrorHandling { musicService.getAlbumsOfArtist(id, name, false) }
                } else {
                    callWithErrorHandling {
                        musicService.getMusicDirectory(id, name, false).getAlbums()
                    }
                }
            }.await()

            albums?.map { album ->
                mediaItems.add(
                    album.title ?: "",
                    listOf(MEDIA_ALBUM_ITEM, album.id, album.name)
                        .joinToString("|")
                )
            }
            if (albums == null) throw BrowseFailure()
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getSongsForAlbum(
        id: String,
        name: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val songs = listSongsInMusicService(id, name)

            if (songs != null) {
                if (songs.getChildren(includeDirs = true, includeFiles = false).isEmpty() &&
                    songs.getChildren(includeDirs = false, includeFiles = true).isNotEmpty()
                ) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_ALBUM_ITEM, id, name).joinToString("|"))
                }

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.getChildren().take(DISPLAY_LIMIT).toMutableList()

                items.sortWith { o1, o2 ->
                    if (o1.isDirectory && o2.isDirectory) {
                        (o1.title ?: "").compareTo(o2.title ?: "")
                    } else if (o1.isDirectory) {
                        -1
                    } else {
                        1
                    }
                }

                items.map { item ->
                    if (item.isDirectory) {
                        mediaItems.add(
                            item.title ?: "",
                            listOf(MEDIA_ALBUM_ITEM, item.id, item.name).joinToString("|")
                        )
                    } else if (item is Track) {
                        mediaItems.add(
                            item.toMediaItem(
                                listOf(
                                    MEDIA_ALBUM_SONG_ITEM,
                                    id,
                                    name,
                                    item.id
                                ).joinToString("|")
                            )
                        )
                    }
                }
            }

            if (songs == null) throw BrowseFailure()
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getAlbums(
        type: AlbumListType,
        page: Int? = null
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val offset = (page ?: 0) * DISPLAY_LIMIT

            val albums = serviceScope.future {
                if (ActiveServerProvider.shouldUseId3Tags()) {
                    callWithErrorHandling {
                        musicService.getAlbumList2(
                            type,
                            DISPLAY_LIMIT,
                            offset,
                            null,
                            null
                        )
                    }
                } else {
                    callWithErrorHandling {
                        musicService.getAlbumList(
                            type,
                            DISPLAY_LIMIT,
                            offset,
                            null
                        )
                    }
                }
            }.await()

            albums?.map { album ->
                mediaItems.add(
                    album.title ?: "",
                    listOf(MEDIA_ALBUM_ITEM, album.id, album.name)
                        .joinToString("|")
                )
            }

            if ((albums?.size ?: 0) >= DISPLAY_LIMIT) {
                mediaItems.add(
                    R.string.search_more,
                    listOf(MEDIA_ALBUM_PAGE_ID, type.typeName, (page ?: 0) + 1).joinToString("|"),
                    null
                )
            }

            if (albums == null) throw BrowseFailure()
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getPlaylists(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val playlists = serviceScope.future {
                callWithErrorHandling { musicService.getPlaylists(true) }
            }.await()

            playlists?.map { playlist ->
                mediaItems.add(
                    playlist.name,
                    listOf(MEDIA_PLAYLIST_ITEM, playlist.id, playlist.name)
                        .joinToString("|"),
                    mediaType = MEDIA_TYPE_PLAYLIST
                )
            }
            if (playlists == null) throw BrowseFailure()
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private fun getPlaylist(
        id: String,
        name: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val server = activeServerId()
            val content = withContext(ioDispatcher) {
                callWithErrorHandling { musicService.getPlaylist(id, name) }
            }

            if (content != null) {
                if (content.size > 1) {
                    mediaItems.addPlayAllItem(
                        listOf(MEDIA_PLAYLIST_ITEM, id, name).joinToString("|")
                    )
                }

                // The list may contain random elements: remember what is shown (see shownLists)
                val shown = content.getTracks()
                shownLists.put(ShownLists.playlist(server, id), shown)
                shown.take(DISPLAY_LIMIT).mapIndexed { position, item ->
                    mediaItems.add(
                        item.toMediaItem(
                            // The position tells apart a song the playlist holds more than once
                            listOf(
                                MEDIA_PLAYLIST_SONG_ITEM,
                                id,
                                name,
                                item.id,
                                position.toString()
                            ).joinToString("|")
                        )
                    )
                }
            }
            if (content == null) throw BrowseFailure()
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private suspend fun playPlaylist(id: String, name: String): List<Track>? {
        val server = activeServerId()
        shownLists.get(ShownLists.playlist(server, id))?.let { return it }
        // Not browsed in this session (or evicted): fetch this very playlist, for this request only.
        val content = withContext(ioDispatcher) {
            callWithErrorHandling { musicService.getPlaylist(id, name) }
        }
        return content?.getTracks()
    }

    private suspend fun playPlaylistSong(id: String, name: String, songId: String): List<Track>? {
        val tracks = playPlaylist(id, name)
        return tracks?.takeIf { list -> list.any { x -> x.id == songId } }
    }

    private suspend fun playAlbum(id: String, name: String?): List<Track>? {
        val songs = listSongsInMusicService(id, name)
        if (songs != null) return songs.getTracks()
        return null
    }

    private suspend fun playAlbumSong(id: String, name: String?, songId: String): List<Track>? {
        val tracks = listSongsInMusicService(id, name)?.getTracks()
        return tracks?.takeIf { list -> list.any { x -> x.id == songId } }
    }

    private fun getStarredSongs(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val server = activeServerId()
            val songs = listStarredSongsInMusicService()

            if (songs != null) {
                if (songs.songs.count() > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_STARRED_ID).joinToString("|"))
                }

                // TODO: Paging is not implemented for songs, is it necessary at all?
                // Android Auto shows the first DISPLAY_LIMIT rows, but Play All and a tapped favorite
                // use the complete list (the server returns all favorites in one response).
                shownLists.put(ShownLists.starred(server), songs.songs)
                songs.songs.take(DISPLAY_LIMIT).map { song ->
                    mediaItems.add(
                        song.toMediaItem(
                            listOf(MEDIA_SONG_STARRED_ITEM, song.id).joinToString("|")
                        )
                    )
                }
            }
            if (songs == null) throw BrowseFailure()
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private suspend fun playStarredSongs(): List<Track>? {
        shownLists.get(ShownLists.starred(activeServerId()))?.let { return it }
        return listStarredSongsInMusicService()?.songs
    }

    private suspend fun playStarredSong(songId: String): List<Track>? {
        return playStarredSongs()?.takeIf { list -> list.any { x -> x.id == songId } }
    }

    private fun getRandomSongs(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val mediaItems: MutableList<MediaItem> = ArrayList()

        return mainScope.future {
            val server = activeServerId()
            val songs = withContext(ioDispatcher) {
                callWithErrorHandling { musicService.getRandomSongs(DISPLAY_LIMIT) }
            }

            if (songs != null) {
                if (songs.size > 1) {
                    mediaItems.addPlayAllItem(listOf(MEDIA_SONG_RANDOM_ID).joinToString("|"))
                }

                // TODO: Paging is not implemented for songs, is it necessary at all?
                val items = songs.getTracks()
                shownLists.put(ShownLists.random(server), items)
                items.map { song ->
                    mediaItems.add(
                        song.toMediaItem(
                            listOf(MEDIA_SONG_RANDOM_ITEM, song.id).joinToString("|")
                        )
                    )
                }
            }
            if (songs == null) throw BrowseFailure()
            return@future LibraryResult.ofItemList(mediaItems, null)
        }
    }

    private suspend fun playRandomSongs(): List<Track>? {
        shownLists.get(ShownLists.random(activeServerId()))?.let { return it }
        // Nothing was shown: ask for a new set of random songs (this request only)
        val content = withContext(ioDispatcher) {
            callWithErrorHandling { musicService.getRandomSongs(DISPLAY_LIMIT) }
        }
        return content?.getTracks()
    }

    private fun playRandomSong(songId: String): List<Track>? {
        // A random song can only be played from the set that was shown; without it there is no
        // way to find the selected song.
        return shownLists.get(ShownLists.random(activeServerId()))
            ?.takeIf { list -> list.any { x -> x.id == songId } }
    }

    private suspend fun listSongsInMusicService(id: String, name: String?): MusicDirectory? =
        withContext(ioDispatcher) {
            if (ActiveServerProvider.shouldUseId3Tags()) {
                callWithErrorHandling { musicService.getAlbumAsDir(id, name, false) }
            } else {
                callWithErrorHandling { musicService.getMusicDirectory(id, name, false) }
            }
        }

    private suspend fun listStarredSongsInMusicService(): SearchResult? =
        withContext(ioDispatcher) {
            if (ActiveServerProvider.shouldUseId3Tags()) {
                callWithErrorHandling { musicService.getStarred2() }
            } else {
                callWithErrorHandling { musicService.getStarred() }
            }
        }

    private fun MutableList<MediaItem>.add(
        title: String,
        mediaId: String,
        mediaType: Int = MEDIA_TYPE_MIXED,
        isBrowsable: Boolean = false
    ) {
        val mediaItem = buildMediaItem(
            title,
            mediaId,
            isPlayable = false,
            isBrowsable = isBrowsable,
            mediaType = mediaType
        )

        this.add(mediaItem)
    }

    @Suppress("LongParameterList")
    private fun MutableList<MediaItem>.add(
        resId: Int,
        mediaId: String,
        groupNameId: Int?,
        isBrowsable: Boolean = true,
        mediaType: Int = MEDIA_TYPE_FOLDER_MIXED,
        icon: Int? = null
    ) {
        val applicationContext = UApp.applicationContext()

        val mediaItem = buildMediaItem(
            applicationContext.getString(resId),
            mediaId,
            isPlayable = !isBrowsable,
            isBrowsable = isBrowsable,
            imageUri = if (icon != null) {
                Util.getUriToDrawable(applicationContext, icon)
            } else {
                null
            },
            group = if (groupNameId != null) {
                applicationContext.getString(groupNameId)
            } else {
                null
            },
            mediaType = mediaType
        )

        this.add(mediaItem)
    }

    private fun MutableList<MediaItem>.addPlayAllItem(mediaId: String) {
        this.add(
            R.string.select_album_play_all,
            mediaId,
            null,
            false,
            icon = R.drawable.media_start
        )
    }

    private fun getSectionFromName(name: String): String {
        var section = name.first().uppercaseChar()
        if (!section.isLetter()) section = '#'
        return section.toString()
    }

    // suspend so callers can pass a suspend lambda (needed for MusicService.search(), the only
    // suspend call among all the callWithErrorHandling { musicService.xxx(...) } call sites in
    // this file). Every call site is already
    // inside a serviceScope.future { ... } coroutine, so this doesn't change how any of them are
    // invoked; a plain non-suspend lambda still satisfies a `suspend () -> T` parameter type.
    private suspend fun <T> callWithErrorHandling(function: suspend () -> T): T? {
        // TODO Implement better error handling
        return try {
            function()
        } catch (cancelled: CancellationException) {
            // Cancellation (a superseded request, a timeout, service teardown) must propagate.
            throw cancelled
        } catch (all: Exception) {
            Timber.i(all)
            null
        }
    }

    private fun Player.setNextRepeatMode() {
        repeatMode =
            when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
    }

    private fun MediaSession.updateCustomCommands() {
        setCustomLayout(
            buildCustomCommands(
                heartIsCurrentlyOn,
                canShuffle()
            )
        )
    }

    /**
     * Rebuilds the custom buttons from the player's current repeat/shuffle state, so a change made
     * from another controller (the phone UI, a media button) is reflected on the car and in the
     * notification instead of only changes made through the custom commands.
     */
    fun refreshCustomLayout(session: MediaSession) = session.updateCustomCommands()

    /**
     * The active server (or offline mode) changed: everything the host shows came from the old one, so ask
     * subscribed controllers to reload each catalog node once. Event driven (no polling), touches no player
     * state and no cached shown list (those are keyed by server).
     */
    internal fun notifyCatalogChanged(session: MediaLibraryService.MediaLibrarySession) {
        CATALOG_NODES.forEach { session.notifyChildrenChanged(it, 0, null) }
    }

    fun updateCustomHeartButton(session: MediaSession, isHeart: Boolean) {
        with(session) {
            setCustomLayout(
                buildCustomCommands(
                    isHeart = isHeart,
                    canShuffle = canShuffle()
                )
            )
        }
    }

    private fun MediaSession.canShuffle() = player.mediaItemCount > 2

    private fun MediaSession.buildCustomCommands(
        isHeart: Boolean = false,
        canShuffle: Boolean = false
    ): ImmutableList<CommandButton> {
        Timber.d("building custom commands (isHeart = %s, canShuffle = %s)", isHeart, canShuffle)

        heartIsCurrentlyOn = isHeart

        return ImmutableList.copyOf(
            buildList {
                // placeholder must come first here because if there is no next button the first
                // custom command button is place right next to the play/pause button
                if (
                    player.repeatMode != Player.REPEAT_MODE_ALL &&
                    player.currentMediaItemIndex == player.mediaItemCount - 1
                ) {
                    add(placeholderButton)
                }

                // due to the previous placeholder this heart button will always appear to the left
                // of the default playback items
                add(
                    if (isHeart) {
                        heartButtonToggleOff
                    } else {
                        heartButtonToggleOn
                    }
                )

                // both the shuffle and the active repeat mode button will end up in the overflow
                // menu if both are available at the same time
                if (canShuffle) {
                    add(shuffleButton)
                }

                add(
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ONE -> repeatOneButton
                        Player.REPEAT_MODE_ALL -> repeatAllButton
                        else -> repeatOffButton
                    }
                )
            }.asIterable()
        )
    }

    private fun shuffleCurrentPlaylist(player: Player) {
        Timber.d("shuffleCurrentPlaylist")

        // 3 was chosen because that leaves at least two other songs to be shuffled around
        @Suppress("MagicNumber")
        if (player.mediaItemCount < 3) {
            return
        }

        val mediaItemsToShuffle = mutableListOf<MediaItem>()

        for (i in 0 until player.currentMediaItemIndex) {
            mediaItemsToShuffle += player.getMediaItemAt(i)
        }

        for (i in player.currentMediaItemIndex + 1 until player.mediaItemCount) {
            mediaItemsToShuffle += player.getMediaItemAt(i)
        }

        player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
        player.removeMediaItems(0, player.currentMediaItemIndex)

        // A single unchunked addMediaItems() here would reintroduce, via the Shuffle button
        // on a very large queue, the exact main-thread ANR that MediaPlayerManager.
        // addToPlaylistLocked() already fixed for the "add tracks" path. Mirror the same
        // chunked+yielded pattern, and suppress the per-chunk timeline publish the same way
        // for the same reason
        // (see MediaPlayerManager.withTimelinePublishSuppressed).
        mainScope.launch {
            mediaPlayerManager.withTimelinePublishSuppressed {
                for (chunk in mediaItemsToShuffle.shuffled().chunked(ADD_MEDIA_ITEMS_CHUNK_SIZE)) {
                    player.addMediaItems(chunk)
                    yield()
                }
            }
        }
    }
}

/** Longest a playback request waits for the playback infrastructure before it proceeds anyway. */
internal const val PLAYBACK_INFRASTRUCTURE_TIMEOUT_MS = 5_000L

/**
 * The most recently shown Android Auto track lists, keyed by server and list identity (never by name),
 * least-recently-used eviction. Values are immutable lists; every access is synchronized, so concurrent
 * requests can neither see another list's tracks nor corrupt it.
 */
internal class ShownLists(private val limit: Int = SHOWN_LISTS_LIMIT) {
    private val lists = object : LinkedHashMap<String, List<Track>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Track>>?) =
            size > limit
    }

    @Synchronized
    fun get(key: String): List<Track>? = lists[key]

    @Synchronized
    fun put(key: String, tracks: List<Track>) {
        lists[key] = tracks.toList()
    }

    companion object {
        private const val SHOWN_LISTS_LIMIT = 8
        private const val INITIAL_CAPACITY = 16
        private const val LOAD_FACTOR = 0.75f

        fun playlist(server: Int, id: String) = "$server|playlist|$id"
        fun starred(server: Int) = "$server|starred"
        fun random(server: Int) = "$server|random"
    }
}

/** Longest a media-item resolution may take before its request fails (never left pending). */
internal const val RESOLUTION_TIMEOUT_MS = 20_000L

/** Longest a resumption request may wait for the saved queue to be restored. */
internal const val RESUMPTION_TIMEOUT_MS = 20_000L

/**
 * The queue a resumption should hand back to Media3, taken from the (already restored) [player]:
 * every item, the current index and the current position. Null when the queue is empty.
 */
@OptIn(UnstableApi::class)
internal fun resumptionFromPlayer(player: Player): MediaSession.MediaItemsWithStartPosition? {
    val items = List(player.mediaItemCount) { player.getMediaItemAt(it) }
    return resumptionOf(items, player.currentMediaItemIndex, player.currentPosition)
}

@OptIn(UnstableApi::class)
internal fun resumptionOf(
    items: List<MediaItem>,
    currentIndex: Int,
    positionMs: Long
): MediaSession.MediaItemsWithStartPosition? {
    if (items.isEmpty()) return null
    val index = currentIndex.coerceIn(0, items.lastIndex)
    return MediaSession.MediaItemsWithStartPosition(items, index, positionMs.coerceAtLeast(0L))
}

/** A resolved playback request. [startIndex] null: keep the start the controller supplied. */
internal class ResolvedPlayback(
    val items: List<MediaItem>,
    val startIndex: Int?,
    val isSongSelection: Boolean = false
) {
    /** What an append request takes: only the song that was picked, never its whole list. */
    fun itemsToAdd(): List<MediaItem> =
        if (isSongSelection && startIndex != null) listOf(items[startIndex]) else items
}

internal const val NOT_FOUND = -1

private val SONG_ITEM_TYPES = setOf(
    MEDIA_ALBUM_SONG_ITEM,
    MEDIA_PLAYLIST_SONG_ITEM,
    MEDIA_SONG_STARRED_ITEM,
    MEDIA_SONG_RANDOM_ITEM
)

/** True for the media id of one song inside a list (not Play All, not a search result). */
internal fun isSongSelection(mediaId: String): Boolean = mediaId.substringBefore('|') in SONG_ITEM_TYPES

/**
 * Position of the song [mediaId] names inside the resolved [tracks]. A playlist may hold the same
 * song twice, so a playlist row also carries the position it was shown at; it is used when it still
 * points at that song, otherwise the first occurrence is taken. Not a song row: 0 (start of list).
 */
internal fun selectedIndexOf(mediaId: String, tracks: List<Track>): Int {
    if (!isSongSelection(mediaId)) return 0
    val parts = mediaId.split('|')
    val type = parts.first()
    val songId = if (type == MEDIA_SONG_STARRED_ITEM || type == MEDIA_SONG_RANDOM_ITEM) parts[1] else parts[3]
    val shownAt = if (type == MEDIA_PLAYLIST_SONG_ITEM) parts.getOrNull(PLAYLIST_SHOWN_AT_PART)?.toIntOrNull() else null
    if (shownAt != null && tracks.getOrNull(shownAt)?.id == songId) return shownAt
    return tracks.indexOfFirst { it.id == songId }
}

private const val PLAYLIST_SHOWN_AT_PART = 4

/** Songs seen in search results, by (server, song id): a tapped result is that very song, whatever was searched later. */
internal class SearchTracks(private val limit: Int = SEARCH_TRACKS_LIMIT) {
    private val tracks = object : LinkedHashMap<String, Track>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Track>?) = size > limit
    }

    @Synchronized
    fun get(server: Int, id: String): Track? = tracks["$server|$id"]

    @Synchronized
    fun put(server: Int, track: Track) {
        tracks["$server|${track.id}"] = track
    }

    private companion object {
        const val SEARCH_TRACKS_LIMIT = 64
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

/** The result rows of the last few browse searches, by (server, query); immutable values, bounded. */
internal class SearchSnapshots(private val limit: Int = SEARCH_SNAPSHOTS_LIMIT) {
    private val snapshots = object : LinkedHashMap<String, List<MediaItem>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MediaItem>>?) = size > limit
    }

    @Synchronized
    fun get(server: Int, query: String): List<MediaItem>? = snapshots["$server|${query.lowercase()}"]

    @Synchronized
    fun put(server: Int, query: String, rows: List<MediaItem>) {
        snapshots["$server|${query.lowercase()}"] = rows.toList()
    }

    private companion object {
        const val SEARCH_SNAPSHOTS_LIMIT = 4
        const val INITIAL_CAPACITY = 8
        const val LOAD_FACTOR = 0.75f
    }
}

/** The browse nodes the host may show and subscribe to; each is reloaded once when the server changes. */
private val CATALOG_NODES = listOf(
    MEDIA_ROOT_ID,
    MEDIA_LIBRARY_ID,
    MEDIA_ARTIST_ID,
    MEDIA_ALBUM_ID,
    MEDIA_PLAYLIST_ID,
    MEDIA_SONG_STARRED_ID,
    MEDIA_SONG_RANDOM_ID,
    MEDIA_ALBUM_FREQUENT_ID,
    MEDIA_ALBUM_NEWEST_ID,
    MEDIA_ALBUM_RECENT_ID,
    MEDIA_ALBUM_RANDOM_ID,
    MEDIA_ALBUM_STARRED_ID
)

/** A browse request whose server call failed (as opposed to returning an empty collection). */
private class BrowseFailure : Exception("The server request failed")
