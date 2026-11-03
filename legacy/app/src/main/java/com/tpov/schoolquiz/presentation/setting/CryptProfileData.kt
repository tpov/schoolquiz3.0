package com.tpov.schoolquiz.presentation.setting

import android.graphics.Bitmap
import com.squareup.picasso.Picasso
import com.tpov.schoolquiz.R


object CryptProfileData {

    fun getProfileIcon(iconName: String): Bitmap? {
        return try {
            when (iconName) {
                "1" -> Picasso.get().load(R.drawable.baseline_download_24).get()
                "2" -> Picasso.get().load(R.drawable.baseline_favorite_24).get()
                "3" -> Picasso.get().load(R.drawable.baseline_favorite_24_empty).get()
                else -> Picasso.get().load(R.drawable.baseline_favorite_24_gold).get()
            }
        } catch (e: Exception) {
            null
        }
    }

}