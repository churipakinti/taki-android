/*
 * ServerSettingCredentialExposureAuditTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5B privacy audit - finding **BF-3** (documented, NOT fixed here).
 *
 * `ServerSetting` is a plain Kotlin `data class`, so its generated `toString()` includes the
 * plaintext `password`. `ServerSettingsModel` interpolates whole `ServerSetting`s into `Timber`
 * messages when a server is added, updated or re-indexed. In a release build Timber has no tree -
 * unless the user switches on "debug log to file" (`FileLoggerTree`), the very feature a beta
 * tester is asked to enable to attach logs to a bug report: then the password is written to the
 * shareable log file.
 *
 * Both halves are pinned as *current behaviour* so the branch stays green and the evidence is
 * executable. When the defect is fixed (redacting `toString`, or not logging the object), these two
 * tests flip and must be updated deliberately.
 */
class ServerSettingCredentialExposureAuditTest {

    private val secret = "hunter2-not-a-real-password"

    private fun setting() = ServerSetting(
        id = 1, index = 1, name = "Home", url = "https://music.example", userName = "alice",
        password = secret, jukeboxByDefault = false, allowSelfSignedCertificate = false,
        forcePlainTextPassword = false, musicFolderId = null, minimumApiVersion = null,
    )

    @Test
    fun `BF-3 the generated toString of ServerSetting contains the plaintext password`() {
        assertTrue(
            "BF-3 is fixed: ServerSetting.toString no longer leaks the password - update this audit",
            setting().toString().contains(secret),
        )
    }

    @Test
    fun `BF-3 ServerSettingsModel still logs whole ServerSetting objects`() {
        val source = File("src/main/kotlin/org/moire/ultrasonic/model/ServerSettingsModel.kt").readLines()
        val loggedWholeObjects = source.count { line ->
            line.contains("Timber.") && (line.contains("\$serverSetting") || line.contains("\$setting"))
        }
        assertEquals(
            "BF-3 call sites changed: re-audit the credential exposure and update this test",
            3,
            loggedWholeObjects,
        )
    }
}
