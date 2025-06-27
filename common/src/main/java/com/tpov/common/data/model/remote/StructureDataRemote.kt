package com.tpov.common.data.model.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.tpov.common.data.model.local.StructureDataLocal

@IgnoreExtraProperties
data class StructureDataRemote(
    val children: List<StructureDataRemote>? = null,
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
    @PropertyName("isShowArchive")
    val isShowArchive: Boolean? = null,
    @PropertyName("isShow")
    val isShow: Boolean? = null
) {
    // No-argument constructor for Firebase
    constructor() : this(
        children = null,
        nameItem = "",
        dataUpdate = "",
        dataCreate = "",
        version = 0,
        ratingGlobal = 0,
        starsAverageGlobal = 0,
        starsMaxGlobal = 0,
        tpovIdCreator = 0,
        nameCreator = "",
        tpovIdMaxStarsGlobal = 0,
        picture = "",
        languages = "",
        isShowArchive = null,
        isShow = null
    )

    fun toStructureDataLocal(): StructureDataLocal = StructureDataLocal(
        children = children?.map { it.toStructureDataLocal() }?.toMutableList(),
        nameItem = nameItem,
        dataUpdateGlobal = dataUpdate,
        dataUpdateLocal = "",
        dataCreate = dataCreate,
        version = version,
        ratingGlobal = ratingGlobal,
        ratingLocal = -1,
        starsMaxLocal = -1,
        starsMaxGlobal = starsMaxGlobal,
        starsAverageLocal = -1,
        starsAverageGlobal = starsAverageGlobal,
        numHQ = 0,
        numQ = 0,
        tpovIdCreator = tpovIdCreator,
        nameCreator = nameCreator,
        tpovIdMaxStarsGlobal = tpovIdMaxStarsGlobal,
        languages = languages,
        picture = picture,
        isShowDownload = true,
        isShowArchive = isShowArchive ?: true
    )
}
