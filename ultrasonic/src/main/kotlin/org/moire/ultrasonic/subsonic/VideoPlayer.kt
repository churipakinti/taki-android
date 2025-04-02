package org.moire.ultrasonic.subsonic

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Util

/**
 * This utility class helps starting video playback
 */
@Suppress("UtilityClassWithPublicConstructor")
class VideoPlayer {
    companion object {
        fun playVideo(context: Context, track: Track?) {
            if (!Util.hasUsableNetwork() || track == null) {
                Util.toast(R.string.select_album_no_network, true, context)
                return
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                val url = MusicServiceFactory.getMusicService().getStreamUrl(
                    track.id,
                    maxBitRate = null,
                    format = "raw"
                )
                intent.setDataAndType(
                    url?.toUri(),
                    "video/*"
                )
                context.startActivity(intent)
            } catch (all: Exception) {
                Util.toast(all.toString(), false, context)
            }
        }
    }
}
