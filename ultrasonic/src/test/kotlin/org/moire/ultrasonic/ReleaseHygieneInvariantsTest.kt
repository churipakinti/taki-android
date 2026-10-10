/*
 * ReleaseHygieneInvariantsTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5B release-hygiene invariants, read straight from the checked-in sources (manifest, backup
 * rules, Gradle). They pin the *audited* state so that a change to any of these shows up in review
 * instead of silently reaching a beta APK. Where the audited state is an accepted trade-off (e.g.
 * cleartext HTTP for self-hosted servers) the test says so rather than pretending it is ideal.
 */
class ReleaseHygieneInvariantsTest {

    private val module = File(".")
    private val manifest by lazy { File(module, "src/main/AndroidManifest.xml").readText() }
    private val buildGradle by lazy { File(module, "build.gradle").readText() }

    private fun attrs(tag: String): List<String> =
        Regex("<$tag\\b[^>]*>", RegexOption.DOT_MATCHES_ALL).findAll(manifest).map { it.value }.toList()

    private fun nameOf(element: String) = Regex("android:name=\"([^\"]+)\"").find(element)!!.groupValues[1]

    @Test
    fun `the installable identity and version are the audited ones`() {
        assertTrue(buildGradle.contains("applicationId \"io.github.churipakinti.taki\""))
        assertTrue(buildGradle.contains("versionName \"0.1.0-beta\""))
        // debug builds install side by side and never reuse the release id
        assertTrue(buildGradle.contains("applicationIdSuffix = '.debug'"))
    }

    @Test
    fun `release is minified, shrunk and never debuggable`() {
        val release = buildGradle.substringAfter("release {").substringBefore("debug {")
        assertTrue(release.contains("minifyEnabled true"))
        assertTrue(release.contains("shrinkResources = true"))
        assertFalse("debuggable must not be forced on", buildGradle.contains("debuggable true"))
        assertFalse(manifest.contains("android:debuggable"))
    }

    @Test
    fun `release signing material is never read from a tracked path`() {
        // keystore.properties is gitignored; the build only warns (unsigned APK) when it is absent
        assertTrue(buildGradle.contains("keystore.properties"))
        assertTrue(File("../.gitignore").readText().contains("keystore.properties"))
    }

    @Test
    fun `the exported components are exactly the audited, intentional ones`() {
        val exported = listOf("activity", "service", "receiver", "provider")
            .flatMap { tag -> attrs(tag).filter { it.contains("android:exported=\"true\"") }.map(::nameOf) }
            .toSortedSet()
        assertEquals(
            setOf(
                ".activity.NavigationActivity", // launcher
                ".service.PlaybackService", // MediaBrowser/MediaLibrary clients (Android Auto, system UI)
                ".receiver.UltrasonicIntentReceiver", // legacy remote-control broadcast actions
                ".receiver.BluetoothIntentReceiver", // system Bluetooth broadcasts
                "androidx.media3.session.MediaButtonReceiver", // media buttons
                ".provider.AlbumArtContentProvider", // widget/launcher artwork (path hardened in 2026-08)
            ).toSortedSet(),
            exported,
        )
    }

    @Test
    fun `the non-exported components stay non-exported`() {
        val internal = listOf("service", "provider")
            .flatMap { tag -> attrs(tag).filter { it.contains("android:exported=\"false\"") }.map(::nameOf) }
        assertTrue(internal.contains(".service.DownloadService"))
        assertTrue(internal.contains(".provider.SearchSuggestionProvider"))
    }

    @Test
    fun `cloud and device backup never carry the plaintext server credentials database`() {
        for (file in listOf("src/main/res/xml/backup_rules.xml", "src/main/res/xml/backup_descriptor.xml")) {
            val text = File(module, file).readText()
            assertTrue("$file must exclude the credentials database", text.contains("path=\"ultrasonic-database\""))
            assertTrue(text.contains("ultrasonic-database-wal"))
        }
        assertTrue(manifest.contains("@xml/backup_rules"))
        assertTrue(manifest.contains("@xml/backup_descriptor"))
    }

    @Test
    fun `cleartext HTTP and user CAs are an accepted self-hosting trade-off, pinned so it stays deliberate`() {
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"true\""))
        val config = File(module, "src/main/res/xml/network_security_config.xml").readText()
        assertTrue(config.contains("cleartextTrafficPermitted=\"true\""))
        assertTrue(config.contains("src=\"user\""))
    }

    @Test
    fun `the permission set is the audited one`() {
        val permissions = Regex("<uses-permission android:name=\"([^\"]+)\"").findAll(manifest)
            .map { it.groupValues[1].removePrefix("android.permission.") }.toSortedSet()
        assertEquals(
            setOf(
                "BLUETOOTH", "BLUETOOTH_CONNECT", "INTERNET", "ACCESS_NETWORK_STATE", "POST_NOTIFICATIONS",
                "WAKE_LOCK", "MODIFY_AUDIO_SETTINGS", "FOREGROUND_SERVICE",
                "FOREGROUND_SERVICE_MEDIA_PLAYBACK", "FOREGROUND_SERVICE_DATA_SYNC",
            ).toSortedSet(),
            permissions,
        )
    }

    @Test
    fun `HTTP request logging is compiled in for debug builds only`() {
        val module = File("src/main/kotlin/org/moire/ultrasonic/di/MusicServiceModule.kt").readText()
        assertTrue("API client debug logging must follow BuildConfig.DEBUG", module.contains("debug = BuildConfig.DEBUG"))
        val app = File("src/main/kotlin/org/moire/ultrasonic/app/UApp.kt").readText()
        assertTrue(
            Regex("""if \(BuildConfig\.DEBUG\)\s*\{\s*Timber\.plant\(DebugTree\(\)\)""").containsMatchIn(app),
        )
    }
}
