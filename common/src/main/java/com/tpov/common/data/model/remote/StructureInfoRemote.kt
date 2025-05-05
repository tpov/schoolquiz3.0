package com.tpov.common.data.model.remote

import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.presentation.model.PathStructure

data class StructureInfoRemote(
    val rating: Int,
    val starsMax: Int,
    val starsAverage: Int,
    val countGame: Int,
    val dataRating: String,
    val versionQuiz: Int,
    val tpovId: Int,
    val languages: String,
    val isShowArchive: Boolean
) {
    fun toStructureInfoEntity(
        pathStructure: PathStructure
    ) = StructureInfoEntity(
        null, pathStructure, dataRating,
        tpovId, rating, starsMax, starsAverage, countGame, languages, isShowArchive
    )
}