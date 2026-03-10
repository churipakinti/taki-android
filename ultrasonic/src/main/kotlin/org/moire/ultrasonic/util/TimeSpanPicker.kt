/*
 * TimeSpanPicker.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import java.util.concurrent.TimeUnit
import org.moire.ultrasonic.R
import timber.log.Timber

/**
 * UI Dialog which allow the User to pick a duration ranging from milliseconds to month
 */
class TimeSpanPicker(private var mContext: Context, attrs: AttributeSet?, defStyle: Int) :
    LinearLayout(
        mContext,
        attrs,
        defStyle
    ),
    AdapterView.OnItemSelectedListener {
    private val timeSpanEditText: EditText
    private val timeSpanSpinner: Spinner
    private val timeSpanDisableCheckbox: CheckBox
    private var mTimeSpan: Long = -1L
    private val adapter: ArrayAdapter<CharSequence>
    private val dialog: View

    constructor(context: Context) : this(context, null) {
        this.mContext = context
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {
        this.mContext = context
    }

    init {
        val inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        dialog = inflater.inflate(R.layout.time_span_dialog, this, true)
        timeSpanEditText = dialog.findViewById<View>(R.id.timeSpanEditText) as EditText
        timeSpanEditText.setText("0")
        timeSpanSpinner = dialog.findViewById<View>(R.id.timeSpanUnitSelector) as Spinner
        timeSpanDisableCheckbox =
            dialog.findViewById<View>(R.id.timeSpanDisableCheckBox) as CheckBox
        timeSpanDisableCheckbox.setOnCheckedChangeListener { _, b ->
            timeSpanEditText.isEnabled = !b
            timeSpanSpinner.isEnabled = !b
        }
        adapter = ArrayAdapter.createFromResource(
            mContext,
            R.array.shareExpirationNames,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        timeSpanSpinner.adapter = adapter
        timeSpanSpinner.onItemSelectedListener = this
    }

    override fun setEnabled(enabled: Boolean) {
        timeSpanEditText.isEnabled = enabled
        timeSpanSpinner.isEnabled = enabled
    }

    fun getTimeSpan(): Long = if (!timeSpanDisableCheckbox.isChecked) {
        getTimeSpanFromDialog(
            mContext,
            dialog
        )
    } else {
        -1L
    }

    val timeSpanEnabled: Boolean
        get() = !timeSpanDisableCheckbox.isChecked
    var timeSpanType: String?
        get() {
            return timeSpanSpinner.selectedItem as String
        }
        set(type) {
            timeSpanSpinner.setSelection(adapter.getPosition(type))
        }
    val timeSpanAmount: Int
        get() {
            val text = timeSpanEditText.text
            var timeSpanAmountString: String? = null
            if (text != null) {
                timeSpanAmountString = text.toString()
            }
            var timeSpanAmount = 0
            if (timeSpanAmountString != null && "" != timeSpanAmountString) {
                timeSpanAmount = timeSpanAmountString.toInt()
            }
            return timeSpanAmount
        }

    fun setTimeSpanAmount(amount: CharSequence?) {
        timeSpanEditText.setText(amount)
    }

    fun setTimeSpanDisableText(text: CharSequence?) {
        timeSpanDisableCheckbox.text = text
    }

    fun setTimeSpanDisableCheckboxChecked(checked: Boolean) {
        timeSpanDisableCheckbox.isChecked = checked
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
        val timeSpanType = parent.getItemAtPosition(pos) as String
        val text = timeSpanEditText.text ?: return
        val timeSpanAmountString = text.toString()
        var timeSpanAmount = 0L
        if ("" != timeSpanAmountString) {
            timeSpanAmount = timeSpanAmountString.toLong()
        }
        mTimeSpan = calculateTimeSpan(mContext, timeSpanType, timeSpanAmount)
    }

    override fun onNothingSelected(adapterView: AdapterView<*>?) {}

    companion object {
        fun getTimeSpanFromDialog(context: Context, dialog: View): Long {
            val timeSpanEditText = dialog.findViewById<View>(R.id.timeSpanEditText) as EditText
            val timeSpanSpinner = dialog.findViewById<View>(R.id.timeSpanUnitSelector) as Spinner
            val timeSpanType = timeSpanSpinner.selectedItem as String
            Timber.i("SELECTED ITEM: %d", timeSpanSpinner.selectedItemId)
            val text = timeSpanEditText.text
            val timeSpanAmountString: String? = text?.toString()
            var timeSpanAmount = 0L

            if (timeSpanAmountString != null && timeSpanAmountString != "") {
                timeSpanAmount = timeSpanAmountString.toLong()
            }

            return calculateTimeSpan(context, timeSpanType, timeSpanAmount)
        }

        fun calculateTimeSpan(context: Context, timeSpanType: String, timeSpanAmount: Long): Long {
            val resources = context.resources

            return when (timeSpanType) {
                resources.getText(R.string.settings_share_minutes) -> {
                    TimeUnit.MINUTES.toMillis(timeSpanAmount)
                }

                resources.getText(R.string.settings_share_hours) -> {
                    TimeUnit.HOURS.toMillis(timeSpanAmount)
                }

                resources.getText(R.string.settings_share_days) -> {
                    TimeUnit.DAYS.toMillis(timeSpanAmount)
                }

                else -> TimeUnit.MINUTES.toMillis(0L)
            }
        }
    }
}
