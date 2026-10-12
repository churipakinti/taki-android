/*
 * SpanishResourceCompletenessTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.l10n

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #26 phase 5C.3: the Spanish UI fell back to English for ~125 strings added with the
 * Compose migration. Every translatable string in the base file must have a Spanish entry, so a
 * new string cannot ship to Spanish users as an English fallback.
 */
class SpanishResourceCompletenessTest {

    private fun keys(path: String): Set<String> =
        Regex("""<string\s+name="([^"]+)"([^>]*)>""")
            .findAll(File(path).readText())
            .filter { !it.groupValues[2].contains("translatable=\"false\"") }
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `every translatable base string has a Spanish translation`() {
        val missing = keys("src/main/res/values/strings.xml") - keys("src/main/res/values-es/strings.xml")
        assertTrue("Add Spanish translations for: ${missing.sorted()}", missing.isEmpty())
    }

    private fun plurals(path: String): Set<String> =
        Regex("""<plurals\s+name="([^"]+)"""").findAll(File(path).readText()).map { it.groupValues[1] }.toSet()

    @Test
    fun `every base plural has a Spanish translation`() {
        val missing = plurals("src/main/res/values/strings.xml") - plurals("src/main/res/values-es/strings.xml")
        assertTrue("Add Spanish plurals for: ${missing.sorted()}", missing.isEmpty())
    }
}
