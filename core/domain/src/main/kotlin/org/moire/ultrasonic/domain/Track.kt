/*
 * Track.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.domain

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.io.Serializable
import java.util.Date

@Entity(tableName = "tracks", primaryKeys = ["id", "serverId"])
data class Track(
    override var id: String,
    @ColumnInfo(defaultValue = "-1")
    override var serverId: Int = -1,
    override var parent: String? = null,
    override var isDirectory: Boolean = false,
    override var title: String? = null,
    override var album: String? = null,
    var albumId: String? = null,
    override var artist: String? = null,
    override var artistId: String? = null,
    var track: Int? = null,
    override var year: Int? = null,
    override var genre: String? = null,
    var contentType: String? = null,
    var suffix: String? = null,
    var transcodedContentType: String? = null,
    var transcodedSuffix: String? = null,
    override var coverArt: String? = null,
    var size: Long? = null,
    override var songCount: Long? = null,
    override var duration: Int? = null,
    var bitRate: Int? = null,
    override var path: String? = null,
    override var isVideo: Boolean = false,
    override var starred: Boolean = false,
    override var discNumber: Int? = null,
    var type: String? = null,
    override var created: Date? = null,
    override var closeness: Int = 0,
    var bookmarkPosition: Int = 0,
    var userRating: Int? = null,
    var averageRating: Float? = null,
    override var name: String? = null,
    // History signals for Mix diario v1.1. Null means the server didn't expose the data,
    // not "zero plays" - callers must not treat null as a confident signal either way.
    var playCount: Long? = null,
    var lastPlayed: Date? = null,
    override var grouping: String? = null
) : MusicDirectory.Child(),
    Serializable {
    fun setDuration(duration: Long) {
        this.duration = duration.toInt()
    }

    companion object {
        private const val serialVersionUID = -3339106650010798108L
    }

    fun compareTo(other: Track): Int = when {
        this.closeness == other.closeness -> {
            0
        }

        this.closeness > other.closeness -> {
            -1
        }

        else -> {
            1
        }
    }

    override fun compareTo(other: Identifiable) = compareTo(other as Track)
}
