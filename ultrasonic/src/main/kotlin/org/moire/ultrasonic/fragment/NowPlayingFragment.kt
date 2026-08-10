/*
 * NowPlayingFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.reactivex.rxjava3.disposables.Disposable
import java.lang.Exception
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.scope.ScopeFragment
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.getNotificationImageSize
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

/**
 * Contains the mini-now playing information box displayed at the bottom of the screen
 */
class NowPlayingFragment : ScopeFragment() {

    private var downX = 0f
    private var downY = 0f

    private var playButton: MaterialButton? = null
    private var previousButton: MaterialButton? = null
    private var nextButton: MaterialButton? = null
    private var nowPlayingAlbumArtImage: ImageView? = null
    private var nowPlayingTrack: TextView? = null
    private var nowPlayingArtist: TextView? = null
    private var progressIndicator: LinearProgressIndicator? = null
    private var progressJob: Job? = null

    private var rxBusSubscription: Disposable? = null
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.now_playing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        playButton = view.findViewById(R.id.now_playing_control_play)
        previousButton = view.findViewById(R.id.now_playing_control_previous)
        nextButton = view.findViewById(R.id.now_playing_control_next)
        nowPlayingAlbumArtImage = view.findViewById(R.id.now_playing_image)
        nowPlayingTrack = view.findViewById(R.id.now_playing_title)
        nowPlayingTrack?.isSelected = true
        nowPlayingArtist = view.findViewById(R.id.now_playing_artist)
        progressIndicator = view.findViewById(R.id.now_playing_progress)
        rxBusSubscription = RxBus.playerStateObservable.subscribe { update() }
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    override fun onDestroyView() {
        progressJob?.cancel()
        rxBusSubscription?.dispose()
        progressIndicator = null
        super.onDestroyView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun update() {
        try {
            if (mediaPlayerManager.isPlaying) {
                playButton!!.setIconResource(R.drawable.media_pause_shadow)
            } else {
                playButton!!.setIconResource(R.drawable.media_start_shadow)
            }
            restartProgressUpdates()

            val file = mediaPlayerManager.currentMediaItem?.toTrack()

            if (file != null) {
                val title = file.title
                val artist = file.artist
                val size = getNotificationImageSize(requireContext())

                imageLoaderProvider.executeOn {
                    it.loadImage(
                        nowPlayingAlbumArtImage,
                        file,
                        false,
                        size
                    )
                }

                nowPlayingTrack!!.text = title
                nowPlayingArtist!!.text = artist

                nowPlayingAlbumArtImage!!.setOnClickListener {
                    val id3 = Settings.id3TagsEnabledOnline
                    val action = NavigationGraphDirections.toTrackCollection(
                        isAlbum = id3,
                        id = if (id3) file.albumId else file.parent,
                        name = file.album
                    )
                    findNavController().navigate(action)
                }
            }

            requireView().setOnTouchListener { _: View?, event: MotionEvent ->
                handleOnTouch(event)
            }

            // This empty onClickListener is necessary for the onTouchListener to work
            requireView().setOnClickListener { }
            playButton!!.setOnClickListener { mediaPlayerManager.togglePlayPause() }
            previousButton!!.setOnClickListener { mediaPlayerManager.seekToPrevious() }
            nextButton!!.setOnClickListener { mediaPlayerManager.seekToNext() }
        } catch (all: Exception) {
            Timber.w(all, "Failed to get notification cover art")
        }
    }

    private fun restartProgressUpdates() {
        progressJob?.cancel()
        updateProgress()
        if (!mediaPlayerManager.isPlaying) return
        progressJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && mediaPlayerManager.isPlaying) {
                delay(PROGRESS_UPDATE_INTERVAL_MS)
                updateProgress()
            }
        }
    }

    private fun updateProgress() {
        val duration = mediaPlayerManager.playerDuration
        val indicator = progressIndicator ?: return
        indicator.isVisible = duration > 0
        if (duration <= 0) return
        val position = mediaPlayerManager.playerPosition.coerceIn(0, duration)
        indicator.setProgressCompat((position.toLong() * PROGRESS_MAX / duration).toInt(), true)
    }

    private fun handleOnTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }

            MotionEvent.ACTION_UP -> {
                val upX = event.x
                val upY = event.y
                val deltaX = downX - upX
                val deltaY = downY - upY

                if (abs(deltaX) > MIN_DISTANCE) {
                    // left or right
                    if (deltaX < 0) {
                        mediaPlayerManager.seekToPrevious()
                    }
                    if (deltaX > 0) {
                        mediaPlayerManager.seekToNext()
                    }
                } else if (abs(deltaY) > MIN_DISTANCE) {
                    // Swiping up/down no longer dismisses the bar - it should stay visible
                    // whenever something is loaded, matching the "always visible" mini player
                    // behavior of reference music apps.
                } else {
                    Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                        .navigate(R.id.playerFragment)
                }
            }
        }
        return false
    }

    companion object {
        private const val MIN_DISTANCE = 30
        private const val PROGRESS_MAX = 1000
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1000L
    }
}
