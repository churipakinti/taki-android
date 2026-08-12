/*
 * ArtistRadioSelector.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.moire.ultrasonic.domain.Track

object ArtistRadioSelector {
    private const val MAX_CONSECUTIVE_PER_ARTIST = 2
    private const val MAX_PER_ALBUM = 4
    private const val SEED_BUCKET = "seed"
    private const val RELATED_BUCKET = "related"
    private const val FILLER_BUCKET = "filler"

    fun select(
        seedArtistId: String,
        seedArtistName: String?,
        seedArtistTracks: List<Track>,
        relatedArtistTracks: List<Track>,
        fillerTracks: List<Track>,
        targetSize: Int,
        minimumSize: Int
    ): List<Track> {
        val seedTracks = seedArtistTracks
            .filter { it.belongsTo(seedArtistId, seedArtistName) }
            .prepared()
        val relatedTracks = relatedArtistTracks
            .filterNot { it.belongsTo(seedArtistId, seedArtistName) }
            .prepared()
        val filler = fillerTracks
            .filterNot { it.belongsTo(seedArtistId, seedArtistName) }
            .prepared()

        val seedQuota = (targetSize * 0.5f).toInt()
        val relatedQuota = (targetSize * 0.35f).toInt()
        val buckets = listOf(
            CandidateBucket(SEED_BUCKET, seedQuota, seedTracks),
            CandidateBucket(RELATED_BUCKET, relatedQuota, relatedTracks),
            CandidateBucket(FILLER_BUCKET, targetSize - seedQuota - relatedQuota, filler)
        )

        val selected = mutableListOf<SelectedTrack>()
        val selectedIds = mutableSetOf<String>()

        addFromBuckets(
            selected = selected,
            selectedIds = selectedIds,
            buckets = buckets,
            targetSize = targetSize,
            enforceBucketQuotas = true,
            enforceAlbumLimit = true,
            avoidLongArtistBlocks = true
        )

        if (selected.size < targetSize) {
            addFromBuckets(
                selected = selected,
                selectedIds = selectedIds,
                buckets = buckets,
                targetSize = targetSize,
                enforceBucketQuotas = false,
                enforceAlbumLimit = true,
                avoidLongArtistBlocks = true
            )
        }

        if (selected.size < targetSize) {
            addFromBuckets(
                selected = selected,
                selectedIds = selectedIds,
                buckets = buckets,
                targetSize = targetSize,
                enforceBucketQuotas = false,
                enforceAlbumLimit = true,
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
                enforceAlbumLimit = false,
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
        enforceAlbumLimit: Boolean,
        avoidLongArtistBlocks: Boolean
    ) {
        do {
            var addedThisPass = false
            for (bucket in buckets) {
                if (selected.size >= targetSize) return
                if (enforceBucketQuotas &&
                    selected.count { it.bucket == bucket.name } >= bucket.quota
                ) {
                    continue
                }

                val candidate = bucket.tracks.firstOrNull { track ->
                    track.id !in selectedIds &&
                        (!enforceAlbumLimit || selected.albumCount(track) < MAX_PER_ALBUM) &&
                        (!avoidLongArtistBlocks || !selected.wouldCreateLongArtistBlock(track))
                } ?: continue

                selected += SelectedTrack(bucket.name, candidate)
                selectedIds += candidate.id
                addedThisPass = true
            }
        } while (addedThisPass && selected.size < targetSize)
    }

    private fun List<Track>.prepared(): List<Track> = asSequence()
        .filterNot { it.isVideo }
        .distinctBy { it.id }
        .toList()

    private fun Track.belongsTo(artistId: String, artistName: String?): Boolean =
        this.artistId == artistId ||
            (!artistName.isNullOrBlank() && artist.equals(artistName, ignoreCase = true))

    private fun List<SelectedTrack>.albumCount(candidate: Track): Int {
        val albumKey = candidate.albumKey() ?: return 0
        return count { it.track.albumKey() == albumKey }
    }

    private fun List<SelectedTrack>.wouldCreateLongArtistBlock(candidate: Track): Boolean {
        val artistKey = candidate.artistKey() ?: return false
        val tail = takeLast(MAX_CONSECUTIVE_PER_ARTIST)
        return tail.size == MAX_CONSECUTIVE_PER_ARTIST &&
            tail.all { it.track.artistKey() == artistKey }
    }

    private fun Track.artistKey(): String? =
        artistId?.takeIf { it.isNotBlank() } ?: artist?.lowercase()?.takeIf { it.isNotBlank() }

    private fun Track.albumKey(): String? =
        albumId?.takeIf { it.isNotBlank() } ?: album?.lowercase()?.takeIf { it.isNotBlank() }

    private data class CandidateBucket(val name: String, val quota: Int, val tracks: List<Track>)

    private data class SelectedTrack(val bucket: String, val track: Track)
}
