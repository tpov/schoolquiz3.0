package com.tpov.common.domain.model

import android.util.Log
import com.tpov.common.Core.tpovId
import com.tpov.common.data.manager.Converters
import com.tpov.common.data.model.local.StructureDataEntity
import com.tpov.common.data.model.remote.StructureDataRemote
import com.tpov.common.domain.usecase.SettingConfigObject
import com.tpov.common.presentation.utils.DateUtil

data class StructureDataLocal(
    val id: Int? = null,
    var childes: MutableList<StructureDataLocal?>? = null,
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

    fun create(
        id: Int?,
        nameCategory: String,
        numQuestion: Int,
        numHardQuestion: Int,
        lang: String,
        picture: String
    ) =
        StructureDataLocal(
            id,
            null,
            nameCategory,
            DateUtil().getDateQuiz(),
            DateUtil().getDateQuiz(),
            0,0,0,0,0,0,0,
            numHardQuestion,
            numQuestion,
            tpovId,
            SettingConfigObject.settingsConfig.name,
            0,
            lang, picture, true, false
        )
    fun printFullStructure(s: String): StructureDataLocal {

        val result = buildString {
            appendLine("")
            fun printNode(node: StructureDataLocal?, depth: Int) {
                if (node == null) return

                val indent = "    ".repeat(depth)
                val prefix = if (depth > 0) "└── " else ""
                appendLine("$indent$prefix${node.nameItem} (id=${node.id})")

                node.childes?.filterNotNull()?.forEach { child ->
                    printNode(child, depth + 1)
                }
            }

            printNode(this@StructureDataLocal, 0)
            appendLine("")
        }

        Log.d(s, "\n$result")
        return this
    }

    fun toStructureDataEntity(): StructureDataEntity? {
        val transformedChildes = childes?.map { child ->
            child?.toStructureDataEntity()
        }

        val childesJson = Converters().fromChildesList(transformedChildes)

        return StructureDataEntity(
            id = id,
            childesJson = childesJson,
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

    fun toStructureDataRemote(): StructureDataRemote {
        return StructureDataRemote(
            id = id,
            childes = childes?.map { toStructureDataRemote() },
            nameItem = nameItem,
            dataUpdate = dataUpdate,
            version = version,
            ratingGlobal = ratingGlobal,
            starsMaxGlobal = starsMaxGlobal,
            starsAverageGlobal = starsAverageGlobal,
            tpovIdCreator = tpovIdCreator,
            nameCreator = nameCreator,
            tpovIdMaxStarsGlobal = tpovIdMaxStarsGlobal,
            picture = picture,
            languages = languages
        )
    }
}
