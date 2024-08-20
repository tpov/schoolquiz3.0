package com.tpov.network.domain.model

import android.graphics.Bitmap

data class ContactItem(
    var name: String?,
    var number: List<String>?,
    var mail: String?,
    var photo: Bitmap?
)
