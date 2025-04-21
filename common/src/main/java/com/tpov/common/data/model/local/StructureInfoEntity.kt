package com.tpov.common.data.model.local

import com.tpov.common.data.model.remote.StructureInfoRemote
import com.tpov.common.presentation.model.PathStructure

data class StructureInfoEntity(
    val id: Int?,
    val pathStructure: PathStructure,
    var dateUpdate: String,
    val tpovIdUser: Int,
    var rating: Int,
    var starsMax: Int,
    var starsAverage: Int,
    val countGame: Int
) {
    fun toStructureUserInfoRemote(dataRating: String, versionQuiz: Int) = StructureInfoRemote(
       rating, starsMax, starsAverage, countGame, dataRating, versionQuiz, tpovIdUser
    )
}