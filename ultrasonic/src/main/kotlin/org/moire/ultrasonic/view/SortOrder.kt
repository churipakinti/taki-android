/*
 * kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.view

/*
 * This enum is very similar to AlbumListType, but not completely the same.
 */
enum class SortOrder(val typeName: String) {
    RANDOM("random"),
    NEWEST("newest"),
    HIGHEST("highest"),
    FREQUENT("frequent"),
    RECENT("recent"),
    BY_NAME("alphabeticalByName"),
    BY_ARTIST("alphabeticalByArtist"),
    BY_GENRE("byGenre"),
    STARRED("starred"),
    BY_YEAR("byYear")
}
