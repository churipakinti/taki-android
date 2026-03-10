/*
 * DownloadableTrack.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.FileUtil.getCompleteFile
import org.moire.ultrasonic.util.FileUtil.getPartialFile
import org.moire.ultrasonic.util.FileUtil.getPinnedFile

class DownloadableTrack(
    val track: Track,
    var pinned: Boolean,
    var tryCount: Int,
    var priority: Int
) : Identifiable {
    val pinnedFile = track.getPinnedFile()
    val partialFile = track.getPartialFile()
    val completeFile = track.getCompleteFile()
    override val id: String
        get() = track.id

    override fun compareTo(other: Identifiable) = compareTo(other as DownloadableTrack)
    fun compareTo(other: DownloadableTrack): Int = priority.compareTo(other.priority)
}
