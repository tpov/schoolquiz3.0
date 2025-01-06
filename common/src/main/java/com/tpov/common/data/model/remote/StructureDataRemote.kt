package com.tpov.common.data.model.remote

data class StructureDataRemote(
    val item: List<StructureRemoteItem> = emptyList()
)

data class StructureRemoteItem(
    val id: Int = 0,
    val childes: List<StructureRemoteItem> = emptyList(),
    val nameItem: String = "",
    val dataUpdate: String = "", // for syncs
    val version: Int = 0,       //for show update user
    val ratingGlobal: Int = 0,
    val tpovIdCreator: Int = 0,
    val nameCreator: Int = 0,
    val tpovIdMax: Int = 0,
    val picture: String = "",
)
