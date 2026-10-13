/*
 * ShareCardStoreTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.xmlpull.v1.XmlPullParser
import org.robolectric.RobolectricTestRunner

/** Issue #20: temporary file handling and the Sharesheet intent. */
@RunWith(RobolectricTestRunner::class)
class ShareCardStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000_000L
    private fun store(dir: File = folder.root) = ShareCardStore(dir) { now }
    private fun tiny() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF336699.toInt()) }
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `write creates a decodable png under cache share`() {
        val file = store().write(Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888))
        assertEquals("share", file.parentFile!!.name)
        val decoded = BitmapFactory.decodeFile(file.path)
        assertEquals(1080, decoded.width)
        assertEquals(1920, decoded.height)
    }

    @Test
    fun `file names carry only a timestamp and counter`() {
        val name = store().write(tiny()).name
        assertTrue(name, Regex("taki-share-\\d+-\\d+\\.png").matches(name))
    }

    @Test
    fun `old files are pruned to a bounded number`() {
        val s = store()
        repeat(7) {
            now += 1_000
            s.write(tiny()).setLastModified(now)
        }
        assertTrue(s.files().size <= SHARE_CARD_MAX_FILES)
    }

    @Test
    fun `files older than a day are removed even under the cap`() {
        val s = store()
        val old = s.write(tiny())
        old.setLastModified(now)
        now += SHARE_CARD_MAX_AGE_MS + 1
        val fresh = s.write(tiny())
        fresh.setLastModified(now)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `the file just written is never pruned`() {
        val s = store()
        repeat(5) {
            now += 10
            s.write(tiny()).setLastModified(now)
        }
        val newest = s.write(tiny())
        assertTrue(newest.exists())
    }

    @Test
    fun `share intent is an image png send with a content uri and read grant`() {
        val uri = Uri.parse("content://${context.packageName}.shareprovider/share/taki-share-1-0.png")
        val chooser = ShareCardStore.shareIntent(uri, "Share")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.shareprovider", uri.authority)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val clip: ClipData = send.clipData!!
        assertEquals(uri, clip.getItemAt(0).uri)
    }

    @Test
    fun `the file provider exposes only the share cache directory`() {
        // androidx FileProvider cannot resolve paths on a Windows host, so rather than skipping,
        // assert the property that matters: the provider config lists exactly one root,
        // cache/share/, and nothing else (no files/, no external storage, no root-path).
        val parser = context.resources.getXml(R.xml.share_file_paths)
        val roots = mutableListOf<Pair<String, String?>>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name != "paths") {
                roots += parser.name to parser.getAttributeValue(null, "path")
            }
        }
        assertEquals(listOf("cache-path" to "share/"), roots)
    }

    @Test
    fun `the provider is registered non exported with grantable uri permissions`() {
        val info = context.packageManager.resolveContentProvider(
            ShareCardStore.authority(context),
            PackageManager.GET_META_DATA,
        )
        assertNotNull(info)
        assertFalse(info!!.exported)
        assertTrue(info.grantUriPermissions)
    }
}
