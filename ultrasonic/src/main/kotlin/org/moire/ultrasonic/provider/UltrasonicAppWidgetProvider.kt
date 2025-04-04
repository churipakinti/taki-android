/*
 * UltrasonicAppWidgetProvider.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.provider

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.receiver.UltrasonicIntentReceiver
import org.moire.ultrasonic.util.Constants
import timber.log.Timber

/**
 * Widget Provider for the Ultrasonic Widget
 */
@Suppress("MagicNumber")
open class UltrasonicAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateTrackAndState(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateTrackAndState(context!!, intArrayOf(appWidgetId))
    }

    companion object {

        private var isPlaying: Boolean = false
        private var track: Track? = null

        /**
         * Pushes the current track details to the widgets
         */
        fun notifyTrackChange(context: Context, currentSong: Track?) {
            this.track = currentSong
            if (hasInstances(context)) {
                // The widget won't update correctly if only the track or the state is updated
                updateTrackAndState(context, null)
            }
        }

        /**
         * Pushes the current player state to the widgets
         */
        fun notifyPlayerStateChange(context: Context, isPlaying: Boolean) {
            this.isPlaying = isPlaying
            if (hasInstances(context)) {
                // The widget won't update correctly if only the track or the state is updated
                updateTrackAndState(context, null)
            }
        }

        /**
         * Send the track and the player state to the widgets
         */
        private fun updateTrackAndState(context: Context, appWidgetIds: IntArray? = null) {
            pushUpdate(context, appWidgetIds) {
                updateTrack(context, it, track)
                updatePlayerState(it, isPlaying)
            }
        }

        /**
         * Iterates through the instances of the widget, and pushes the update to them
         */
        private fun pushUpdate(
            context: Context,
            appWidgetIds: IntArray? = null,
            update: (RemoteViews) -> Unit
        ) {
            val manager = AppWidgetManager.getInstance(context)
            if (manager != null) {
                val widgetIds =
                    appWidgetIds ?: manager.getAppWidgetIds(
                        ComponentName(
                            context,
                            UltrasonicAppWidgetProvider::class.java
                        )
                    )

                widgetIds.forEach {
                    val widgetOptions = manager.getAppWidgetOptions(it)
                    val minHeight =
                        widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    val minWidth =
                        widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val layoutId = getLayout(minHeight, minWidth)
                    val views = RemoteViews(context.packageName, layoutId)
                    update(views)
                    manager.updateAppWidget(it, views)
                }
            }
        }

        /**
         * Computes the layout to be displayed for the widget height
         */
        private fun getLayout(height: Int, width: Int): Int {
            val portrait = (width / height.toFloat()) < 1.8F
            if (portrait) return R.layout.appwidget_portrait
            if (height > 100) return R.layout.appwidget_landscape
            return R.layout.appwidget_landscape_small
        }

        /**
         * Check against [AppWidgetManager] if there are any instances of this widget.
         */
        private fun hasInstances(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (manager != null) {
                val appWidgetIds = manager.getAppWidgetIds(
                    ComponentName(
                        context,
                        UltrasonicAppWidgetProvider::class.java
                    )
                )
                return appWidgetIds.isNotEmpty()
            }
            return false
        }

        /**
         * Update Player state in widgets
         */
        private fun updatePlayerState(views: RemoteViews, isPlaying: Boolean) {
            if (isPlaying) {
                views.setImageViewResource(R.id.control_play, R.drawable.media_pause)
            } else {
                views.setImageViewResource(R.id.control_play, R.drawable.media_start)
            }
        }

        /**
         * Update Track details in widgets
         */
        private fun updateTrack(context: Context, views: RemoteViews, currentSong: Track?) {
            Timber.d("Updating Widget")
            val res = context.resources
            val title = currentSong?.title
            val artist = currentSong?.artist
            val album = currentSong?.album
            var errorState: CharSequence? = null

            // Show error message?
            val status = Environment.getExternalStorageState()
            if (status == Environment.MEDIA_SHARED || status == Environment.MEDIA_UNMOUNTED) {
                errorState = res.getText(R.string.widget_sdcard_busy)
            } else if (status == Environment.MEDIA_REMOVED) {
                errorState = res.getText(R.string.widget_sdcard_missing)
            } else if (currentSong == null) {
                errorState = res.getText(R.string.widget_initial_text)
            }
            if (errorState != null) {
                // Show error state to user
                views.setViewVisibility(R.id.title, GONE)
                views.setViewVisibility(R.id.album, GONE)
                views.setTextViewText(R.id.artist, res.getText(R.string.widget_initial_text))
                views.setBoolean(R.id.artist, "setSingleLine", false)
                views.setImageViewResource(R.id.appwidget_coverart, R.drawable.unknown_album)
            } else {
                // No error, so show normal titles
                views.setTextViewText(R.id.title, title)
                views.setTextViewText(R.id.artist, artist)
                views.setTextViewText(R.id.album, album)
                views.setBoolean(R.id.artist, "setSingleLine", true)
                views.setViewVisibility(R.id.title, VISIBLE)
                views.setViewVisibility(R.id.album, VISIBLE)
            }
            // Set the cover art
            try {
                val uri = AlbumArtContentProvider.mapArtworkToContentProviderUri(currentSong)
                views.setImageViewUri(R.id.appwidget_coverart, uri)
            } catch (all: Exception) {
                Timber.e(all, "Failed to load cover art")
                views.setImageViewResource(R.id.appwidget_coverart, R.drawable.unknown_album)
            }

            // Link actions buttons to intents
            linkButtons(context, views, currentSong != null)
        }

        /**
         * Link up various button actions using [PendingIntent].
         */
        private fun linkButtons(context: Context, views: RemoteViews, playerActive: Boolean) {
            var intent = Intent(
                context,
                NavigationActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (playerActive) intent.putExtra(Constants.INTENT_SHOW_PLAYER, true)
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            // needed starting Android 12 (S = 31)
            flags = flags or PendingIntent.FLAG_IMMUTABLE
            var pendingIntent =
                PendingIntent.getActivity(context, 10, intent, flags)
            views.setOnClickPendingIntent(R.id.appwidget_coverart, pendingIntent)
            views.setOnClickPendingIntent(R.id.appwidget_top, pendingIntent)

            // Emulate media button clicks.
            intent = Intent(Constants.CMD_PROCESS_KEYCODE)
            intent.component = ComponentName(context, UltrasonicIntentReceiver::class.java)
            intent.putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )
            flags = 0
            // needed starting Android 12 (S = 31)
            flags = PendingIntent.FLAG_IMMUTABLE
            pendingIntent = PendingIntent.getBroadcast(context, 11, intent, flags)
            views.setOnClickPendingIntent(R.id.control_play, pendingIntent)
            intent = Intent(Constants.CMD_PROCESS_KEYCODE)
            intent.component = ComponentName(context, UltrasonicIntentReceiver::class.java)
            intent.putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
            )
            pendingIntent = PendingIntent.getBroadcast(context, 12, intent, flags)
            views.setOnClickPendingIntent(R.id.control_next, pendingIntent)
            intent = Intent(Constants.CMD_PROCESS_KEYCODE)
            intent.component = ComponentName(context, UltrasonicIntentReceiver::class.java)
            intent.putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            )
            pendingIntent = PendingIntent.getBroadcast(context, 13, intent, flags)
            views.setOnClickPendingIntent(R.id.control_previous, pendingIntent)
        }
    }
}
