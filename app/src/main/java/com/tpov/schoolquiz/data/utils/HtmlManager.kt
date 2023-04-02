package com.tpov.shoppinglist.utils

import android.text.Html
import android.text.Spanned

object HtmlManager {
    fun getFormatHtml(text: String): Spanned {
        return if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(text)
        } else {
            Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
        }
    }
    fun toHtml(text: Spanned): String {
        return if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.N) {
            Html.toHtml(text)
        } else {
            Html.toHtml(text, Html.FROM_HTML_MODE_COMPACT)
        }
    }
}