/*
 * ComposeArtwork.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.imageloader

import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.FileUtil

/**
 * Builds the Coil model for a domain entity's cover art, for use with Compose `AsyncImage`
 * (via `TakiArtwork`). Returns a [CoverArtRequest] the app's singleton `ImageLoader`
 * resolves through [CoverArtFetcher] / [CoverArtKeyer] - the same disk-cache-then-network
 * path the View-based `ImageLoader.loadImage()` uses - or `null` when the entity has no
 * cover, in which case the caller shows the neutral placeholder.
 *
 * `getAlbumArtKey` builds a path and hashes it (see [FileUtil]); call this off the main
 * thread (the ViewModel maps domain -> UI models on a background dispatcher).
 */
fun MusicDirectory.Child.coverArtRequestOrNull(large: Boolean = false): CoverArtRequest? {
    val id = coverArt
    if (id.isNullOrEmpty()) return null
    val key = FileUtil.getAlbumArtKey(this, large) ?: return null
    // size 0 -> server default / no downsample, matching the existing carousel delegates.
    return CoverArtRequest(id, key, size = 0)
}
