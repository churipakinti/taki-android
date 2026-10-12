/*
 * SharedStatesScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the shared late-state surfaces (issue #26 phase 5C.2): the load-error state, the app
 * error sheet (short / long), and a confirm sheet, at standard, 1.3x font and narrow width.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class SharedStatesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(
        tag: String,
        width: Dp = 420.dp,
        height: Dp = 560.dp,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                TakiTheme {
                    Box(
                        modifier = Modifier
                            .testTag(tag)
                            .width(width)
                            .height(height)
                            .background(TakiTheme.colors.black),
                    ) { content() }
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private val longError =
        "Could not connect to the server at https://music.example.com:4533. Check the address, " +
            "your network connection and that the server is running, then try again."

    @Test
    fun loadErrorStandard() = capture("shared_load_error") { LoadErrorState(onRetry = {}) }

    @Test
    fun loadErrorFont130Narrow() =
        capture("shared_load_error_font_1_30_narrow", width = 320.dp, fontScale = 1.3f) {
            LoadErrorState(onRetry = {})
        }

    @Test
    fun appErrorShort() = capture("shared_app_error_short") {
        AppErrorSheet(listOf("Playlist not found"), onDismiss = {}, bottomContentInset = 0.dp)
    }

    @Test
    fun appErrorLongFont130() = capture("shared_app_error_long_font_1_30", fontScale = 1.3f) {
        AppErrorSheet(listOf(longError), onDismiss = {}, bottomContentInset = 0.dp)
    }

    @Test
    fun confirmDestructiveNarrow() = capture("shared_confirm_destructive_narrow", width = 320.dp, height = 400.dp) {
        TakiConfirmSheet(
            title = "Confirmar",
            message = "¿Seguro que quieres eliminar todas las canciones descargadas? No se puede deshacer.",
            confirmLabel = "Eliminar descargas",
            dismissLabel = "Cancelar",
            onConfirm = {},
            onDismiss = {},
            bottomContentInset = 0.dp,
            sheetTestTag = "shared_confirm_sheet",
            destructive = true,
        )
    }
}
