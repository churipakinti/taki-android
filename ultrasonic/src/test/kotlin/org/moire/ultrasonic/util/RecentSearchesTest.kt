package org.moire.ultrasonic.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentSearchesTest {
    private lateinit var recentSearches: RecentSearches

    @BeforeTest
    fun setUp() {
        recentSearches = RecentSearches(ApplicationProvider.getApplicationContext<Context>())
        recentSearches.clear()
    }

    @Test
    fun `normalizes duplicates and keeps latest capitalization`() {
        recentSearches.save("  Queens  ")
        recentSearches.save("Beatles")
        recentSearches.save("QUEENS")

        assertEquals(listOf("QUEENS", "Beatles"), recentSearches.get())
    }

    @Test
    fun `ignores empty values and limits history to ten`() {
        recentSearches.save("   ")
        (1..12).forEach { recentSearches.save("Query $it") }

        assertEquals(10, recentSearches.get().size)
        assertEquals("Query 12", recentSearches.get().first())
        assertEquals("Query 3", recentSearches.get().last())
    }

    @Test
    fun `removes only requested query ignoring case`() {
        recentSearches.save("Mozart")
        recentSearches.save("Radiohead")

        assertEquals(listOf("Radiohead"), recentSearches.remove("MOZART"))
    }
}
