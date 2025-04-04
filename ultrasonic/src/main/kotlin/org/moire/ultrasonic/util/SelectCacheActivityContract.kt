/*
 * SelectCacheActivityContract.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import org.moire.ultrasonic.fragment.SettingsFragment

class SelectCacheActivityContract : ActivityResultContract<String?, Uri?>() {
    override fun createIntent(context: Context, input: String?): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (Settings.cacheLocationUri != "") {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
        }
        intent.addFlags(SettingsFragment.RW_FLAG)
        intent.addFlags(SettingsFragment.PERSISTABLE_FLAG)
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (
            resultCode == Activity.RESULT_OK &&
            intent != null
        ) {
            val read = (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
            val write = (intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0
            val persist = (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0

            if (read && write && persist) {
                if (intent.data != null) {
                    // The result data contains a URI for the document or directory that
                    // the user selected.
                    return intent.data!!
                }
            }
        }
        return null
    }
}
