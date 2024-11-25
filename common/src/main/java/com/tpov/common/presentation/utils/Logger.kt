package com.tpov.common.presentation.utils


object Logger {
    fun log(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }
}
