package com.tpov.common.domain.model

import com.tpov.common.Core.tpovId
import com.tpov.common.data.manager.Converters
import com.tpov.common.data.model.local.StructureDataEntity
import com.tpov.common.data.model.remote.StructureDataRemote
import com.tpov.common.domain.usecase.SettingConfigObject
import com.tpov.common.presentation.utils.DateUtil

data class StructureDataLocal(
    var children: MutableList<StructureDataLocal>? = null,
    var nameItem: String = "",
    var dataUpdateGlobal: String = "", // for syncs
    var dataUpdateLocal: String = "", // for syncs -1 - delete
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
    var isShowArchive: Boolean = true,
    var hasGeneratedQuiz: Boolean = false,
    var quizId: Int = 0,
    var isPurchased: Boolean = false,
    var isBought: Boolean = false,
    var isBoughtTime: String = "",
    var isDownload: Boolean = false,
    var show: Boolean = true
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
            children = null,
            nameItem = nameCategory,
            dataUpdateGlobal = DateUtil().getDateQuiz(),
            dataUpdateLocal = DateUtil().getDateQuiz(),
            dataCreate = DateUtil().getDateQuiz(),
            version = 0,
            ratingGlobal = 0,
            ratingLocal = 0,
            starsMaxLocal = 0,
            starsMaxGlobal = 0,
            starsAverageLocal = 0,
            starsAverageGlobal = 0,
            numHQ = numHardQuestion,
            numQ = numQuestion,
            tpovIdCreator = tpovId,
            nameCreator = SettingConfigObject.settingsConfig.name,
            tpovIdMaxStarsGlobal = 0,
            languages = lang,
            picture = picture,
            isShowDownload = true,
            isShowArchive = false,
            hasGeneratedQuiz = false,
            quizId = 0,
            isPurchased = false,
            isBought = false,
            isBoughtTime = "",
            isDownload = false,
            show = true
        )
    fun printFullStructure(s: String): StructureDataLocal {

        val result = buildString {
            appendLine("")
            fun printNode(node: StructureDataLocal?, depth: Int) {
                if (node == null) return

                val indent = "    ".repeat(depth)
                val prefix = if (depth > 0) "└── " else ""
                appendLine("$indent$prefix${node.nameItem} ")

                node.children?.filterNotNull()?.forEach { child ->
                    printNode(child, depth + 1)
                }
            }

            printNode(this@StructureDataLocal, 0)
            appendLine("")
        }
        println(result)
        return this
    }

    fun toStructureDataEntity(): StructureDataEntity? {
        val transformedChildes = children?.map { child ->
            child?.toStructureDataEntity()
        }

        val childesJson = Converters().fromChildesList(transformedChildes)

        return StructureDataEntity(
            childesJson = childesJson,
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
            isShowArchive = isShowArchive
        )
    }

    fun toStructureDataRemote(): StructureDataRemote {
        return StructureDataRemote(
            children = children?.map { toStructureDataRemote() },
            nameItem = nameItem,
            dataUpdate = dataUpdateGlobal,
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
