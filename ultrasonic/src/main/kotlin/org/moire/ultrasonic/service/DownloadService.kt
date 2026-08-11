/*
 * DownloadService.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadState.Companion.isFinalState
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.FileUtil.getCompleteFile
import org.moire.ultrasonic.util.FileUtil.getPartialFile
import org.moire.ultrasonic.util.FileUtil.getPinnedFile
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.stopForegroundRemoveNotification
import timber.log.Timber

private const val NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic"
private const val NOTIFICATION_CHANNEL_NAME = "Ultrasonic background service"
private const val NOTIFICATION_ID = 3033

private const val CHECK_INTERVAL = 5000L

/**
 * Android Foreground service which is used to download tracks even when the app is not visible
 *
 * "A foreground service is a service that the user is
 * actively aware of and isn’t a candidate for the system to kill when low on memory."
 *
 */
class DownloadService :
    Service(),
    KoinComponent {
    private var scope: CoroutineScope? = null
    private val storageMonitor: ExternalStorageMonitor by inject()
    private val cacheCleaner: CacheCleaner by inject()
    private val binder: IBinder = SimpleServiceBinder(this)

    private var isInForeground = false
    private var wifiLock: WifiManager.WifiLock? = null
    private var isShuttingDown = false
    private var retrying = false

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        // Create Coroutine lifecycle scope. We use a SupervisorJob(), otherwise the failure of one
        // would mean the failure of all jobs!
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val notificationManagerCompat = NotificationManagerCompat.from(this)

        // Create Notification Channel
        Util.ensureNotificationChannel(
            id = NOTIFICATION_CHANNEL_ID,
            name = NOTIFICATION_CHANNEL_NAME,
            importance = 2,
            notificationManager = notificationManagerCompat
        )
        updateNotification()

        if (wifiLock == null) {
            wifiLock = Util.createWifiLock(toString())
            wifiLock?.acquire()
        }

        startFuture?.set(this)
        Timber.i("DownloadService created")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        startFuture = null

        isShuttingDown = true
        isInForeground = false
        stopForegroundRemoveNotification()

        wifiLock?.release()
        wifiLock = null

        clearDownloads()
        observableDownloads.value = listOf()

        scope?.cancel()
        scope = null

        Timber.i("DownloadService destroyed")
    }

    @Synchronized
    fun processNextTracks() {
        retrying = false
        if (
            !Util.hasUsableNetwork() ||
            !Util.isExternalStoragePresent() ||
            !storageMonitor.isExternalStorageAvailable
        ) {
            retryProcessNextTracks()
            return
        }

        Timber.v("DownloadService processNextTracks checking downloads")
        var listChanged = false

        // Fill up active List with waiting tasks
        while (activeDownloads.size < Settings.PARALLEL_DOWNLOADS && downloadQueue.peek() != null) {
            // Use poll() instead of remove() which throws an Exception if there is no element.
            val track: DownloadableTrack = downloadQueue.poll() ?: continue

            val downloadTask = DownloadTask(track, scope!!, ::downloadStateChangedCallback)
            activeDownloads[track.id] = downloadTask

            downloadTask.start()
            listChanged = true
        }

        // Stop Executor service when done downloading
        if (activeDownloads.isEmpty()) {
            cacheCleaner.cleanSpace()
            stopSelf()
        }

        if (listChanged) {
            updateLiveData()
        }
    }

    private fun retryProcessNextTracks() {
        Timber.i("Scheduling retry to process next tracks")
        if (isShuttingDown || retrying) return
        retrying = true
        Handler(Looper.getMainLooper()).postDelayed(
            { if (retrying) processNextTracks() },
            CHECK_INTERVAL
        )
    }

    private fun downloadStateChangedCallback(
        item: DownloadableTrack,
        downloadState: DownloadState,
        progress: Int?
    ) {
        postState(item.track, downloadState, progress)

        if (downloadState.isFinalState()) {
            activeDownloads.remove(item.id)
            processNextTracks()
        }

        when (downloadState) {
            DownloadState.FAILED -> {
                downloadQueue.remove(item)
                failedList[item.id] = item
            }

            DownloadState.RETRYING -> {
                item.tryCount++
                downloadQueue.add(item)
            }

            else -> {}
        }
    }

    // We should use a single notification builder, otherwise the notification may not be updated
    // Set some values that never change
    private val notificationBuilder: NotificationCompat.Builder by lazy {
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ultrasonic)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(Util.getPendingIntentToShowPlayer(this))
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun updateNotification() {
        val notification = buildForegroundNotification()

        if (isInForeground) {
            val manager = NotificationManagerCompat.from(this)
            Util.postNotificationIfPermitted(manager, NOTIFICATION_ID, notification)
            Timber.v("Updated notification")
        } else {
            startForeground(NOTIFICATION_ID, notification)
            isInForeground = true
            Timber.v("Created Foreground notification")
        }
    }

    /**
     * This method builds a notification, reusing the Notification Builder if possible
     */
    @Suppress("SpreadOperator")
    private fun buildForegroundNotification(): Notification {
        notificationBuilder.setContentTitle(getString(R.string.notification_downloading_title))
        return notificationBuilder.build()
    }

    @Suppress("MagicNumber", "NestedBlockDepth", "TooManyFunctions")
    companion object {

        private var startFuture: SettableFuture<DownloadService>? = null

        private val downloadQueue = PriorityBlockingQueue<DownloadableTrack>()
        private val activeDownloads = ConcurrentHashMap<String, DownloadTask>()
        private val failedList = ConcurrentHashMap<String, DownloadableTrack>()

        // The generic list models expect a LiveData, so even though we are using Rx for many events
        // surrounding playback the list of Downloads is published as LiveData.
        val observableDownloads = MutableLiveData<List<Track>>()

        private var backgroundPriorityCounter = 100

        // download() is only @Synchronized around firing off the launch{} below, not around the
        // actual check-then-add sequence in downloadAsync() - two overlapping calls (double-tap
        // download, or triggered from two screens) could both pass the "not already queued/
        // active" check before either inserted, queuing the same track twice and racing two
        // DownloadTasks writing the same file. This mutex serializes that whole sequence instead.
        // See docs/AUDITORIA_FUNCIONAMIENTO_INTERNO.md.
        private val downloadQueueMutex = Mutex()

        @Synchronized
        fun download(
            tracks: List<Track>,
            save: Boolean = false,
            isHighPriority: Boolean = false,
            updateSaveFlag: Boolean = false
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                downloadAsync(tracks, save, isHighPriority, updateSaveFlag)
            }
        }

        suspend fun downloadAsync(
            tracks: List<Track>,
            save: Boolean = false,
            isHighPriority: Boolean = false,
            updateSaveFlag: Boolean = false
        ) {
            withContext(Dispatchers.IO) {
                downloadQueueMutex.withLock {
                    // Remove tracks which are already downloaded and update the save flag
                    // if needed
                    var filteredTracks = if (updateSaveFlag) {
                        setSaveFlagForTracks(save, tracks)
                    } else {
                        removeDownloadedTracksFromList(tracks)
                    }

                    // Remove tracks which are currently downloading
                    filteredTracks = filteredTracks.filter {
                        !downloadQueue.any { i -> i.id == it.id } &&
                            !activeDownloads.containsKey(it.id)
                    }

                    // The remaining tracks should be added to the download queue
                    // By using the counter we ensure that the songs are added in the correct order
                    var priority = 0
                    val tracksToDownload =
                        filteredTracks.map {
                            DownloadableTrack(
                                it,
                                save,
                                0,
                                if (isHighPriority) priority++ else backgroundPriorityCounter++
                            )
                        }

                    if (tracksToDownload.isNotEmpty()) {
                        downloadQueue.addAll(tracksToDownload)
                        tracksToDownload.forEach { postState(it.track, DownloadState.QUEUED) }
                        processNextTracksOnService()
                    }
                }
            }
        }

        private fun removeDownloadedTracksFromList(tracks: List<Track>): List<Track> {
            return tracks.filter { track ->
                val pinnedFile = Storage.getFromPath(track.getPinnedFile())
                val completeFile = Storage.getFromPath(track.getCompleteFile())

                completeFile?.let {
                    postState(track, DownloadState.DONE)
                    return@filter false
                }
                pinnedFile?.let {
                    postState(track, DownloadState.PINNED)
                    return@filter false
                }
                true
            }
        }

        private fun updateLiveData() {
            val temp: MutableList<Track> = ArrayList()
            temp.addAll(activeDownloads.values.map { it.downloadTrack.track })
            temp.addAll(downloadQueue.map { x -> x.track })
            observableDownloads.postValue(temp.distinct().sorted())
        }

        fun clearDownloads() {
            // Clear the pending queue
            while (!downloadQueue.isEmpty()) {
                postState(downloadQueue.remove().track, DownloadState.IDLE)
            }
            // Cancel all active downloads
            for (download in activeDownloads) {
                download.value.cancel()
            }
            activeDownloads.clear()
            updateLiveData()
        }

        private fun setSaveFlagForTracks(shouldPin: Boolean, tracks: List<Track>): List<Track> {
            // Walk through the tracks. If a track is pinned or complete and needs to be changed
            // to the other state, rename it, but don't return it, thereby excluding it from
            // further processing.
            // If it is neither pinned nor saved, return it, so that it can be processed.
            val filteredTracks: List<Track> = tracks.map { track ->
                val pinnedFile = Storage.getFromPath(track.getPinnedFile())
                val completeFile = Storage.getFromPath(track.getCompleteFile())

                if (shouldPin) {
                    pinnedFile?.let {
                        null
                    }
                    completeFile?.let {
                        Storage.renameOrDeleteIfAlreadyExists(it, track.getPinnedFile())
                        postState(track, DownloadState.PINNED)
                        null
                    }
                } else {
                    completeFile?.let {
                        null
                    }
                    pinnedFile?.let {
                        Storage.renameOrDeleteIfAlreadyExists(it, track.getCompleteFile())
                        postState(track, DownloadState.DONE)
                        null
                    }
                }
                track
            }

            // Update Pinned flag of items in progress
            downloadQueue.filter { item -> tracks.any { it.id == item.id } }
                .forEach { it.pinned = shouldPin }
            tracks.forEach {
                activeDownloads[it.id]?.downloadTrack?.pinned = shouldPin
            }
            tracks.forEach {
                failedList[it.id]?.pinned = shouldPin
            }
            return filteredTracks
        }

        fun requestStop() {
            val context = UApp.applicationContext()
            val intent = Intent(context, DownloadService::class.java)
            context.stopService(intent)
            failedList.clear()
        }

        private fun delete(track: Track) {
            CoroutineScope(Dispatchers.IO).launch {
                deleteAsync(track)
            }
        }

        private suspend fun deleteAsync(track: Track) {
            withContext(Dispatchers.IO) {
                downloadQueue.get(track.id)?.let { downloadQueue.remove(it) }
                failedList[track.id]?.let { downloadQueue.remove(it) }
                cancelDownload(track)

                Storage.delete(track.getPartialFile())
                Storage.delete(track.getCompleteFile())
                Storage.delete(track.getPinnedFile())
                postState(track, DownloadState.IDLE)
                val cacheCleaner: CacheCleaner by inject(CacheCleaner::class.java)
                cacheCleaner.cleanDatabaseSelective(track)
                Util.scanMedia(track.getPinnedFile())
            }
        }

        @Synchronized
        fun unpin(tracks: List<Track>) {
            tracks.forEach(::unpin)
        }

        @Synchronized
        fun delete(tracks: List<Track>) {
            tracks.forEach(::delete)
        }

        suspend fun unpinAsync(tracks: List<Track>) {
            tracks.forEach { unpinAsync(it) }
        }

        suspend fun deleteAsync(tracks: List<Track>) {
            tracks.forEach { deleteAsync(it) }
        }

        private fun unpin(track: Track) {
            CoroutineScope(Dispatchers.IO).launch {
                unpinAsync(track)
            }
        }

        private suspend fun unpinAsync(track: Track) {
            withContext(Dispatchers.IO) {
                // Update Pinned flag of items in progress
                downloadQueue.get(track.id)?.pinned = false
                activeDownloads[track.id]?.downloadTrack?.pinned = false
                failedList[track.id]?.pinned = false

                val pinnedFile = track.getPinnedFile()
                if (!Storage.isPathExists(pinnedFile)) return@withContext
                val file = Storage.getFromPath(track.getPinnedFile()) ?: return@withContext
                try {
                    Storage.rename(file, track.getCompleteFile())
                } catch (ignored: FileAlreadyExistsException) {
                    // Play console has revealed a crash when for some reason both files exist
                    Storage.delete(file.path)
                }
                postState(track, DownloadState.DONE)
            }
        }

        @Suppress("ReturnCount")
        fun getDownloadState(track: Track): DownloadState {
            if (activeDownloads.contains(track.id)) return DownloadState.QUEUED
            if (downloadQueue.contains(track.id)) return DownloadState.QUEUED

            val downloadableTrack = activeDownloads[track.id]?.downloadTrack
            if (downloadableTrack != null) {
                if (downloadableTrack.tryCount > 0) return DownloadState.RETRYING
                return DownloadState.DOWNLOADING
            }
            if (failedList[track.id] != null) return DownloadState.FAILED
            if (Storage.isPathExists(track.getCompleteFile())) return DownloadState.DONE
            if (Storage.isPathExists(track.getPinnedFile())) return DownloadState.PINNED
            return DownloadState.IDLE
        }

        private fun processNextTracksOnService() {
            val serviceFuture = startFuture ?: requestStart()
            serviceFuture.addListener({
                val service = serviceFuture.get()
                service.processNextTracks()
                Timber.i("DownloadService processNextTracks executed.")
            }, MoreExecutors.directExecutor())
        }

        private fun cancelDownload(track: Track) {
            activeDownloads[track.id]?.cancel()
        }

        private fun postState(track: Track, state: DownloadState, progress: Int? = null) {
            RxBus.trackDownloadStatePublisher.onNext(
                RxBus.TrackDownloadState(
                    track.id,
                    state,
                    progress
                )
            )
        }

        private fun requestStart(): ListenableFuture<DownloadService> {
            val future = SettableFuture.create<DownloadService>()
            startFuture = future
            startService()
            return future
        }

        private fun startService() {
            try {
                val context = UApp.applicationContext()
                val intent = Intent(context, DownloadService::class.java)
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Timber.w(e, "Failed to start download service: the app is in the background")
            }
        }

        fun PriorityBlockingQueue<DownloadableTrack>.get(id: String): DownloadableTrack? {
            for (el in this) {
                if (el.id == id) return el
            }
            return null
        }

        fun PriorityBlockingQueue<DownloadableTrack>.contains(id: String): Boolean =
            (this.get(id) != null)
    }
}

class SimpleServiceBinder<S>(val service: S) : Binder()
