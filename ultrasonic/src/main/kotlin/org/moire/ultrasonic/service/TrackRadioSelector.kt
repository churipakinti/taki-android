/*
 * TrackRadioSelector.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.moire.ultrasonic.domain.Track

object TrackRadioSelector {
    private const val MAX_PER_ARTIST = 4
    private const val MAX_CONSECUTIVE_PER_ARTIST = 2

    fun select(
        seed: Track,
        candidateGroups: List<List<Track>>,
        targetSize: Int,
        minimumSize: Int
    ): List<Track> {
        val selected = mutableListOf(seed)
        val seenIds = mutableSetOf(seed.id)
        val candidates = candidateGroups
            .asSequence()
            .flatten()
            .filterNot { it.isVideo }
            .filter { seenIds.add(it.id) }
            .toList()

        addCandidates(
            selected = selected,
            candidates = candidates,
            targetSize = targetSize,
            enforceArtistLimit = true,
            avoidLongBlocks = true
        )

        if (selected.size < minimumSize) {
            addCandidates(
                selected = selected,
                candidates = candidates,
                targetSize = targetSize,
                enforceArtistLimit = false,
                avoidLongBlocks = true
            )
        }

        if (selected.size < minimumSize) {
            addCandidates(
                selected = selected,
                candidates = candidates,
                targetSize = targetSize,
                enforceArtistLimit = false,
                avoidLongBlocks = false
            )
        }

        return selected.take(targetSize)
    }

    private fun addCandidates(
        selected: MutableList<Track>,
        candidates: List<Track>,
        targetSize: Int,
        enforceArtistLimit: Boolean,
        avoidLongBlocks: Boolean
    ) {
        val selectedIds = selected.mapTo(mutableSetOf()) { it.id }

        do {
            var addedThisPass = false
            for (candidate in candidates) {
                if (selected.size >= targetSize) return
                if (candidate.id in selectedIds) continue
                if (enforceArtistLimit && selected.artistCount(candidate) >= MAX_PER_ARTIST) {
                    continue
                }
                if (avoidLongBlocks && selected.wouldCreateLongArtistBlock(candidate)) continue
                selectedIds += candidate.id
                selected += candidate
                addedThisPass = true
            }
        } while (addedThisPass && selected.size < targetSize)
    }

    private fun List<Track>.artistCount(candidate: Track): Int {
        val artistKey = candidate.artistKey() ?: return 0
        return count { it.artistKey() == artistKey }
    }

    private fun List<Track>.wouldCreateLongArtistBlock(candidate: Track): Boolean {
        val artistKey = candidate.artistKey() ?: return false
        val tail = takeLast(MAX_CONSECUTIVE_PER_ARTIST)
        return tail.size == MAX_CONSECUTIVE_PER_ARTIST && tail.all { it.artistKey() == artistKey }
    }

    private fun Track.artistKey(): String? =
        artistId?.takeIf { it.isNotBlank() } ?: artist?.lowercase()?.takeIf { it.isNotBlank() }
}
