/*
 * DailyMixSelector.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import kotlin.random.Random
import org.moire.ultrasonic.domain.Track

object DailyMixSelector {
    private const val MAX_PER_ARTIST = 3
    private const val MAX_PER_ALBUM = 3
    private const val MAX_CONSECUTIVE_PER_ARTIST = 2
    private const val FAMILIAR_BUCKET = "familiar"
    private const val REDISCOVERY_BUCKET = "rediscovery"
    private const val EXPLORATION_BUCKET = "exploration"
    private const val FALLBACK_BUCKET = "fallback"

    fun select(
        familiarTracks: List<Track>,
        rediscoveryTracks: List<Track>,
        explorationTracks: List<Track>,
        fallbackTracks: List<Track>,
        targetSize: Int,
        minimumSize: Int,
        seed: Long = 0L
    ): List<Track> {
        val familiarQuota = (targetSize * 0.5f).toInt()
        val rediscoveryQuota = (targetSize * 0.3f).toInt()
        val buckets = listOf(
            CandidateBucket(FAMILIAR_BUCKET, familiarQuota, familiarTracks.prepared(seed, FAMILIAR_BUCKET)),
            CandidateBucket(REDISCOVERY_BUCKET, rediscoveryQuota, rediscoveryTracks.prepared(seed, REDISCOVERY_BUCKET)),
            CandidateBucket(
                EXPLORATION_BUCKET,
                targetSize - familiarQuota - rediscoveryQuota,
                explorationTracks.prepared(seed, EXPLORATION_BUCKET)
            ),
            CandidateBucket(FALLBACK_BUCKET, 0, fallbackTracks.prepared(seed, FALLBACK_BUCKET))
        )

        val selected = mutableListOf<SelectedTrack>()
        val selectedIds = mutableSetOf<String>()

        addFromBuckets(
            selected = selected,
            selectedIds = selectedIds,
            buckets = buckets,
            targetSize = targetSize,
            enforceBucketQuotas = true,
            enforceDiversity = true,
            avoidLongArtistBlocks = true
        )

        if (selected.size < targetSize) {
            addFromBuckets(
                selected = selected,
                selectedIds = selectedIds,
                buckets = buckets,
                targetSize = targetSize,
                enforceBucketQuotas = false,
                enforceDiversity = true,
                avoidLongArtistBlocks = true
            )
        }

        if (selected.size < minimumSize) {
            addFromBuckets(
                selected = selected,
                selectedIds = selectedIds,
                buckets = buckets,
                targetSize = targetSize,
                enforceBucketQuotas = false,
                enforceDiversity = true,
                avoidLongArtistBlocks = false
            )
        }

        if (selected.size < minimumSize) {
            addFromBuckets(
                selected = selected,
                selectedIds = selectedIds,
                buckets = buckets,
                targetSize = targetSize,
                enforceBucketQuotas = false,
                enforceDiversity = false,
                avoidLongArtistBlocks = false
            )
        }

        return selected.map { it.track }.take(targetSize)
    }

    private fun addFromBuckets(
        selected: MutableList<SelectedTrack>,
        selectedIds: MutableSet<String>,
        buckets: List<CandidateBucket>,
        targetSize: Int,
        enforceBucketQuotas: Boolean,
        enforceDiversity: Boolean,
        avoidLongArtistBlocks: Boolean
    ) {
        do {
            var addedThisPass = false
            for (bucket in buckets) {
                if (selected.size >= targetSize) return
                if (enforceBucketQuotas && selected.count { it.bucket == bucket.name } >= bucket.quota) {
                    continue
                }

                val candidate = bucket.tracks.firstOrNull { track ->
                    track.id !in selectedIds &&
                        (!enforceDiversity || selected.artistCount(track) < MAX_PER_ARTIST) &&
                        (!enforceDiversity || selected.albumCount(track) < MAX_PER_ALBUM) &&
                        (!avoidLongArtistBlocks || !selected.wouldCreateLongArtistBlock(track))
                } ?: continue

                selected += SelectedTrack(bucket.name, candidate)
                selectedIds += candidate.id
                addedThisPass = true
            }
        } while (addedThisPass && selected.size < targetSize)
    }

    private fun List<Track>.prepared(seed: Long, bucket: String): List<Track> =
        asSequence()
            .filterNot { it.isVideo }
            .distinctBy { it.id }
            .toList()
            .shuffled(Random(seed xor bucket.hashCode().toLong()))

    private fun List<SelectedTrack>.artistCount(candidate: Track): Int {
        val artistKey = candidate.artistKey() ?: return 0
        return count { it.track.artistKey() == artistKey }
    }

    private fun List<SelectedTrack>.albumCount(candidate: Track): Int {
        val albumKey = candidate.albumKey() ?: return 0
        return count { it.track.albumKey() == albumKey }
    }

    private fun List<SelectedTrack>.wouldCreateLongArtistBlock(candidate: Track): Boolean {
        val artistKey = candidate.artistKey() ?: return false
        val tail = takeLast(MAX_CONSECUTIVE_PER_ARTIST)
        return tail.size == MAX_CONSECUTIVE_PER_ARTIST && tail.all { it.track.artistKey() == artistKey }
    }

    private fun Track.artistKey(): String? =
        artistId?.takeIf { it.isNotBlank() } ?: artist?.lowercase()?.takeIf { it.isNotBlank() }

    private fun Track.albumKey(): String? =
        albumId?.takeIf { it.isNotBlank() } ?: album?.lowercase()?.takeIf { it.isNotBlank() }

    private data class CandidateBucket(
        val name: String,
        val quota: Int,
        val tracks: List<Track>
    )

    private data class SelectedTrack(
        val bucket: String,
        val track: Track
    )
}
