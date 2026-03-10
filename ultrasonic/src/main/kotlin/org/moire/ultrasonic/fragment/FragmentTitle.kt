/*
 * FragmentTitle.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment

/**
 * Contains utility functions related to Fragment title handling
 */
object FragmentTitle {
    fun setTitle(fragment: Fragment, title: CharSequence?) {
        // Only set the title if our fragment is a direct child of the NavHostFragment...
        if (fragment.parentFragment is NavHostFragment) {
            (fragment.activity as AppCompatActivity).supportActionBar?.title = title
        }
    }

    fun setTitle(fragment: Fragment, id: Int) {
        // Only set the title if our fragment is a direct child of the NavHostFragment...
        if (fragment.parentFragment is NavHostFragment) {
            (fragment.activity as AppCompatActivity).supportActionBar?.setTitle(id)
        }
    }

    fun getTitle(fragment: Fragment): CharSequence? =
        (fragment.activity as AppCompatActivity).supportActionBar?.title
}
