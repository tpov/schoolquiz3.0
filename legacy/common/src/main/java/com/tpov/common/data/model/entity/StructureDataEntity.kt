package com.tpov.common.data.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.common.data.manager.Converters
import com.tpov.common.data.model.local.StructureDataLocal

@Entity(tableName = "structure_data")
data class StructureDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val childesJson: String? = null,
    val nameItem: String = "",
    val dataUpdateGlobal: String = "", // for syncs
    val dataUpdateLocal: String = "", // for syncs
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
    val isShowArchive: Boolean = true,
    
    // 🔍 Семантический поиск (хранится как строка для Room)
    val searchVector: List<Float>? = null,  // Будет автоматически конвертироваться TypeConverter'ом
    val vectorVersion: Int = 0
) {
    private fun toStructureDataLocal(): StructureDataLocal {
        val childesList = Converters().toChildesList(childesJson ?: "[]")
        return StructureDataLocal(
            children = childesList?.map { it.toStructureDataLocal() }?.toMutableList(),
            nameItem = nameItem,
            dataUpdateGlobal = dataUpdateGlobal,
            dataUpdateLocal = dataUpdateLocal,
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
            isShowArchive = isShowArchive,
            searchVector = searchVector,
            vectorVersion = vectorVersion
        )
    }

    fun toStructureCategoryListLocal(): List<StructureDataLocal>? {
        return this.toStructureDataLocal().children
    }
}
