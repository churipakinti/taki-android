/*
 * ReplayGain.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.annotation.OptIn
import androidx.core.math.MathUtils
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlin.math.pow
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

/**
 * These are the tags we look for in id3 tags and vorbis comments that contain replaygain metadata.
 * Our comparisons of these tags are case-insensitive.
 */
const val RG_TRACK = "REPLAYGAIN_TRACK_GAIN"
const val RG_ALBUM = "REPLAYGAIN_ALBUM_GAIN"
const val R128_TRACK = "R128_TRACK_GAIN"
const val R128_ALBUM = "R128_ALBUM_GAIN"

// Some magic numbers we use for converting the replaygain value into an actual volume level.
const val LOUDNESS_COEFFICIENT = 10f
const val LOUDNESS_EXPONENT_DENOMINATOR = 20f

/**
 * When determining if a playlist is a single album this is the maximum number of songs to inspect.
 * If the first N songs are all from the same album then we assume all songs are from the same
 * album. This is simply a safeguard to prevent us walking very long playlists where every song has
 * the same album name, hopefully a rare occurrence in practice.
 */
const val MAX_SONGS_TO_INSPECT_ALBUM_FOR_REPLAYGAIN = 30

val replayGainTags = arrayOf(
    RG_TRACK,
    RG_ALBUM,
    R128_ALBUM,
    R128_TRACK
)

data class Gain(val track: Float, val album: Float)

enum class ReplayGainType {
    AlbumGainWithFallback,
    AlbumGain,
    TrackGainWithFallback,
    TrackGain
}

/**
 * Gets the current playlist from the player and inspects the album name for each item in the
 * playlist. Returns true if they all match. Note: This function only inspects the first
 * MAX_SONGS_TO_INSPECT_ALBUM_FOR_REPLAYGAIN items in the playlist.
 */
fun isSingleAlbumPlaylist(player: Player): Boolean {
    // To avoid iterating over super long playlists we limit the number of songs we'll inspect.
    // If we've gotten a bunch of songs in a row all from the same album we might as well assume
    // it's one really long album.
    val maxSongsToCheck = MAX_SONGS_TO_INSPECT_ALBUM_FOR_REPLAYGAIN
    val nextSongs = Util.getPlayListFromTimeline(
        player.currentTimeline,
        player.shuffleModeEnabled,
        0,
        maxSongsToCheck
    ).map {
        // These items should skip the MediaItemConverter cache.
        // The cache contains the controller's items, which may be modified (e.g. their rating)
        it.toTrack(false)
    }
    if (nextSongs.isEmpty()) return false
    val album = nextSongs[0].album ?: return false
    if (album.isEmpty()) return false
    for (i in 1 until nextSongs.size) {
        if (nextSongs[i].album != album) return false
    }
    return true
}

/**
 * Looks for replayGain metadata in 'tracks' and returns the normalized volume on a scale from
 * 0f (no volume) to 1f (maximum volume). Either track gain or album gain metadata is used based on
 * 'replayGainType'.
 */
@OptIn(UnstableApi::class)
fun getReplayGainVolume(replayGainType: ReplayGainType, tracks: Tracks): Float {
    val groups = tracks.groups
    if (groups.isEmpty()) return 1f
    if (groups.size != 1) {
        Timber.w("Unexpected track group size: %d", groups.size)
        return 1f
    }
    val metadata = tracks.groups.first().getTrackFormat(0).metadata ?: return 1f
    Timber.d("New metadata: %s", metadata)
    return getReplayGainVolumeFromMetadata(replayGainType, metadata)
}

/**
 * Returns desired volume on a scale of 0f to 1f based on replayGain tags in 'metadata'.
 * This is based off Vanilla Music's implementation.
 */
@OptIn(UnstableApi::class)
private fun getReplayGainVolumeFromMetadata(
    replayGainType: ReplayGainType,
    metadata: Metadata
): Float {
    val gain = parseReplayGain(metadata)

    var adjust = 0f
    if (gain != null) {
        when (replayGainType) {
            ReplayGainType.AlbumGainWithFallback -> {
                adjust = gain.album
                if (adjust == 0f) adjust = gain.track
            }

            ReplayGainType.AlbumGain -> {
                adjust = gain.album
            }

            ReplayGainType.TrackGainWithFallback -> {
                adjust = gain.track
                if (adjust == 0f) adjust = gain.album
            }

            ReplayGainType.TrackGain -> {
                adjust = gain.track
            }
        }
    }

    // Final adjustment along the volume curve. As detailed here:
    // https://wiki.hydrogenaudio.org/index.php?title=ReplayGain_1.0_specification#Loudness_normalization
    // Ensure this is clamped to 0 or 1 so that it can be used as a volume. If no replaygain tags
    // were found adjust ends up being 1.0, max volume.
    return MathUtils.clamp(
        (LOUDNESS_COEFFICIENT.pow((adjust / LOUDNESS_EXPONENT_DENOMINATOR))),
        0f,
        1f
    )
}

@OptIn(UnstableApi::class)
private fun parseReplayGain(metadata: Metadata): Gain? {
    data class GainTag(val key: String, val value: Float)

    var trackGain = 0f
    var albumGain = 0f
    var found = false

    val tags = mutableListOf<GainTag>()

    for (i in 0 until metadata.length()) {
        val entry = metadata.get(i)

        // Sometimes the ReplayGain keys will be lowercase, so make them uppercase.
        if (entry is TextInformationFrame && entry.description?.uppercase() in replayGainTags) {
            tags.add(
                GainTag(entry.description!!.uppercase(), parseReplayGainFloat(entry.values.first()))
            )
            continue
        }

        if (entry is VorbisComment && entry.key.uppercase() in replayGainTags) {
            tags.add(GainTag(entry.key.uppercase(), parseReplayGainFloat(entry.value)))
        }
    }

    // Case 1: Normal ReplayGain, most commonly found on MPEG files.
    tags.findLast { tag -> tag.key == RG_TRACK }?.let { tag ->
        trackGain = tag.value
        found = true
    }

    tags.findLast { tag -> tag.key == RG_ALBUM }?.let { tag ->
        albumGain = tag.value
        found = true
    }

    // Case 2: R128 ReplayGain, most commonly found on FLAC files.
    // While technically there is the R128 base gain in Opus files, ExoPlayer doesn't
    // have metadata parsing functionality for those, so we just ignore it.
    tags.findLast { tag -> tag.key == R128_TRACK }?.let { tag ->
        trackGain += tag.value / 256f
        found = true
    }

    tags.findLast { tag -> tag.key == R128_ALBUM }?.let { tag ->
        albumGain += tag.value / 256f
        found = true
    }

    return if (found) {
        Gain(trackGain, albumGain)
    } else {
        null
    }
}

private fun parseReplayGainFloat(raw: String): Float = try {
    raw.replace(Regex("[^0-9.-]"), "").toFloat()
} catch (e: NumberFormatException) {
    0f
}
