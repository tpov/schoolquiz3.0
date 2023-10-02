package com.tpov.schoolquiz.presentation.custom

import android.util.Log
import com.tpov.schoolquiz.R

object Logcat {

    const val LOG_ACTIVITY = 1
    const val LOG_VIEW_MODEL = 2
    const val LOG_FIREBASE = 3
    const val LOG_DATABASE = 4
    const val LOG_FRAGMENT = 5

    fun log(massage: String, className: String, type: Int) {
        if (type == LOG_ACTIVITY) Log.v("${R.string.app_name_full} $className", massage)
        if (type == LOG_VIEW_MODEL) Log.i("${R.string.app_name_full} $className", massage)
        if (type == LOG_FIREBASE) Log.d("${R.string.app_name_full} $className", massage)
        if (type == LOG_DATABASE) Log.wtf("${R.string.app_name_full} $className", massage)
        if (type == LOG_FRAGMENT) Log.w("${R.string.app_name_full} $className", massage)
    }

}