package com.tpov.setting.presentation

import android.content.Context
import android.content.res.TypedArray
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.TimePicker
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.tpov.setting.R

internal class TimePickerPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    var time: String = "00:00"
        set(value) {
            field = value
            persistString(value)
        }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        time = getPersistedString(defaultValue as? String ?: "00:00")
    }
}

internal class TimePickerPreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat() {

    private lateinit var timePicker: TimePicker

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        timePicker = view.findViewById(R.id.time_picker)
        val time = (preference as TimePickerPreference).time.split(":")
        timePicker.hour = time[0].toInt()
        timePicker.minute = time[1].toInt()
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val time = String.format("%02d:%02d", timePicker.hour, timePicker.minute)
            if (preference.callChangeListener(time)) {
                (preference as TimePickerPreference).time = time
            }
        }
    }

    companion object {
        fun newInstance(key: String): TimePickerPreferenceDialogFragmentCompat {
            val fragment = TimePickerPreferenceDialogFragmentCompat()
            val b = Bundle(1)
            b.putString(ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}