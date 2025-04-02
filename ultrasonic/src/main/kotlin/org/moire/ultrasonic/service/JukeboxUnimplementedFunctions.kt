/*
 * JukeboxUnimplementedFunctions.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.annotation.SuppressLint
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks

/**
 * This class helps to hide the unused (thus unimplemented) functions
 * of the crowded Player interface, so the JukeboxMediaPlayer class can be a bit clearer.
 */
@Suppress("TooManyFunctions", "DeprecatedCallableAddReplaceWith")
@SuppressLint("UnsafeOptInUsageError")
abstract class JukeboxUnimplementedFunctions : Player {

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        TODO("Not yet implemented")
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        TODO("Not yet implemented")
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun replaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: MutableList<MediaItem>
    ) {
        TODO("Not yet implemented")
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        TODO("Not yet implemented")
    }

    override fun seekToDefaultPosition() {
        TODO("Not yet implemented")
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun hasPreviousMediaItem(): Boolean {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun seekToPreviousWindow() {
        TODO("Not yet implemented")
    }

    override fun seekToPreviousMediaItem() {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun hasNext(): Boolean {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun hasNextWindow(): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasNextMediaItem(): Boolean {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun next() {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun seekToNextWindow() {
        TODO("Not yet implemented")
    }

    override fun seekToNextMediaItem() {
        TODO("Not yet implemented")
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        TODO("Not yet implemented")
    }

    override fun setPlaybackSpeed(speed: Float) {
        TODO("Not yet implemented")
    }
    override fun getCurrentTracks(): Tracks {
        // TODO Dummy information is returned for now, this seems to work
        return Tracks.EMPTY
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        TODO("Not yet implemented")
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        TODO("Not yet implemented")
    }

    override fun getCurrentManifest(): Any? {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun getCurrentWindowIndex(): Int {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun getNextWindowIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getNextMediaItemIndex(): Int {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun getPreviousWindowIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getPreviousMediaItemIndex(): Int {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowDynamic(): Boolean {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowLive(): Boolean {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowSeekable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurface() {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        TODO("Not yet implemented")
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }
}
