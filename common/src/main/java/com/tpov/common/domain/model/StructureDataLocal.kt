package com.tpov.common.domain.model

import com.tpov.common.Core.tpovId
import com.tpov.common.data.manager.Converters
import com.tpov.common.data.model.local.StructureDataEntity
import com.tpov.common.data.model.remote.StructureDataRemote
import com.tpov.common.domain.usecase.SettingConfigObject
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.utils.DateUtil

data class StructureDataLocal(
    var id: Int? = null,
    var children: MutableList<StructureDataLocal>? = null,
    var nameItem: String = "",
    var dataUpdate: String = "", // for syncs
    var dataCreate: String = "",
    var version: Int = 0,       // for show update user
    var ratingGlobal: Int = 0,
    var ratingLocal: Int = 0,
    var starsMaxLocal: Int = 0,
    var starsMaxGlobal: Int = 0,
    var starsAverageLocal: Int = 0,
    var starsAverageGlobal: Int = 0,
    var numHQ: Int = 0,
    var numQ: Int = 0,
    var tpovIdCreator: Int = 0,
    var nameCreator: String = "",
    var tpovIdMaxStarsGlobal: Int = 0,
    var languages: String = "",
    var picture: String = "",
    var isShowDownload: Boolean = true,
    var isShowArchive: Boolean = false
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

                node.children?.filterNotNull()?.forEach { child ->
                    printNode(child, depth + 1)
                }
            }

            printNode(this@StructureDataLocal, 0)
            appendLine("")
        }

        StructureUseCase.Log.d(s, "\n$result")
        return this
    }

    fun toStructureDataEntity(): StructureDataEntity? {
        val transformedChildes = children?.map { child ->
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
            children = children?.map { toStructureDataRemote() },
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
