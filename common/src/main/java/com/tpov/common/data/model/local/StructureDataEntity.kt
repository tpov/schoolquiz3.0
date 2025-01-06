package com.tpov.common.data.model.local

import com.tpov.common.data.model.remote.QuizRemote

data class StructureDataEntity(
    val item: List<StructureEntityItem> = emptyList()
)

data class StructureEntityItem(
    val id: Int = 0,
    val childes: List<StructureEntityItem> = emptyList(),
    val quizList: List<QuizRemote?> = emptyList(),
    val nameItem: String = "",
    val dataUpdate: String = "", // for syncs
    val version: Int = 0,       //for show update user
    val ratingGlobal: Int = 0,
    val tpovIdCreator: Int = 0,
    val nameCreator: Int = 0,
    val tpovIdMax: Int = 0,
    val picture: String = "",
)
