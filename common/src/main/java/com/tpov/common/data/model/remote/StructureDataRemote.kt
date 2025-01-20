package com.tpov.common.data.model.remote

import com.tpov.common.domain.model.StructureDataLocal

data class StructureDataRemote(
    val id: Int? = null,
    val childes: List<StructureDataRemote>? = null,
    val nameItem: String = "",
    val dataUpdate: String = "", // for syncs
    val dataCreate: String = "",
    val version: Int = 0,       //for show update user
    val ratingGlobal: Int = 0,
    val starsAverageGlobal: Int = 0,
    val starsMaxGlobal: Int = 0,
    val tpovIdCreator: Int = 0,
    val nameCreator: String = "",
    val tpovIdMaxStarsGlobal: Int = 0,
    val picture: String = "",
    var languages: String = "",
    val isShowArchive: Boolean = false,
    val isShow: Boolean = true
) {
    fun toStructureDataLocal(): StructureDataLocal = StructureDataLocal(
        id,
        childes.orEmpty().map { it.toStructureDataLocal() }.toMutableList(),
        nameItem,
        dataUpdate,
        dataCreate,
        version,
        ratingGlobal,
        -1,
        -1,
        starsMaxGlobal,
        -1,
        starsAverageGlobal,
        -1,
        -1,
        tpovIdCreator,
        nameCreator,
        tpovIdMaxStarsGlobal,
        languages,
        picture,
        isShowArchive
    )
}
