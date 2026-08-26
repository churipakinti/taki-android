/*
 * StackedArtworkBinder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.view.View
import android.widget.ImageView
import androidx.core.view.isVisible
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/**
 * Binds up to 3 albums into an inflated `view_stacked_artwork.xml`. Shared by the Box Sets
 * grid card (CollectionRowAdapter) and the Collection Detail header (CollectionDetailFragment)
 * so both
 * screens follow the same rule: only the covers a Collection actually has are shown - a
 * single-cover Collection shows one clean cover, never 3 copies of the same image.
 */
fun bindStackedArtwork(root: View, albums: List<Album>, imageLoaderProvider: ImageLoaderProvider) {
    val back = root.findViewById<View>(R.id.stack_back)
    val middle = root.findViewById<View>(R.id.stack_middle)
    val frontImage = root.findViewById<ImageView>(R.id.stack_front_image)
    val middleImage = root.findViewById<ImageView>(R.id.stack_middle_image)
    val backImage = root.findViewById<ImageView>(R.id.stack_back_image)

    back.isVisible = albums.size > 2
    middle.isVisible = albums.size > 1

    imageLoaderProvider.executeOn {
        it.loadImage(frontImage, albums.getOrNull(0), false, 0, R.drawable.unknown_album)
        if (middle.isVisible) {
            it.loadImage(middleImage, albums.getOrNull(1), false, 0, R.drawable.unknown_album)
        }
        if (back.isVisible) {
            it.loadImage(backImage, albums.getOrNull(2), false, 0, R.drawable.unknown_album)
        }
    }
}
