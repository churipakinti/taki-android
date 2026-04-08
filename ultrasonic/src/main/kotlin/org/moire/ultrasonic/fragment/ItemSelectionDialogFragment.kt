/*
 * ItemSelectionDialogFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.moire.ultrasonic.R

// Displays a list of items to select from in a dialog. Returns either RESULT_CANCELLED=true or
// RESULT_SELECTED_ITEM="foo" as a string.
class ItemSelectionDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val titleId = arguments?.getInt(ARGS_TITLE) ?: R.string.common_title
        val items = arguments?.getStringArray(ARGS_ITEMS) ?: emptyArray()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleId)
            .setItems(items) { _, which ->
                val result = Bundle()
                result.putString(RESULT_SELECTED_ITEM, items[which])
                setFragmentResult(REQUEST_KEY, result)
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        val result = Bundle()
        result.putBoolean(RESULT_CANCELLED, true)
        setFragmentResult(REQUEST_KEY, result)
    }

    companion object {
        const val TAG = "ItemSelectionDialog"
        const val REQUEST_KEY = "ItemSelectionRequest"
        const val RESULT_SELECTED_ITEM = "SelectedItem"
        const val RESULT_CANCELLED = "Cancelled"
        const val ARGS_TITLE = "Title"
        const val ARGS_ITEMS = "Items"

        fun create(@StringRes titleId: Int, items: Array<String>): ItemSelectionDialogFragment {
            val frag = ItemSelectionDialogFragment()
            val args = Bundle()
            args.putInt(ARGS_TITLE, titleId)
            args.putStringArray(ARGS_ITEMS, items)
            frag.setArguments(args)
            return frag
        }
    }
}
