/*
 * ServerColor.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors

private const val LUMINANCE_LIMIT = 0.5

/**
 * Contains functions for computing server display colors
 */
object ServerColor {

    @ColorInt
    fun getBackgroundColor(context: Context, serverColor: Int?): Int = if (serverColor != null) {
        MaterialColors.harmonizeWithPrimary(context, serverColor)
    } else {
        MaterialColors.getColor(context, android.R.attr.colorPrimary, "")
    }

    @ColorInt
    fun getForegroundColor(context: Context, serverColor: Int?): Int {
        val backgroundColor = getBackgroundColor(context, serverColor)
        val luminance = ColorUtils.calculateLuminance(backgroundColor)

        return if (luminance < LUMINANCE_LIMIT) {
            ContextCompat.getColor(context, org.moire.ultrasonic.R.color.selected_menu_dark)
        } else {
            ContextCompat.getColor(context, org.moire.ultrasonic.R.color.selected_menu_light)
        }
    }
}
