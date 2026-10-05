/*
 * TakiLinkify.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import android.util.Patterns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

/**
 * The Compose equivalent of the legacy `Linkify.addLinks(message, Linkify.WEB_URLS)`: every web
 * URL found in [text] becomes a tappable link (a scheme-less match such as `example.com` opens as
 * `http://example.com`, as `Linkify` did). The text itself is never altered.
 */
fun linkifyWebUrls(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val found = matcher.group()
        val url = if (found.contains("://")) found else "http://$found"
        addLink(LinkAnnotation.Url(url, styles), matcher.start(), matcher.end())
    }
}
