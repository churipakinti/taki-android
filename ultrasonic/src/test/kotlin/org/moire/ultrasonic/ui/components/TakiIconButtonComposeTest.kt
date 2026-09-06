/*
 * TakiIconButtonComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner

/**
 * Proves the JVM Compose-UI test harness on the smallest primitive: a [TakiIconButton]
 * keeps a >=48dp touch target, announces its content description, fires its callback, and
 * exposes selected state to accessibility.
 */
@RunWith(RobolectricTestRunner::class)
class TakiIconButtonComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `has a 48dp touch target, a description and a click action`() {
        var clicks = 0
        compose.setContent {
            TakiTheme {
                TakiIconButton(
                    onClick = { clicks++ },
                    painter = painterResource(R.drawable.media_start),
                    contentDescription = "Play"
                )
            }
        }

        compose.onNodeWithContentDescription("Play")
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `exposes selected state to accessibility`() {
        compose.setContent {
            TakiTheme {
                TakiIconButton(
                    onClick = {},
                    painter = painterResource(R.drawable.ic_radio),
                    contentDescription = "Shuffle",
                    selected = true
                )
            }
        }

        compose.onNodeWithContentDescription("Shuffle").assertIsSelected()
    }
}
