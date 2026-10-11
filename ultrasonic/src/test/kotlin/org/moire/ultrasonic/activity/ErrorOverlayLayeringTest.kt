/*
 * ErrorOverlayLayeringTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.activity

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element

/**
 * The Activity-wide error sheet must be the topmost modal layer (issue #33): drawn above and
 * hit-tested before the elevated Material bottom navigation and the mini-player.
 *
 * Android orders siblings by effective Z (elevation + translationZ) first and by child index only
 * as the tie-break, for both drawing and touch dispatch. The Material bottom navigation carries a
 * non-zero default elevation, so an overlay that is merely *later* in the XML (elevation 0) sits
 * below it. These tests read the real `navigation_activity.xml` and drive a real [FrameLayout]
 * built from those attributes through `dispatchTouchEvent`.
 */
@RunWith(RobolectricTestRunner::class)
class ErrorOverlayLayeringTest {

    private val ns = "http://schemas.android.com/apk/res/android"
    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.UltrasonicTheme_Dark)

    private val shellRoot: Element by lazy {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/res/layout/navigation_activity.xml"))
        doc.documentElement.getElementsByTagName("FrameLayout").let { list ->
            (0 until list.length).map { list.item(it) as Element }
                .first { it.getAttributeNS(ns, "id") == "@+id/nav_host_container" }
        }
    }

    private fun children(): List<Element> =
        (0 until shellRoot.childNodes.length).map { shellRoot.childNodes.item(it) }.filterIsInstance<Element>()

    private fun child(id: String) = children().first { it.getAttributeNS(ns, "id") == "@+id/$id" }

    /** Resolves `@dimen/name` / `Ndp` / absent (0) from the layout's own attribute, in px. */
    private fun elevationPx(e: Element): Float {
        val raw = e.getAttributeNS(ns, "elevation")
        return when {
            raw.isEmpty() -> 0f
            raw.startsWith("@dimen/") ->
                context.resources.getDimension(
                    context.resources.getIdentifier(raw.removePrefix("@dimen/"), "dimen", context.packageName)
                )
            raw.endsWith("dp") -> raw.removeSuffix("dp").toFloat() * context.resources.displayMetrics.density
            else -> error("unsupported elevation value $raw")
        }
    }

    private fun realBottomNavElevationPx(): Float =
        BottomNavigationView(context).elevation

    // --- structural contract ------------------------------------------------------------------

    @Test
    fun `the error overlay is the last sibling and starts GONE`() {
        assertEquals("error_overlay_host", children().last().getAttributeNS(ns, "id").removePrefix("@+id/"))
        assertEquals("gone", child("error_overlay_host").getAttributeNS(ns, "visibility"))
    }

    @Test
    fun `the Material bottom navigation has a non-zero default elevation - the cause of 33`() {
        assertTrue(
            "the bottom nav's own elevation is what lifts it above a later zero-elevation sibling",
            realBottomNavElevationPx() > 0f
        )
    }

    @Test
    fun `the error overlay stacks strictly above the bottom navigation and the mini-player`() {
        val overlay = elevationPx(child("error_overlay_host"))
        assertTrue(
            "overlay Z $overlay must exceed the bottom nav's ${realBottomNavElevationPx()}",
            overlay > maxOf(realBottomNavElevationPx(), elevationPx(child("bottom_navigation")))
        )
        assertTrue(overlay > elevationPx(child("mini_player_host")))
    }

    // --- real touch dispatch through a hierarchy built from the layout's attributes ----------------

    private class Shell(
        val root: FrameLayout,
        val nav: BottomNavigationView,
        val miniPlayer: View,
        val overlay: View,
        var overlayTouches: Int = 0,
        var miniPlayerTouches: Int = 0,
        var navTabsSelected: Int = 0,
    )

    private fun downCount(e: MotionEvent): Int = if (e.action == MotionEvent.ACTION_DOWN) 1 else 0

    private fun buildShell(overlayVisible: Boolean): Shell {
        val root = FrameLayout(context)
        val nav = BottomNavigationView(context).apply {
            inflateMenu(R.menu.bottom_navigation)
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
        }
        val mini = View(context).apply { elevation = elevationPx(child("mini_player_host")) }
        val overlay = View(context).apply {
            elevation = elevationPx(child("error_overlay_host"))
            visibility = if (overlayVisible) View.VISIBLE else View.GONE
        }
        val shell = Shell(root, nav, mini, overlay)
        nav.setOnItemSelectedListener { shell.navTabsSelected++; true }
        mini.setOnTouchListener { _, e ->
            shell.miniPlayerTouches += downCount(e)
            true
        }
        overlay.setOnTouchListener { _, e ->
            shell.overlayTouches += downCount(e)
            true
        }

        // Same order and gravities as navigation_activity.xml.
        root.addView(View(context), FrameLayout.LayoutParams(-1, -1))
        root.addView(nav, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(mini, FrameLayout.LayoutParams(-1, 192, Gravity.BOTTOM))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        relayout(root)
        return shell
    }

    private fun relayout(root: FrameLayout) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1080, 2000)
    }

    private fun tap(shell: Shell, x: Float, y: Float) {
        relayout(shell.root)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, x, y, 0)
        shell.root.dispatchTouchEvent(down)
        shell.root.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    @Test
    fun `a visible error overlay receives taps over the bottom navigation and the mini-player`() {
        val shell = buildShell(overlayVisible = true)
        // Right-most bottom-nav item (where "Search" sits) and the mini-player band.
        tap(shell, 950f, (shell.nav.top + shell.nav.height / 2).toFloat())
        assertEquals("overlay must get the tap that landed on the bottom nav", 1, shell.overlayTouches)
        assertEquals("no tab may be selected behind the modal", 0, shell.navTabsSelected)
        tap(shell, 540f, (shell.miniPlayer.top + shell.miniPlayer.height / 2).toFloat())
        assertEquals(2, shell.overlayTouches)
        assertEquals("the mini-player must not react behind the modal", 0, shell.miniPlayerTouches)
    }

    @Test
    fun `a hidden error overlay never intercepts input`() {
        val shell = buildShell(overlayVisible = false)
        tap(shell, 540f, (shell.miniPlayer.top + shell.miniPlayer.height / 2).toFloat())
        assertEquals(0, shell.overlayTouches)
        assertEquals("with no error the mini-player works normally", 1, shell.miniPlayerTouches)
        assertFalse(shell.overlay.isShown)
    }

    @Test
    fun `showing then hiding the overlay toggles who receives the same tap`() {
        val shell = buildShell(overlayVisible = false)
        val y = (shell.miniPlayer.top + shell.miniPlayer.height / 2).toFloat()
        tap(shell, 540f, y)
        assertEquals(1, shell.miniPlayerTouches)
        shell.overlay.visibility = View.VISIBLE
        tap(shell, 540f, y)
        assertEquals("while visible the overlay takes the tap", 1, shell.overlayTouches)
        assertEquals(1, shell.miniPlayerTouches)
        shell.overlay.visibility = View.GONE
        tap(shell, 540f, y)
        assertEquals("after dismissal the mini-player works again", 2, shell.miniPlayerTouches)
        assertEquals(1, shell.overlayTouches)
    }
}
