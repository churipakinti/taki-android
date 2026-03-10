package org.moire.ultrasonic.util

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference
import org.moire.ultrasonic.R

/**
 * Created by Joshua Bahnsen on 12/22/13.
 */
class TimeSpanPreference(mContext: Context, attrs: AttributeSet?) :
    DialogPreference(
        mContext,
        attrs
    ) {
    init {
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
        dialogIcon = null
    }

    val text: String
        get() {
            val persisted = getPersistedString("")
            return if ("" != persisted) {
                persisted.replace(':', ' ')
            } else {
                context.resources.getString(R.string.time_span_disabled)
            }
        }
}
