package org.moire.ultrasonic.service

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.domain.Track

class RxBus {

    /**
     * IMPORTANT: methods like .delay() or .throttle() will implicitly change the thread to the
     * RxComputationScheduler. Always use the function call with the additional arguments of the
     * desired scheduler
     **/
    companion object {

        fun mainThread(): Scheduler = AndroidSchedulers.mainThread()

        val shufflePlayPublisher: PublishSubject<ShufflePlay> =
            PublishSubject.create()
        val shufflePlayObservable: Observable<ShufflePlay> =
            shufflePlayPublisher

        var activeServerChangingPublisher: PublishSubject<Int> =
            PublishSubject.create()

        // Subscribers should be called synchronously, not on another thread
        var activeServerChangingObservable: Observable<Int> =
            activeServerChangingPublisher

        var activeServerChangedPublisher: PublishSubject<ServerSetting> =
            PublishSubject.create()
        var activeServerChangedObservable: Observable<ServerSetting> =
            activeServerChangedPublisher.observeOn(mainThread())

        val musicFolderChangedEventPublisher: PublishSubject<Folder> =
            PublishSubject.create()
        val musicFolderChangedEventObservable: Observable<Folder> =
            musicFolderChangedEventPublisher.observeOn(mainThread())

        val playerStatePublisher: PublishSubject<StateWithTrack> =
            PublishSubject.create()
        val playerStateObservable: Observable<StateWithTrack> =
            playerStatePublisher
                .replay(1)
                .autoConnect(0)
        val throttledPlayerStateObservable: Observable<StateWithTrack> =
            playerStatePublisher
                .replay(1)
                .autoConnect(0)
                // Need to specify thread, see comment at beginning
                .throttleLatest(300, TimeUnit.MILLISECONDS, mainThread())

        val playlistPublisher: PublishSubject<List<Track>> =
            PublishSubject.create()
        val playlistObservable: Observable<List<Track>> =
            playlistPublisher
                .replay(1)
                .autoConnect(0)
        val throttledPlaylistObservable: Observable<List<Track>> =
            playlistPublisher
                .replay(1)
                .autoConnect(0)
                // Need to specify thread, see comment at beginning
                .throttleLatest(300, TimeUnit.MILLISECONDS, mainThread())

        // Whether a large queue is currently being built in chunks (see
        // MediaPlayerManager.withTimelinePublishSuppressed). UI can use this to show a loading
        // state instead of appearing frozen while it waits for playlistObservable/
        // playerStateObservable to fire.
        val queueLoadingPublisher: PublishSubject<Boolean> =
            PublishSubject.create()
        val queueLoadingObservable: Observable<Boolean> =
            queueLoadingPublisher
                .replay(1)
                .autoConnect(0)

        val sleepTimerStatePublisher: PublishSubject<SleepTimerState> =
            PublishSubject.create()
        val sleepTimerStateObservable: Observable<SleepTimerState> =
            sleepTimerStatePublisher
                .replay(1)
                .autoConnect(0)

        val trackDownloadStatePublisher: PublishSubject<TrackDownloadState> =
            PublishSubject.create()
        val trackDownloadStateObservable: Observable<TrackDownloadState> =
            trackDownloadStatePublisher.observeOn(mainThread())

        // Sends a RatingUpdate which was just triggered by the user
        val ratingSubmitter: PublishSubject<RatingUpdate> =
            PublishSubject.create()
        val ratingSubmitterObservable: Observable<RatingUpdate> =
            ratingSubmitter

        // Sends a RatingUpdate which was successfully submitted to the server or database
        val ratingPublished: PublishSubject<RatingUpdate> =
            PublishSubject.create()
        val ratingPublishedObservable: Observable<RatingUpdate> =
            ratingPublished

        // Commands
        val dismissNowPlayingCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val dismissNowPlayingCommandObservable: Observable<Unit> =
            dismissNowPlayingCommandPublisher.observeOn(mainThread())

        val createServiceCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val createServiceCommandObservable: Observable<Unit> =
            createServiceCommandPublisher.observeOn(mainThread())

        val stopServiceCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val stopServiceCommandObservable: Observable<Unit> =
            stopServiceCommandPublisher.observeOn(mainThread())

        val shutdownCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val shutdownCommandObservable: Observable<Unit> =
            shutdownCommandPublisher.observeOn(mainThread())
    }

    data class StateWithTrack(
        val track: Track?,
        val index: Int = -1,
        val isPlaying: Boolean = false,
        val state: Int
    )

    data class TrackDownloadState(val id: String, val state: DownloadState, val progress: Int?)

    /**
     * Shuffle-mode change request.
     *
     * [reshuffleAll] distinguishes a whole-queue shuffle (a fresh Album / collection "Shuffle",
     * where the first played track must be random) from every other way shuffle gets enabled
     * (Now Playing toggle, session restore), which keep the current track pinned and only
     * shuffle what comes after it. See PlaybackService's shuffle-order reset.
     */
    data class ShufflePlay(val enabled: Boolean, val reshuffleAll: Boolean = false)

    data class Folder(val id: String?)
}

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    this.add(disposable)
}
