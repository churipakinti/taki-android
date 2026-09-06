/*
 * ArchitectureGuardTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cheap, dependency-free architecture guards for production Compose UI code under
 * `src/main/kotlin/org/moire/ultrasonic/ui`. Scans source text rather than pulling in
 * Konsist or a custom detekt rule (issue #9 asked for simple and maintainable).
 *
 * Enforced:
 *  1. no `androidx.media3.*` import anywhere under `ui` - playback state must arrive through
 *     `PlaybackUiStateHolder`, never a Media3 type;
 *  2. no `androidx.compose.material3.MaterialTheme` import outside `ui/theme` - screens must
 *     theme through `TakiTheme`;
 *  3. no raw `Color(0x...)` literal outside `ui/theme`;
 *  4. no raw `<number>.dp` / `<number>.sp` literal outside `ui/theme` - use a Taki token.
 *     A deliberate one-off (platform / inset maths) may end the line with `// taki-raw-ok`.
 *
 * See docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md sections 5.4 and 7.
 */
class ArchitectureGuardTest {

    private val uiRoot = File("src/main/kotlin/org/moire/ultrasonic/ui")

    private val uiSources: List<File> by lazy {
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun violations(predicate: (path: String, line: String) -> Boolean): List<String> {
        val hits = mutableListOf<String>()
        uiSources.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (predicate(file.invariantSeparatorsPath, line)) {
                    hits += "${file.name}:${index + 1}: ${line.trim()}"
                }
            }
        }
        return hits
    }

    @Test
    fun `ui code contains no androidx media3 imports`() {
        val hits = violations { _, line ->
            line.trimStart().startsWith("import androidx.media3.")
        }
        assertTrue(
            "Media3 types must not reach the ui layer:\n${hits.joinToString("\n")}",
            hits.isEmpty()
        )
    }

    @Test
    fun `MaterialTheme is only imported inside ui theme`() {
        val hits = violations { path, line ->
            line.trimStart().startsWith("import androidx.compose.material3.MaterialTheme") &&
                !path.contains("/ui/theme/")
        }
        assertTrue(
            "Screens must theme through TakiTheme, not MaterialTheme:\n${hits.joinToString("\n")}",
            hits.isEmpty()
        )
    }

    @Test
    fun `no raw Color literals outside ui theme`() {
        val rawColor = Regex("""Color\(\s*0x[0-9A-Fa-f]{6,8}""")
        val hits = violations { path, line ->
            !path.contains("/ui/theme/") && rawColor.containsMatchIn(line)
        }
        assertTrue(
            "Use a TakiColors token instead of a raw Color():\n${hits.joinToString("\n")}",
            hits.isEmpty()
        )
    }

    @Test
    fun `no raw dp or sp literals outside ui theme`() {
        val rawDimen = Regex("""(?<![A-Za-z0-9_])\d+(\.\d+)?\.(dp|sp)\b""")
        val hits = violations { path, line ->
            !path.contains("/ui/theme/") &&
                rawDimen.containsMatchIn(line) &&
                !line.trimEnd().endsWith("// taki-raw-ok")
        }
        assertTrue(
            "Use a Taki spacing/dimension token instead of a raw .dp/.sp " +
                "(or end the line with // taki-raw-ok for a deliberate one-off):\n" +
                hits.joinToString("\n"),
            hits.isEmpty()
        )
    }

    @Test
    fun `the ui source tree is actually being scanned`() {
        assertTrue("ui source root not found at ${uiRoot.absolutePath}", uiRoot.isDirectory)
        assertTrue("expected to scan several ui source files", uiSources.size >= 5)
    }
}
