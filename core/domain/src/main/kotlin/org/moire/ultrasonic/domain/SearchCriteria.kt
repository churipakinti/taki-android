package org.moire.ultrasonic.domain

/**
 * The criteria for a music search.
 */
data class SearchCriteria(
    val query: String,
    val artistCount: Int,
    val albumCount: Int,
    val songCount: Int,
    val artistOffset: Int = 0,
    val albumOffset: Int = 0,
    val songOffset: Int = 0,
    val musicFolderId: String? = null,
    val artistId: String? = null
)
