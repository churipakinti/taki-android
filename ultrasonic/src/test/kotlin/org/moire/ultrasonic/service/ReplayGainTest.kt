/*
 * ReplayGainTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.text.TextUtils
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlin.math.pow
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic.Verification
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ReplayGainTest {
    private val mockPlayer: Player = mock()

    private fun expectedVolume(replayGain: Float): Float = 10f.pow(replayGain / 20f)

    private fun buildTracks(vararg entries: Metadata.Entry): Tracks {
        // Creating a TrackGroup calls TextUtils.isEmpty() which needs to be mocked for the test.
        Mockito.mockStatic<TextUtils?>(TextUtils::class.java).use { utilities ->
            utilities.`when`<Any?>(Verification { TextUtils.isEmpty(anyString()) }).thenReturn(true)

            val metadata = Metadata(*entries)
            val format = Format.Builder().setMetadata(metadata).build()
            val group = TrackGroup("", format)
            val groups = Tracks.Group(
                group,
                false,
                IntArray(1),
                BooleanArray(1)
            )
            return Tracks(mutableListOf(groups))
        }
    }

    @Test
    fun `playlist is from single album`() {
        val playlist = mutableListOf(
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("Album1").setTitle("Title1").build()
            ).build(),
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("Album1").setTitle("Title2").build()
            ).build(),
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("Album1").setTitle("Title3").build()
            ).build()
        )
        val timeline = PlaylistTimeline(playlist)
        whenever(
            mockPlayer.currentTimeline
        ).thenReturn(timeline)
        whenever(
            mockPlayer.shuffleModeEnabled
        ).thenReturn(false)
        isSingleAlbumPlaylist(mockPlayer) shouldBeEqualTo true
    }

    @Test
    fun `playlist is from multiple albums`() {
        val playlist = mutableListOf(
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("Album1").setTitle("Title1").build()
            ).build(),
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("Album2").setTitle("Title2").build()
            ).build(),
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("Album1").setTitle("Title3").build()
            ).build()
        )
        val timeline = PlaylistTimeline(playlist)
        whenever(
            mockPlayer.currentTimeline
        ).thenReturn(timeline)
        whenever(
            mockPlayer.shuffleModeEnabled
        ).thenReturn(false)
        isSingleAlbumPlaylist(mockPlayer) shouldBeEqualTo false
    }

    @Test
    fun `long playlist`() {
        val playlist = mutableListOf<MediaItem>()
        for (i in 0 until 100) {
            playlist.add(
                MediaItem.Builder().setMediaMetadata(
                    MediaMetadata.Builder().setAlbumTitle("Album1").setTitle("Title$i").build()
                ).build()
            )
        }
        val timeline = PlaylistTimeline(playlist)
        whenever(
            mockPlayer.currentTimeline
        ).thenReturn(timeline)
        whenever(
            mockPlayer.shuffleModeEnabled
        ).thenReturn(false)
        isSingleAlbumPlaylist(mockPlayer) shouldBeEqualTo true
    }

    @Test
    fun `empty playlist`() {
        val playlist = mutableListOf(
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("Album1").setTitle("Title1").build()
            ).build()
        )
        val timeline = PlaylistTimeline(playlist)
        whenever(
            mockPlayer.currentTimeline
        ).thenReturn(timeline)
        whenever(
            mockPlayer.shuffleModeEnabled
        ).thenReturn(false)
        isSingleAlbumPlaylist(mockPlayer) shouldBeEqualTo true
    }

    @Test
    fun `one song playlist`() {
        val playlist = emptyList<MediaItem>()
        val timeline = PlaylistTimeline(playlist)
        whenever(
            mockPlayer.currentTimeline
        ).thenReturn(timeline)
        whenever(
            mockPlayer.shuffleModeEnabled
        ).thenReturn(false)
        isSingleAlbumPlaylist(mockPlayer) shouldBeEqualTo false
    }

    @Test
    fun `no album name`() {
        val playlist = mutableListOf(
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setTitle("Title1").build()
            ).build(),
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setTitle("Title2").build()
            ).build(),
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setTitle("Title3").build()
            ).build()
        )
        val timeline = PlaylistTimeline(playlist)
        whenever(
            mockPlayer.currentTimeline
        ).thenReturn(timeline)
        whenever(
            mockPlayer.shuffleModeEnabled
        ).thenReturn(false)
        isSingleAlbumPlaylist(mockPlayer) shouldBeEqualTo false
    }

    @Test
    fun `empty album name`() {
        val playlist = mutableListOf(
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("").setTitle("Title1").build()
            ).build(),
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("").setTitle("Title2").build()
            ).build(),
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumTitle("").setTitle("Title3").build()
            ).build()
        )
        val timeline = PlaylistTimeline(playlist)
        whenever(
            mockPlayer.currentTimeline
        ).thenReturn(timeline)
        whenever(
            mockPlayer.shuffleModeEnabled
        ).thenReturn(false)
        isSingleAlbumPlaylist(mockPlayer) shouldBeEqualTo false
    }

    @Test
    fun `empty metadata`() {
        getReplayGainVolume(
            ReplayGainType.TrackGainWithFallback,
            buildTracks()
        ) shouldBeEqualTo 1f
    }

    @Test
    fun `id3 replaygain_track_gain`() {
        val replayGain = -1f
        val entry = TextInformationFrame(
            "id",
            "repLaygain_Track_gain",
            listOf<String>(replayGain.toString())
        )
        getReplayGainVolume(
            ReplayGainType.TrackGain,
            buildTracks(entry)
        ) shouldBeEqualTo expectedVolume(replayGain)
    }

    @Test
    fun `id3 replaygain_album_gain`() {
        val replayGain = -2.1f
        val entry = TextInformationFrame(
            "id",
            "repLaygain_albUm_gain",
            listOf<String>(replayGain.toString())
        )
        getReplayGainVolume(
            ReplayGainType.AlbumGain,
            buildTracks(entry)
        ) shouldBeEqualTo expectedVolume(replayGain)
    }

    @Test
    fun `VorbisComment r128_track_gain`() {
        val replayGain = -32.7f
        val r128Gain = replayGain * 256f
        val entry = VorbisComment("r128_Track_gain", r128Gain.toString())
        getReplayGainVolume(
            ReplayGainType.TrackGain,
            buildTracks(entry)
        ) shouldBeEqualTo expectedVolume(replayGain)
    }

    @Test
    fun `VorbisComment r128_album_gain`() {
        val replayGain = -1.2f
        val r128Gain = replayGain * 256f
        val entry = VorbisComment("R128_album_gain", r128Gain.toString())
        getReplayGainVolume(
            ReplayGainType.AlbumGain,
            buildTracks(entry)
        ) shouldBeEqualTo expectedVolume(replayGain)
    }

    @Test
    fun `VorbisComment replaygain_track_gain`() {
        val replayGain = -9.8f
        val entry = VorbisComment("replaygain_track_gain", replayGain.toString())
        getReplayGainVolume(
            ReplayGainType.TrackGain,
            buildTracks(entry)
        ) shouldBeEqualTo expectedVolume(replayGain)
    }

    @Test
    fun `Clamp max track gain`() {
        // Positive replayGain values should clamp to max volume.
        val replayGain = 1.3f
        val entry = VorbisComment("replaygain_track_gain", replayGain.toString())
        getReplayGainVolume(ReplayGainType.TrackGain, buildTracks(entry)) shouldBeEqualTo 1f
    }

    @Test
    fun `Clamp min track gain`() {
        // Very negative replayGain values should approach minimum volume.
        val replayGain = -999f
        val entry = VorbisComment("replaygain_track_gain", replayGain.toString())
        getReplayGainVolume(ReplayGainType.TrackGain, buildTracks(entry)) shouldBeEqualTo 0f
    }

    @Test
    fun `Zero replay gain`() {
        // replayGain of 0 should be max volume.
        val replayGain = 0f
        val entry = VorbisComment("replaygain_track_gain", replayGain.toString())
        getReplayGainVolume(ReplayGainType.TrackGain, buildTracks(entry)) shouldBeEqualTo 1f
    }

    @Test
    fun `Invalid replayGain in tag`() {
        val replayGain = "foo"
        val entry = VorbisComment("replaygain_track_gain", replayGain)
        getReplayGainVolume(ReplayGainType.TrackGain, buildTracks(entry)) shouldBeEqualTo 1f
    }

    @Test
    fun `id3 multiple entries`() {
        val replayGain = -1.3f
        val entry1 = TextInformationFrame(
            "id",
            "foo",
            listOf<String>("bar")
        )
        val entry2 = TextInformationFrame(
            "id",
            "REPLAYGAIN_TRACK_GAIN",
            listOf<String>(replayGain.toString())
        )
        val entry3 = TextInformationFrame(
            "id",
            "bar",
            listOf<String>("foo")
        )
        getReplayGainVolume(
            ReplayGainType.TrackGain,
            buildTracks(entry1, entry2, entry3)
        ) shouldBeEqualTo expectedVolume(replayGain)
    }

    @Test
    fun `vorbisComment multiple entries`() {
        val replayGain = -1.3f
        val entry1 = VorbisComment("foo", "bar")
        val entry2 = VorbisComment("replaygain_track_gain", replayGain.toString())
        val entry3 = VorbisComment("bar", "foo")
        getReplayGainVolume(
            ReplayGainType.TrackGain,
            buildTracks(entry1, entry2, entry3)
        ) shouldBeEqualTo expectedVolume(replayGain)
    }

    @Test
    fun `duplicate replayGain tags`() {
        val replayGain1 = -1.3f
        val replayGain2 = -5.7f
        val entry1 = VorbisComment("replaygain_track_gain", replayGain1.toString())
        val entry2 = VorbisComment("replaygain_track_gain", replayGain2.toString())
        getReplayGainVolume(
            ReplayGainType.TrackGain,
            buildTracks(entry1, entry2)
        ) shouldBeEqualTo expectedVolume(replayGain2)
    }

    @Test
    fun `replayGainType no fallback`() {
        val trackGain = -1.3f
        val albumGain = -5.7f
        val entry1 = VorbisComment("replaygain_track_gain", trackGain.toString())
        val entry2 = VorbisComment("replaygain_album_gain", albumGain.toString())

        getReplayGainVolume(
            ReplayGainType.TrackGain,
            buildTracks(entry1, entry2)
        ) shouldBeEqualTo expectedVolume(trackGain)

        getReplayGainVolume(
            ReplayGainType.TrackGainWithFallback,
            buildTracks(entry1, entry2)
        ) shouldBeEqualTo expectedVolume(trackGain)

        getReplayGainVolume(
            ReplayGainType.AlbumGain,
            buildTracks(entry1, entry2)
        ) shouldBeEqualTo expectedVolume(albumGain)

        getReplayGainVolume(
            ReplayGainType.AlbumGainWithFallback,
            buildTracks(entry1, entry2)
        ) shouldBeEqualTo expectedVolume(albumGain)
    }

    @Test
    fun `replayGainType fallback`() {
        val trackGain = -1.3f
        val albumGain = -5.7f
        val trackEntry = VorbisComment("replaygain_track_gain", trackGain.toString())
        val albumEntry = VorbisComment("replaygain_album_gain", albumGain.toString())

        // Request track gain with fallback, but only album gain is available. Expect to get album gain.
        getReplayGainVolume(
            ReplayGainType.TrackGainWithFallback,
            buildTracks(albumEntry)
        ) shouldBeEqualTo expectedVolume(albumGain)

        // Request only track gain, but only album gain is available. Expect to get max volume.
        getReplayGainVolume(
            ReplayGainType.TrackGain,
            buildTracks(albumEntry)
        ) shouldBeEqualTo 1f

        // Request album gain with fallback, but only track gain is available. Expect to get track gain.
        getReplayGainVolume(
            ReplayGainType.AlbumGainWithFallback,
            buildTracks(trackEntry)
        ) shouldBeEqualTo expectedVolume(trackGain)

        // Request only album gain, but only track gain is available. Expect to get max volume.
        getReplayGainVolume(
            ReplayGainType.AlbumGain,
            buildTracks(trackEntry)
        ) shouldBeEqualTo 1f
    }

    @Test
    fun `empty groups`() {
        val tracks = Tracks(emptyList())
        getReplayGainVolume(
            ReplayGainType.AlbumGain,
            tracks
        ) shouldBeEqualTo 1f
    }

    @Test
    fun `unexpected group size`() {
        Mockito.mockStatic<TextUtils?>(TextUtils::class.java).use { utilities ->
            utilities.`when`<Any?>(Verification { TextUtils.isEmpty(anyString()) }).thenReturn(true)

            val albumGain = -5.7f
            val albumEntry = VorbisComment("replaygain_album_gain", albumGain.toString())
            val metadata = Metadata(albumEntry)
            val format = Format.Builder().setMetadata(metadata).build()
            val group = TrackGroup("", format)
            val groups = Tracks.Group(
                group,
                false,
                IntArray(1),
                BooleanArray(1)
            )
            val tracks = Tracks(mutableListOf(groups, groups))

            getReplayGainVolume(
                ReplayGainType.AlbumGain,
                tracks
            ) shouldBeEqualTo 1f
        }
    }

    @Test
    fun `no metadata`() {
        Mockito.mockStatic<TextUtils?>(TextUtils::class.java).use { utilities ->
            utilities.`when`<Any?>(Verification { TextUtils.isEmpty(anyString()) }).thenReturn(true)

            val format = Format.Builder().build()
            val group = TrackGroup("", format)
            val groups = Tracks.Group(
                group,
                false,
                IntArray(1),
                BooleanArray(1)
            )
            val tracks = Tracks(mutableListOf(groups))

            getReplayGainVolume(
                ReplayGainType.AlbumGain,
                tracks
            ) shouldBeEqualTo 1f
        }
    }
}
