package com.tpov.schoolquiz.presentation.core

import android.app.Activity
import android.graphics.drawable.Drawable
import com.tpov.schoolquiz.R

object getResources {

    fun getUserIcon(icon: String, context: Activity): Drawable? {
        return when (icon) {
            "star" -> context.resources.getDrawable(R.drawable.star_full)
            else -> context.resources.getDrawable(R.drawable.common_google_signin_btn_icon_dark)
        }
    }
}