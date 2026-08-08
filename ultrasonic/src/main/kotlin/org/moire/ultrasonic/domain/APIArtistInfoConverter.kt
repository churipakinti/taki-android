/*
 * APIArtistInfoConverter.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

@file:JvmName("APIArtistInfoConverter")

package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.ArtistInfo as APIArtistInfo

fun APIArtistInfo.toDomainEntity(serverId: Int): ArtistInfo = ArtistInfo(
    biography = biography,
    musicBrainzId = musicBrainzId,
    lastFmUrl = lastFmUrl,
    smallImageUrl = smallImageUrl,
    mediumImageUrl = mediumImageUrl,
    largeImageUrl = largeImageUrl,
    similarArtists = similarArtists.map { it.toDomainEntity(serverId) }
)
