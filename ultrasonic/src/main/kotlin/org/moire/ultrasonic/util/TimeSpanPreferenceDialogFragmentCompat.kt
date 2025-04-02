/*
 * TimeSpanPreferenceDialogFragmentCompat.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import android.view.View
import androidx.core.content.edit
import androidx.preference.DialogPreference.TargetFragment
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import java.util.Locale
import org.moire.ultrasonic.R

class TimeSpanPreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat(), TargetFragment {
    var picker: TimeSpanPicker? = null

    override fun onCreateDialogView(context: Context): View? {
        picker = TimeSpanPicker(context)
        val disabledText = requireContext().resources.getString(R.string.no_expiration)
        picker!!.setTimeSpanDisableText(disabledText)
        val persisted = Settings.defaultShareExpiration
        if ("" != persisted) {
            val split = Settings.COLON_PATTERN.split(persisted)
            if (split.size == 2) {
                val amount = split[0]
                if ("0" == amount || "" == amount) {
                    picker!!.setTimeSpanDisableCheckboxChecked(true)
                }
                picker!!.setTimeSpanAmount(amount)
                picker!!.timeSpanType = split[1]
            }
        } else {
            picker!!.setTimeSpanDisableCheckboxChecked(true)
        }
        return picker
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        var persisted = ""
        if (picker!!.timeSpanEnabled) {
            val tsAmount = picker!!.timeSpanAmount
            if (tsAmount > 0) {
                val tsType = picker!!.timeSpanType
                persisted = String.format(Locale.US, "%d:%s", tsAmount, tsType)
            }
        }
        val preference: Preference = preference
        preference.sharedPreferences!!.edit { putString(preference.key, persisted) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Preference?> findPreference(p0: CharSequence): T {
        return preference as T
    }
}
