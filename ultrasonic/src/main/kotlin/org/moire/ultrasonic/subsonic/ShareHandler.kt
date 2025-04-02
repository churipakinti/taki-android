/*
 * ShareHandler.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.subsonic

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Share
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.ShareDetails
import org.moire.ultrasonic.util.TimeSpanPicker
import org.moire.ultrasonic.util.Util.getString
import org.moire.ultrasonic.util.Util.ifNotNull

/**
 * This class handles sharing items in the media library
 */
class ShareHandler {
    private var shareDescription: EditText? = null
    private var timeSpanPicker: TimeSpanPicker? = null
    private var shareOnServerCheckBox: CheckBox? = null
    private var hideDialogCheckBox: CheckBox? = null
    private var noExpirationCheckBox: CheckBox? = null
    private var saveAsDefaultsCheckBox: CheckBox? = null
    private var textViewComment: TextView? = null
    private var textViewExpiration: TextView? = null
    private val pattern = Pattern.compile(":")

    fun share(fragment: Fragment, shareDetails: ShareDetails, additionalId: String?) {
        val scope = fragment.activity?.lifecycleScope ?: fragment.lifecycleScope
        scope.launch {
            val share = createShareOnServer(shareDetails, additionalId)
            startActivityForShare(share, shareDetails, fragment)
        }
    }

    private suspend fun createShareOnServer(
        shareDetails: ShareDetails,
        additionalId: String?
    ): Share? {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val ids: MutableList<String> = ArrayList()

                if (!shareDetails.shareOnServer && shareDetails.entries.size == 1) {
                    return@withContext null
                }
                if (shareDetails.entries.isEmpty()) {
                    additionalId.ifNotNull {
                        ids.add(it)
                    }
                } else {
                    for ((id) in shareDetails.entries) {
                        ids.add(id)
                    }
                }

                val musicService = getMusicService()
                var timeInMillis: Long = 0

                if (shareDetails.expiration != 0L) {
                    timeInMillis = shareDetails.expiration
                }

                val shares = musicService.createShare(
                    ids = ids,
                    description = shareDetails.description,
                    expires = timeInMillis
                )

                // Return the share
                if (shares.isNotEmpty()) {
                    shares[0]
                } else {
                    null
                }
            } catch (ignored: Exception) {
                null
            }
        }
    }

    private suspend fun startActivityForShare(
        result: Share?,
        shareDetails: ShareDetails,
        fragment: Fragment
    ) {
        return withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"

            if (result != null) {
                // Created a share, send the URL
                intent.putExtra(
                    Intent.EXTRA_TEXT,
                    String.format(
                        Locale.ROOT,
                        "%s\n\n%s",
                        Settings.shareGreeting,
                        result.url
                    )
                )
            } else {
                // Sending only text details
                val textBuilder = StringBuilder()
                textBuilder.appendLine(Settings.shareGreeting)

                if (!shareDetails.entries[0].title.isNullOrEmpty()) {
                    textBuilder.append(getString(R.string.common_title))
                        .append(": ").appendLine(shareDetails.entries[0].title)
                }
                if (!shareDetails.entries[0].artist.isNullOrEmpty()) {
                    textBuilder.append(getString(R.string.common_artist))
                        .append(": ").appendLine(shareDetails.entries[0].artist)
                }
                if (!shareDetails.entries[0].album.isNullOrEmpty()) {
                    textBuilder.append(getString(R.string.common_album))
                        .append(": ").append(shareDetails.entries[0].album)
                }

                intent.putExtra(Intent.EXTRA_TEXT, textBuilder.toString())
            }

            fragment.activity?.startActivity(
                Intent.createChooser(
                    intent,
                    getString(R.string.share_via)
                )
            )
        }
    }

    fun createShare(fragment: Fragment, tracks: List<Track>, additionalId: String? = null) {
        if (tracks.isEmpty()) return
        val askForDetails = Settings.shouldAskForShareDetails
        val shareDetails = ShareDetails(tracks)
        if (askForDetails) {
            showDialog(fragment, shareDetails, additionalId)
        } else {
            shareDetails.description = Settings.defaultShareDescription
            shareDetails.expiration = System.currentTimeMillis() +
                Settings.defaultShareExpirationInMillis
            share(fragment, shareDetails, additionalId)
        }
    }

    @SuppressLint("InflateParams")
    private fun showDialog(fragment: Fragment, shareDetails: ShareDetails, additionalId: String?) {
        val layout = LayoutInflater.from(fragment.context).inflate(R.layout.share_details, null)

        if (layout != null) {
            shareDescription = layout.findViewById<View>(R.id.share_description) as EditText
            hideDialogCheckBox = layout.findViewById<View>(R.id.hide_dialog) as CheckBox
            shareOnServerCheckBox = layout.findViewById<View>(R.id.share_on_server) as CheckBox
            saveAsDefaultsCheckBox = layout.findViewById<View>(R.id.save_as_defaults) as CheckBox
            timeSpanPicker = layout.findViewById<View>(R.id.date_picker) as TimeSpanPicker
            noExpirationCheckBox = timeSpanPicker!!.findViewById<View>(
                R.id.timeSpanDisableCheckBox
            ) as CheckBox
            textViewComment = layout.findViewById<View>(R.id.commentHeading) as TextView
            textViewExpiration = layout.findViewById<View>(R.id.expirationHeading) as TextView
        }

        // Handle the visibility based on shareDetails.Entries size
        if (shareDetails.entries.size == 1) {
            shareOnServerCheckBox?.setOnCheckedChangeListener { _, _ ->
                updateVisibility()
            }
            shareOnServerCheckBox?.isChecked = Settings.shareOnServer
        } else {
            shareOnServerCheckBox?.isVisible = false
        }

        updateVisibility()

        // Set up the dialog builder
        val builder = makeDialogBuilder(fragment, shareDetails, additionalId, layout)

        // Initialize UI components with default values
        setupDefaultValues()

        builder.create()
        builder.show()
    }

    private fun setupDefaultValues() {
        val defaultDescription = Settings.defaultShareDescription
        val timeSpan = Settings.defaultShareExpiration
        val split = pattern.split(timeSpan)
        if (split.size == 2) {
            val timeSpanAmount = split[0].toInt()
            val timeSpanType = split[1]
            if (timeSpanAmount > 0) {
                noExpirationCheckBox!!.isChecked = false
                timeSpanPicker!!.isEnabled = true
                timeSpanPicker!!.setTimeSpanAmount(timeSpanAmount.toString())
                timeSpanPicker!!.timeSpanType = timeSpanType
            } else {
                noExpirationCheckBox!!.isChecked = true
                timeSpanPicker!!.isEnabled = false
            }
        } else {
            noExpirationCheckBox!!.isChecked = true
            timeSpanPicker!!.isEnabled = false
        }
        shareDescription!!.setText(defaultDescription)
    }

    private fun makeDialogBuilder(
        fragment: Fragment,
        shareDetails: ShareDetails,
        additionalId: String?,
        layout: View?
    ): ConfirmationDialog.Builder {
        val builder = ConfirmationDialog.Builder(fragment.requireContext())
        builder.setTitle(R.string.share_set_share_options)

        builder.setPositiveButton(R.string.menu_share) { _, _ ->
            if (!noExpirationCheckBox!!.isChecked) {
                val timeSpan: Long = timeSpanPicker!!.getTimeSpan()
                shareDetails.expiration = System.currentTimeMillis() + timeSpan
            }

            shareDetails.description = shareDescription!!.text.toString()
            shareDetails.shareOnServer = shareOnServerCheckBox!!.isChecked

            if (hideDialogCheckBox!!.isChecked) {
                Settings.shouldAskForShareDetails = false
            }

            if (saveAsDefaultsCheckBox!!.isChecked) {
                val timeSpanType: String = timeSpanPicker!!.timeSpanType!!
                val timeSpanAmount: Int = timeSpanPicker!!.timeSpanAmount
                Settings.defaultShareExpiration =
                    if (!noExpirationCheckBox!!.isChecked && timeSpanAmount > 0) {
                        String.format(Locale.ROOT, "%d:%s", timeSpanAmount, timeSpanType)
                    } else {
                        ""
                    }

                Settings.defaultShareDescription = shareDetails.description!!
                Settings.shareOnServer = shareDetails.shareOnServer
            }

            share(fragment, shareDetails, additionalId)
        }

        builder.setNegativeButton(R.string.common_cancel) { dialog, _ ->
            dialog.cancel()
        }

        builder.setView(layout)
        builder.setCancelable(true)

        // Set up the timeSpanPicker
        timeSpanPicker!!.setTimeSpanDisableText(getString(R.string.no_expiration))
        noExpirationCheckBox!!.setOnCheckedChangeListener { _, b ->
            timeSpanPicker!!.isEnabled = !b
        }
        return builder
    }

    private fun updateVisibility() {
        if (!shareOnServerCheckBox!!.isVisible || shareOnServerCheckBox!!.isChecked) {
            noExpirationCheckBox?.isVisible = true
            timeSpanPicker?.isVisible = true
            shareDescription?.isVisible = true
            textViewComment?.isVisible = true
            textViewExpiration?.isVisible = true
        } else {
            noExpirationCheckBox?.isVisible = false
            timeSpanPicker?.isVisible = false
            shareDescription?.isVisible = false
            textViewComment?.isVisible = false
            textViewExpiration?.isVisible = false
        }
    }
}
