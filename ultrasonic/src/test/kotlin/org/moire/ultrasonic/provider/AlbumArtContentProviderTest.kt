/*
 * AlbumArtContentProviderTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.provider

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import java.io.File
import java.lang.reflect.Method
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.FileUtil
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage for the P0-1 security fix in [AlbumArtContentProvider]. This provider is
 * `android:exported="true"` (required so the home-screen widget's `RemoteViews.setImageViewUri`
 * can be resolved by the launcher process -- a different app UID with no automatic URI-grant for
 * that API, confirmed before this fix was written), so its inputs must be treated as fully
 * untrusted:
 *
 * 1. The cache-key path segment must not be able to escape the artwork directory
 *    (path traversal).
 * 2. The cover-art id path segment must not let an external caller make Taki fetch an arbitrary,
 *    caller-chosen id from the user's authenticated server (confused deputy) -- it must match an
 *    (id, cacheKey) pair Taki itself already minted via [AlbumArtContentProvider
 *    .mapArtworkToContentProviderUri].
 */
@RunWith(RobolectricTestRunner::class)
class AlbumArtContentProviderTest {

    private val imageLoaderProvider: ImageLoaderProvider = mock()
    private lateinit var provider: AlbumArtContentProvider
    private lateinit var albumArtDir: File

    private val companionInstance: Any =
        AlbumArtContentProvider::class.java.getDeclaredField("Companion").get(null)!!

    @Before
    fun setUp() {
        FileUtil.cachedUltrasonicDirectory = File(
            Environment.getExternalStorageDirectory(),
            "Android/data/org.moire.ultrasonic.test"
        )
        albumArtDir = FileUtil.albumArtDirectory
        albumArtDir.mkdirs()
        clearKnownArtwork()

        provider = AlbumArtContentProvider()
        val delegateField = AlbumArtContentProvider::class.java
            .getDeclaredField("imageLoaderProvider\$delegate")
        delegateField.isAccessible = true
        delegateField.set(provider, lazy { imageLoaderProvider })
    }

    @After
    fun tearDown() {
        albumArtDir.deleteRecursively()
        clearKnownArtwork()
    }

    @Suppress("UNCHECKED_CAST")
    private fun knownArtworkMap(): MutableMap<String, String> {
        // The Kotlin compiler hoists this companion-object property's backing field onto the
        // outer class as a static field (visible via javap), not onto the Companion class itself.
        val field = AlbumArtContentProvider::class.java.getDeclaredField("knownArtwork")
        field.isAccessible = true
        return field.get(null) as MutableMap<String, String>
    }

    private fun clearKnownArtwork() = knownArtworkMap().clear()

    private fun registerKnownArtwork(coverArtId: String, cacheKey: String) {
        knownArtworkMap()[cacheKey] = coverArtId
    }

    private fun isContainedIn(file: File, directory: File): Boolean {
        val method: Method = companionInstance.javaClass.getDeclaredMethod(
            "isContainedIn",
            File::class.java,
            File::class.java
        )
        method.isAccessible = true
        return method.invoke(companionInstance, file, directory) as Boolean
    }

    private fun uriFor(coverArtId: String, cacheKey: String): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority("org.moire.ultrasonic.provider.AlbumArtContentProvider")
        .path("$coverArtId|$cacheKey")
        .build()

    // 32 lowercase hex chars, matching the real MD5-hex format FileUtil.getAlbumArtKey produces.
    private val validCacheKey = "0123456789abcdef0123456789abcdef.jpeg"

    @Test
    fun `rejects traversal-style cache keys and never touches the image loader`() {
        val maliciousKeys = listOf(
            "../something.jpeg",
            "../../music/file.jpeg",
            "abc/def.jpeg",
            "abc\\def.jpeg",
            "${"a".repeat(32)}.jpeg/../../secret"
        )

        for (key in maliciousKeys) {
            registerKnownArtwork("legit-id", key)
            assertNull(provider.openFile(uriFor("legit-id", key), "r"), "key=$key should be rejected")
        }

        verify(imageLoaderProvider, never()).executeOn(any())
    }

    @Test
    fun `rejects an encoded traversal sequence that Uri decodes back to raw separators`() {
        // %7C = '|', %2F = '/' -- these decode back to literal separator characters once
        // Uri#getPath() is called, exactly as an external caller crafting a raw URI string
        // would attempt.
        val uri = Uri.parse(
            "content://org.moire.ultrasonic.provider.AlbumArtContentProvider/" +
                "legit-id%7C..%2F..%2Fsecret.jpeg"
        )

        assertNull(provider.openFile(uri, "r"))
        verify(imageLoaderProvider, never()).executeOn(any())
    }

    @Test
    fun `rejects a well-formed cache key paired with an unregistered cover art id`() {
        registerKnownArtwork("legit-id", validCacheKey)

        val uri = uriFor("attacker-chosen-id", validCacheKey)

        assertNull(provider.openFile(uri, "r"))
        verify(imageLoaderProvider, never()).executeOn(any())
    }

    @Test
    fun `rejects a cache key that was never minted by this app`() {
        // Well-formed, but never registered via mapArtworkToContentProviderUri().
        val uri = uriFor("some-id", validCacheKey)

        assertNull(provider.openFile(uri, "r"))
        verify(imageLoaderProvider, never()).executeOn(any())
    }

    @Test
    fun `a legitimate known pairing with cached artwork resolves normally`() {
        registerKnownArtwork("legit-id", validCacheKey)
        val cachedFile = File(FileUtil.getAlbumArtFile(validCacheKey))
        cachedFile.writeBytes(byteArrayOf(1, 2, 3))

        val pfd = provider.openFile(uriFor("legit-id", validCacheKey), "r")

        assertNotNull(pfd)
        pfd.close()
        verify(imageLoaderProvider).executeOn(any())
    }

    @Test
    fun `containment check is boundary-safe rather than a string prefix comparison`() {
        val base = File(albumArtDir, "sub").apply { mkdirs() }
        // Shares "sub" as a string prefix with the real directory's sibling name, but is a
        // completely different directory. A naive `path.startsWith(base.path)` check would
        // wrongly treat a file in here as contained.
        val sibling = File(albumArtDir, "sub-evil").apply { mkdirs() }
        val fileOutside = File(sibling, "leak.jpeg")
        val fileInside = File(base, "ok.jpeg")

        assertFalse(isContainedIn(fileOutside, base))
        assertTrue(isContainedIn(fileInside, base))
    }
}
