/*
 * APIAlbumInfoConverter.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

@file:JvmName("APIAlbumInfoConverter")

package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.AlbumInfo as APIAlbumInfo

fun APIAlbumInfo.toDomainEntity(): AlbumInfo = AlbumInfo(
    notes = notes,
    musicBrainzId = musicBrainzId,
    lastFmUrl = lastFmUrl,
    smallImageUrl = smallImageUrl,
    mediumImageUrl = mediumImageUrl,
    largeImageUrl = largeImageUrl
)
