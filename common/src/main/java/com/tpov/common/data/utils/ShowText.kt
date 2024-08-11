package com.tpov.common.data.utils

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ShowText {
    fun showTextWithDelay(textView: TextView, text: String, delayInMillis: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            val spannableText = SpannableStringBuilder()

            delay(200)
            for (char in text) {
                val start = spannableText.length
                spannableText.append(char.toString())
                spannableText.setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    start, start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = spannableText
                delay(delayInMillis)

                spannableText.setSpan(
                    ForegroundColorSpan(Color.BLACK),
                    start, start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = spannableText
            }
        }
    }
}