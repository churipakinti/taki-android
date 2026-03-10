/*
 * DownloadStatus.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

enum class DownloadState {
    IDLE,
    QUEUED,
    DOWNLOADING,
    RETRYING,
    FAILED,
    CANCELLED,
    DONE,
    PINNED,
    UNKNOWN;

    companion object {
        fun DownloadState.isFinalState(): Boolean = when (this) {
            RETRYING,
            FAILED,
            CANCELLED,
            DONE,
            PINNED
            -> true

            else -> false
        }
    }
}
