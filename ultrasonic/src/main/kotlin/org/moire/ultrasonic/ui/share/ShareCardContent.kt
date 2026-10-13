/*
 * ShareCardContent.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import android.text.Html

/**
 * The text of a share card (issue #20): exactly what is printed on it and nothing else - never a
 * server address, account, media id or path. Built once from a track snapshot by
 * [ShareCardContent.from], so every field is already cleaned and the renderer never has to
 * guess.
 */
data class ShareCardContent(
    val title: String,
    /** Null when the track has no artist: the line is simply not drawn. */
    val artist: String?,
    /** Null when there is no album or it only repeats the title (a single). */
    val album: String?,
) {
    /** One line for accessibility ("Title by Artist"). */
    fun describe(): String = if (artist != null) "$title - $artist" else title

    companion object {
        /** Null when there is no usable title: there is nothing to share. */
        fun from(title: String?, artist: String?, album: String?): ShareCardContent? {
            val cleanTitle = cleanShareText(title) ?: return null
            val cleanArtist = cleanShareText(artist)
            val cleanAlbum = cleanShareText(album)?.takeUnless { it.equals(cleanTitle, ignoreCase = true) }
            return ShareCardContent(cleanTitle, cleanArtist, cleanAlbum)
        }
    }
}

/**
 * Server metadata is occasionally HTML-flavoured (`&amp;`, `<b>`): decode entities, drop tags and
 * control characters, collapse whitespace. Null for blank input.
 */
fun cleanShareText(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val decoded = if (raw.indexOf('&') >= 0 || raw.indexOf('<') >= 0) {
        Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
    } else {
        raw
    }
    val cleaned = decoded
        .filter { !it.isISOControl() || it == '\n' || it == '\t' }
        .replace(WHITESPACE, " ")
        .trim()
    return cleaned.ifEmpty { null }
}

private val WHITESPACE = Regex("\\s+")
