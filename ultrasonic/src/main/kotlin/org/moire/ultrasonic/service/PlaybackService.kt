/*
 * PlaybackService.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.CachedDataSource
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.ArtworkBitmapLoader
import org.moire.ultrasonic.provider.UltrasonicAppWidgetProvider
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.stopForegroundRemoveNotification
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

@SuppressLint("UnsafeOptInUsageError")
class PlaybackService :
    MediaLibraryService(),
    KoinComponent,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private lateinit var player: Player
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private var equalizer: EqualizerController? = null
    private val activeServerProvider: ActiveServerProvider by inject()

    private lateinit var librarySessionCallback: MediaLibrarySessionCallback

    private var rxBusSubscription = CompositeDisposable()

    private var isStarted = false

    override fun onCreate() {
        Timber.i("onCreate called")
        super.onCreate()
        initializeSessionAndPlayer()
        setListener(MediaSessionServiceListener())
        instance = this
    }

    private fun getWakeModeFlag(): Int {
        return if (ActiveServerProvider.isOffline()) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK
    }

    override fun onDestroy() {
        Timber.i("onDestroy called")
        releasePlayerAndSession()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.i("Stopping the playback because we were swiped away")
        releasePlayerAndSession()
        super.onTaskRemoved(rootIntent)
    }

    private fun releasePlayerAndSession() {
        Timber.i("Releasing player and session")
        // Broadcast that the service is being shutdown
        RxBus.stopServiceCommandPublisher.onNext(Unit)

        // TODO Save the player state before shutdown
        player.removeListener(listener)
        player.release()
        mediaLibrarySession.release()
        rxBusSubscription.dispose()
        isStarted = false
        stopForegroundRemoveNotification()
        stopSelf()
        instance = null
    }

    private val resolver: ResolvingDataSource.Resolver = ResolvingDataSource.Resolver {
        val components = it.uri.toString().split('|')
        val id = components[0]
        val bitrate = components[1].toInt()
        val uri = getMusicService().getStreamUrl(id, bitrate, null)!!
        // AirSonic doesn't seem to stream correctly with the default
        // icy-metadata headers set by media3, so remove them.
        it.buildUpon().setUri(uri).setHttpRequestHeaders(emptyMap()).build()
    }

    private fun initializeSessionAndPlayer() {
        if (isStarted) return

        val desiredBackend = if (activeServerProvider.getActiveServer().jukeboxByDefault) {
            Timber.i("Jukebox enabled by default")
            MediaPlayerManager.PlayerBackend.JUKEBOX
        } else {
            MediaPlayerManager.PlayerBackend.LOCAL
        }

        player = createNewBackend(desiredBackend)

        actualBackend = desiredBackend

        // Create browser interface
        librarySessionCallback = MediaLibrarySessionCallback()

        // This will need to use the AutoCalls
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback)
            .setSessionActivity(getPendingIntentForContent())
            .setBitmapLoader(ArtworkBitmapLoader())
            .build()

        // Send custom layout to legacy session.
        mediaLibrarySession.setCustomLayout(librarySessionCallback.defaultCustomCommands)

        // Set a listener to update the API client when the active server has changed
        rxBusSubscription += RxBus.activeServerChangedObservable.subscribe {
            // Set the player wake mode
            (player as? ExoPlayer)?.setWakeMode(getWakeModeFlag())
        }

        // Set a listener to reset the ShuffleOrder
        rxBusSubscription += RxBus.shufflePlayObservable.subscribe { shuffle ->
            // This only applies for local playback
            val exo = if (player is ExoPlayer) {
                player as ExoPlayer
            } else {
                return@subscribe
            }
            val len = player.currentTimeline.windowCount

            Timber.i("Resetting shuffle order, isShuffled: %s", shuffle)

            // If disabling Shuffle return early
            if (!shuffle) {
                return@subscribe exo.setShuffleOrder(
                    ShuffleOrder.UnshuffledShuffleOrder(len)
                )
            }

            // Get the position of the current track in the unshuffled order
            val cur = player.currentMediaItemIndex
            val seed = System.currentTimeMillis()
            val random = Random(seed)

            val list = createShuffleListFromCurrentIndex(cur, len, random)
            Timber.i("New Shuffle order: %s", list.joinToString { it.toString() })
            exo.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(list, seed))
        }

        // Listen to the shutdown command
        rxBusSubscription += RxBus.shutdownCommandObservable.subscribe {
            Timber.i("Received destroy command via Rx")
            onDestroy()
        }

        player.addListener(listener)
        isStarted = true
    }

    private fun updateBackend(newBackend: MediaPlayerManager.PlayerBackend) {
        Timber.i("Switching player backends")
        // Remove old listeners
        player.removeListener(listener)
        player.release()

        player = createNewBackend(newBackend)

        // Add fresh listeners
        player.addListener(listener)

        mediaLibrarySession.player = player

        actualBackend = newBackend
    }

    private fun createNewBackend(newBackend: MediaPlayerManager.PlayerBackend): Player {
        return if (newBackend == MediaPlayerManager.PlayerBackend.JUKEBOX) {
            getJukeboxPlayer()
        } else {
            getLocalPlayer()
        }
    }

    private fun getJukeboxPlayer(): Player {
        return JukeboxMediaPlayer()
    }

    private fun getLocalPlayer(): Player {
        // Create a new plain OkHttpClient
        val builder = OkHttpClient.Builder()
        val client = builder.build()

        // Create the wrapped data sources:
        // CachedDataSource is the first. If it cannot find a file,
        // it will forward to ResolvingDataSource, which will create a URL through the resolver
        // and pass it onto the OkHttpDataSource.
        val okHttpDataSource = OkHttpDataSource.Factory(client)
        val resolvingDataSource = ResolvingDataSource.Factory(okHttpDataSource, resolver)
        val cacheDataSourceFactory: DataSource.Factory =
            CachedDataSource.Factory(resolvingDataSource)

        // Create the player
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(getAudioAttributes(), true)
            .setWakeMode(getWakeModeFlag())
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setSeekBackIncrementMs(Settings.seekInterval.toLong())
            .setSeekForwardIncrementMs(Settings.seekInterval.toLong())
            .build()

        // Enable audio offload
        if (Settings.useHwOffload) {
            player.enableOffload()
        }

        // Setup Equalizer
        equalizer = EqualizerController.create(player.audioSessionId)

        return player
    }

    private fun ExoPlayer.enableOffload() {
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences
                    .Builder()
                    .setAudioOffloadMode(
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    )
                    .build()
            ).build()
    }

    private fun createShuffleListFromCurrentIndex(
        currentIndex: Int,
        length: Int,
        random: Random
    ): IntArray {
        val list = IntArray(length) { it }

        // Shuffle the remaining items using a swapping algorithm
        for (i in currentIndex + 1 until length) {
            val swapIndex = (currentIndex + 1) + random.nextInt(i - currentIndex)
            val swapItem = list[i]
            list[i] = list[swapIndex]
            list[swapIndex] = swapItem
        }

        return list
    }

    private val listener: Player.Listener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            cacheNextSongs()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Since we cannot update the metadata of the media item after creation,
            // we cannot set change the rating on it
            // Therefore the track must be our source of truth
            val track = mediaItem?.toTrack()
            if (track != null) {
                updateCustomHeartButton(track.starred)
            }
            updateWidgetTrack(track)
            cacheNextSongs()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateWidgetPlayerState(isPlaying)
            cacheNextSongs()
        }
    }

    private fun updateCustomHeartButton(isHeart: Boolean) {
        librarySessionCallback.updateCustomHeartButton(mediaLibrarySession, isHeart)
    }

    private fun cacheNextSongs() {
        if (actualBackend == MediaPlayerManager.PlayerBackend.JUKEBOX) return
        Timber.d("PlaybackService caching the next songs")
        val nextSongs = Util.getPlayListFromTimeline(
            player.currentTimeline,
            player.shuffleModeEnabled,
            player.currentMediaItemIndex,
            Settings.preloadCount
        ).map {
            // These items should skip the MediaItemConverter cache.
            // The cache contains the controller's items, which may be modified (e.g. their rating)
            it.toTrack(false)
        }

        launch {
            DownloadService.download(nextSongs, isHighPriority = true)
        }
    }

    private fun getPendingIntentForContent(): PendingIntent {
        val intent = Intent(this, NavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        intent.action = Intent.ACTION_MAIN
        intent.putExtra(Constants.INTENT_SHOW_PLAYER, true)
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    private fun updateWidgetTrack(song: Track?) {
        val context = UApp.applicationContext()
        UltrasonicAppWidgetProvider.notifyTrackChange(context, song)
    }

    private fun updateWidgetPlayerState(isPlaying: Boolean) {
        val context = UApp.applicationContext()
        UltrasonicAppWidgetProvider.notifyPlayerStateChange(context, isPlaying)
    }

    private inner class MediaSessionServiceListener : Listener {

        /**
         * This method is only required to be implemented on Android 12 or above when an attempt is made
         * by a media controller to resume playback when the {@link MediaSessionService} is in the
         * background.
         */
        override fun onForegroundServiceStartNotAllowedException() {
            val notificationManagerCompat = NotificationManagerCompat.from(this@PlaybackService)
            Util.ensureNotificationChannel(
                id = NOTIFICATION_CHANNEL_ID,
                name = NOTIFICATION_CHANNEL_NAME,
                notificationManager = notificationManagerCompat
            )
            val pendingIntent =
                TaskStackBuilder.create(this@PlaybackService).run {
                    addNextIntent(Intent(this@PlaybackService, NavigationActivity::class.java))

                    val immutableFlag = FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
                    getPendingIntent(0, immutableFlag)
                }
            val builder =
                NotificationCompat.Builder(this@PlaybackService, NOTIFICATION_CHANNEL_ID)
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.media3_notification_small_icon)
                    .setContentTitle(getString(R.string.foreground_exception_title))
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            getString(R.string.foreground_exception_text)
                        )
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)

            Util.postNotificationIfPermitted(
                notificationManagerCompat,
                NOTIFICATION_ID,
                builder.build()
            )
        }
    }

    companion object {
        var actualBackend: MediaPlayerManager.PlayerBackend? = null

        private var desiredBackend: MediaPlayerManager.PlayerBackend? = null
        fun setBackend(playerBackend: MediaPlayerManager.PlayerBackend) {
            desiredBackend = playerBackend
            instance?.updateBackend(playerBackend)
        }

        var instance: PlaybackService? = null

        private const val NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic.error"
        private const val NOTIFICATION_CHANNEL_NAME = "Ultrasonic error messages"
        const val CUSTOM_COMMAND_TOGGLE_HEART_ON =
            "org.moire.ultrasonic.HEART_ON"
        const val CUSTOM_COMMAND_TOGGLE_HEART_OFF =
            "org.moire.ultrasonic.HEART_OFF"
        const val CUSTOM_COMMAND_SHUFFLE =
            "org.moire.ultrasonic.SHUFFLE"
        const val CUSTOM_COMMAND_PLACEHOLDER =
            "org.moire.ultrasonic.PLACEHOLDER"
        const val CUSTOM_COMMAND_REPEAT_MODE =
            "org.moire.ultrasonic.REPEAT_MODE"
        private const val NOTIFICATION_ID = 3009
    }
}
