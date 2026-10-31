/*
 * PlaybackPolicyInvariantsTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #36 (listening-experience audit): audio focus and "audio becoming noisy" are not code in
 * Taki - Media3 implements both, and only if the player is built with these two flags. Nothing
 * else in the suite would notice if one were dropped (the watchdog tests start from an already
 * running player), so they are pinned here from the checked-in source, like the other
 * source-text invariants. The foreground-service type is pinned too: without `mediaPlayback`,
 * Android 14+ refuses to keep the playback service alive in the background.
 */
class PlaybackPolicyInvariantsTest {

    private val module = File(".")
    private val playbackService by lazy {
        File(module, "src/main/kotlin/org/moire/ultrasonic/service/PlaybackService.kt").readText()
    }
    private val manifest by lazy { File(module, "src/main/AndroidManifest.xml").readText() }

    private fun playerBuilder() = playbackService.substringAfter("ExoPlayer.Builder(").substringBefore(".build()")

    @Test
    fun `Media3 owns audio focus - the player requests focus handling`() {
        // second argument true == handleAudioFocus: transient loss pauses/ducks, regain resumes
        assertTrue(Regex("""\.setAudioAttributes\(\s*getAudioAttributes\(\)\s*,\s*true\s*\)""").containsMatchIn(playerBuilder()))
    }

    @Test
    fun `playback pauses when the output becomes noisy`() {
        assertTrue(playerBuilder().contains(".setHandleAudioBecomingNoisy(true)"))
    }

    @Test
    fun `the player keeps the device awake while playing`() {
        assertTrue(playerBuilder().contains(".setWakeMode(getWakeModeFlag())"))
        assertTrue(playbackService.contains("C.WAKE_MODE_LOCAL") && playbackService.contains("C.WAKE_MODE_NETWORK"))
    }

    @Test
    fun `the audio attributes describe music playback`() {
        val attrs = playbackService.substringAfter("fun getAudioAttributes()").substringBefore("private inner class")
        assertTrue(attrs.contains("setUsage(USAGE_MEDIA)"))
        assertTrue(attrs.contains("C.AUDIO_CONTENT_TYPE_MUSIC"))
    }

    @Test
    fun `the playback service is declared as a mediaPlayback foreground service`() {
        // the <service ...> element that declares the Media3 library action
        val action = manifest.indexOf("androidx.media3.session.MediaLibraryService")
        val service = manifest.substring(manifest.lastIndexOf("<service", action)).substringBefore("</service>")
        assertTrue(service.contains("android:foregroundServiceType=\"mediaPlayback\""))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
    }

    @Test
    fun `removing the task consults the policy first and never stops playback unconditionally`() {
        val body = playbackService.substringAfter("override fun onTaskRemoved").substringBefore("private fun releasePlayerAndSession")
        val policy = body.indexOf("shouldContinuePlaybackAfterTaskRemoved(")
        val release = body.indexOf("releasePlayerAndSession()")
        assertTrue("onTaskRemoved must ask the policy", policy >= 0)
        assertTrue("the release must come after the policy", release > policy)
        // the continue branch returns before the release and before super (which pauses every player)
        val keepBranch = body.substring(policy, release)
        assertTrue(keepBranch.contains("return"))
        assertTrue(!keepBranch.contains("super.onTaskRemoved"))
    }
}
