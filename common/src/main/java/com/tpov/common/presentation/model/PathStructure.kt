package com.tpov.common.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PathStructure(
    var idEvent: Int,
    var idCategory: Int,
    var idSubCategory: Int,
    var idSubsubCategory: Int,
    var idQuiz: Int,
) : Parcelable