package com.tpov.common.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PathStructure(
    var nameEvent: String = "",
    var nameCategory: String = "",
    var nameSubCategory: String = "",
    var nameSubsubCategory: String = "",
    var nameQuiz: String = "",
) : Parcelable {
        fun toPath() = "$nameCategory>$nameSubCategory>$nameSubsubCategory>$nameQuiz"
}
