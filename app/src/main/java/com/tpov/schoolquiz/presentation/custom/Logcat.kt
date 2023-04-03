package com.tpov.schoolquiz.presentation.custom

import android.util.Log
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity

object Logcat {

    const val LOG_ACTIVITY = 1
    const val LOG_VIEW_MODEL = 2
    const val LOG_FIREBASE = 3
    const val LOG_DATABASE = 4
    const val LOG_FRAGMENT = 5

    fun log(massage: String, className: String, type: Int) {
        if (type == LOG_ACTIVITY) Log.v("SchoolQuiz3.0 $className", massage)
        if (type == LOG_VIEW_MODEL) Log.i("SchoolQuiz3.0 $className", massage)
        if (type == LOG_FIREBASE) Log.d("SchoolQuiz3.0 $className", massage)
        if (type == LOG_DATABASE) Log.wtf("SchoolQuiz3.0 $className", massage)
        if (type == LOG_FRAGMENT) Log.w("SchoolQuiz3.0 $className", massage)
    }

}