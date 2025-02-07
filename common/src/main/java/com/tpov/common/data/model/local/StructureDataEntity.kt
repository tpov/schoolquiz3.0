package com.tpov.common.data.model.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.manager.Converters
import com.tpov.common.domain.model.StructureDataLocal

@Entity(tableName = "structure_data")
data class StructureDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val childesJson: String? = null,
    val nameItem: String = "",
    val dataUpdate: String = "", // for syncs
    val dataCreate: String = "",
    val version: Int = 0,       // for show update user
    val ratingGlobal: Int = 0,
    val ratingLocal: Int = 0,
    var starsMaxLocal: Int = 0,
    val starsMaxGlobal: Int = 0,
    var starsAverageLocal: Int = 0,
    val starsAverageGlobal: Int = 0,
    val numHQ: Int = 0,
    val numQ: Int = 0,
    val tpovIdCreator: Int = 0,
    val nameCreator: String = "",
    val tpovIdMaxStarsGlobal: Int = 0,
    var languages: String = "",
    val picture: String = "",
    val isShowDownload: Boolean = false,
    val isShowArchive: Boolean = false
) {
    fun toStructureDataLocal(): StructureDataLocal {
        val childesList = Converters().toChildesList(childesJson ?: "[]")
        return StructureDataLocal(
            id = id,
            children = childesList?.map { it.toStructureDataLocal() }?.toMutableList(),
            nameItem = nameItem,
            dataUpdate = dataUpdate,
            dataCreate = dataCreate,
            version = version,
            ratingGlobal = ratingGlobal,
            ratingLocal = ratingLocal,
            starsMaxLocal = starsMaxLocal,
            starsMaxGlobal = starsMaxGlobal,
            starsAverageLocal = starsAverageLocal,
            starsAverageGlobal = starsAverageGlobal,
            numHQ = numHQ,
            numQ = numQ,
            tpovIdCreator = tpovIdCreator,
            nameCreator = nameCreator,
            tpovIdMaxStarsGlobal = tpovIdMaxStarsGlobal,
            languages = languages,
            picture = picture,
            isShowDownload = isShowDownload,
            isShowArchive = isShowArchive
        )
    }
}
