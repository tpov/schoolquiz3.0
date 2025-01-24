package com.tpov.schoolquiz.presentation.create_quiz

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.AdapterView

class CustomSpinnerCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.spinnerStyle
) : androidx.appcompat.widget.AppCompatSpinner(context, attrs, defStyleAttr) {

    private var wasOpened = false
    private var userSelectedListener: ((Int) -> Unit)? = null

    override fun performClick(): Boolean {
        wasOpened = true
        Log.d("SpinnerDebug", "performClick: wasOpened set to true")
        return super.performClick()
    }

    fun setOnUserSelectedListener(listener: (Int) -> Unit) {
        userSelectedListener = listener
    }

    override fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        super.setOnItemSelectedListener(object : OnItemSelectedListener {
            private var isFirst = true

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Log.d("SpinnerDebug", "onItemSelected: pos=$position, wasOpened=$wasOpened, isFirst=$isFirst")
                
                if (isFirst) {
                    isFirst = false
                    return
                }

                if (wasOpened) {
                    Log.d("SpinnerDebug", "Calling userSelectedListener with position: $position")
                    userSelectedListener?.invoke(position)
                    wasOpened = false
                }
                
                listener?.onItemSelected(parent, view, position, id)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                listener?.onNothingSelected(parent)
                wasOpened = false
            }
        })
    }
}