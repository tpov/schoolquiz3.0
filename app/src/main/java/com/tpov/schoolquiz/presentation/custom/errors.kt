package com.tpov.schoolquiz.presentation.custom

import android.widget.Toast
import androidx.fragment.app.FragmentActivity

object Errors {
    fun errorGetLvlTranslate(context: FragmentActivity?): Int {
        Toast.makeText(context, "ошибка получения lvlTranslate user", Toast.LENGTH_LONG).show()
        return 0
    }
}