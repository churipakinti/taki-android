/*
 * ShareCardViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** How long a card waits for the cover before it is drawn with the Taki fallback tile. */
internal const val SHARE_ARTWORK_TIMEOUT_MS = 6_000L

/** A second tap on Share within this window is the same tap (no second chooser). */
internal const val SHARE_LAUNCH_GUARD_MS = 1_500L

/** Longest side requested for the cover: the card draws it at 780 px, so this is never upscaled. */
private const val ARTWORK_REQUEST_PX = 1_000

/**
 * A stable snapshot of the track the user chose to share, taken at the moment Share is tapped.
 * Everything the card needs is in here, so a track change while the card is generating or the
 * preview is open can never mix one track's title with another's artwork (issue #20).
 */
data class ShareCardRequest(
    val trackId: String?,
    val content: ShareCardContent,
    /** Opaque Coil model of the cover (`CoverArtRequest`), or null for the fallback tile. */
    val artworkModel: Any?,
)

sealed interface ShareCardUiState {
    data object Hidden : ShareCardUiState
    data class Generating(val request: ShareCardRequest) : ShareCardUiState
    data class Ready(val request: ShareCardRequest, val preview: Bitmap, val file: File) : ShareCardUiState
    data class Error(val request: ShareCardRequest) : ShareCardUiState
}

/**
 * Owns the share flow's state, deliberately separate from playback: it reads a snapshot, never
 * the player, so opening, generating or dismissing a card cannot seek, pause, prepare or touch
 * the queue. Generation runs off the main thread; every run gets a generation number and a stale
 * result (the sheet was closed or reopened meanwhile) is dropped instead of replacing the
 * preview. The rendered bitmap is both the preview and the exported PNG, so the two cannot
 * diverge.
 */
class ShareCardViewModel(
    private val store: ShareCardStore,
    private val loadArtwork: suspend (Any?) -> Bitmap?,
    private val render: (ShareCardContent, Bitmap?) -> Bitmap = ShareCardRenderer()::render,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    private val _state = MutableStateFlow<ShareCardUiState>(ShareCardUiState.Hidden)
    val state: StateFlow<ShareCardUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var generation = 0
    private var lastLaunchAt: Long? = null

    /** Opens the preview for [request]. A repeated tap while a card is already open is ignored. */
    fun open(request: ShareCardRequest) {
        if (_state.value !is ShareCardUiState.Hidden) return
        lastLaunchAt = null
        generate(request)
    }

    fun retry() {
        val request = (_state.value as? ShareCardUiState.Error)?.request ?: return
        generate(request)
    }

    fun dismiss() {
        generation++
        job?.cancel()
        job = null
        _state.value = ShareCardUiState.Hidden
    }

    /**
     * The file to hand to the Sharesheet, or null when there is nothing ready or this is a
     * duplicate tap of a launch that just happened.
     */
    fun claimShare(): File? {
        val ready = _state.value as? ShareCardUiState.Ready ?: return null
        val now = clock()
        val previous = lastLaunchAt
        if (previous != null && now - previous < SHARE_LAUNCH_GUARD_MS) return null
        lastLaunchAt = now
        return ready.file
    }

    private fun generate(request: ShareCardRequest) {
        job?.cancel()
        val mine = ++generation
        _state.value = ShareCardUiState.Generating(request)
        job = viewModelScope.launch {
            val outcome = try {
                withContext(workDispatcher) {
                    val artwork = withTimeoutOrNull(SHARE_ARTWORK_TIMEOUT_MS) {
                        runCatching { loadArtwork(request.artworkModel) }.getOrNull()
                    }
                    val bitmap = render(request.content, artwork)
                    ShareCardUiState.Ready(request, bitmap, store.write(bitmap))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                // Includes OutOfMemoryError: a failed card must never take Now Playing down.
                Timber.w(failure, "Share card generation failed")
                ShareCardUiState.Error(request)
            }
            if (mine == generation) _state.value = outcome
        }
    }

    override fun onCleared() {
        store.prune()
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ShareCardViewModel(
                store = ShareCardStore(appContext.cacheDir),
                loadArtwork = { model -> loadShareArtwork(appContext, model) },
            ) as T
    }
}

/**
 * The cover through the app's own image pipeline: the same `CoverArtRequest` Now Playing already
 * shows, so it is normally a disk/memory cache hit and costs no extra request. Software bitmap
 * (the renderer crops and rescales it).
 */
suspend fun loadShareArtwork(context: Context, model: Any?): Bitmap? {
    if (model == null) return null
    val request = ImageRequest.Builder(context)
        .data(model)
        .size(Size(ARTWORK_REQUEST_PX, ARTWORK_REQUEST_PX))
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    val bitmap = result.image.toBitmap()
    return if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
}
