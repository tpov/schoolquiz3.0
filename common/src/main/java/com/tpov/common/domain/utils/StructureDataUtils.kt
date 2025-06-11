package com.tpov.common.domain.utils

import com.tpov.common.data.model.entity.QuestionEntity
import com.tpov.common.data.model.entity.StructureInfoEntity
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.OldStructureResult
import com.tpov.common.presentation.model.PathStructure
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

typealias StructureDataHandler = (MutableList<StructureDataLocal>?, StructureDataLocal, PathStructure) -> Unit

data class CallbackDifferences(
    val onMissingOldStructure: StructureDataHandler,
    val onHasChildren: StructureDataHandler,
    val onNoChildren: StructureDataHandler
)

object StructureDataUtils {

    fun findStructureByName(
        structureDataOld: List<StructureDataLocal>?,
        structureDataNew: StructureDataLocal?
    ): StructureDataLocal? {
        return structureDataOld?.find {
            it.nameItem == structureDataNew?.nameItem
        }
    }

    fun isUpdateStructure(
        structureDataOld: StructureDataLocal?,
        structureDataNew: StructureDataLocal
    ) = structureDataOld?.version!! > structureDataNew.version


    fun MutableList<StructureDataLocal>.updateLocalInfoData(
        structureDataNew: List<StructureDataLocal>,
        path: PathStructure
    ): MutableList<StructureDataLocal>? {
        val structureCategoryNew = structureDataNew.find { it.nameItem == path.nameCategory }
        var nodeOld = findStructureByName(this, structureCategoryNew)?.children

        if (path.nameSubCategory != "") {
            val structureSubCategoryNew =
                structureCategoryNew?.children?.find { it.nameItem == path.nameSubCategory }
            nodeOld = findStructureByName(nodeOld?.toList(), structureSubCategoryNew)?.children

            if (path.nameSubsubCategory != "") {
                val structureSubsubCategoryNew =
                    structureSubCategoryNew?.children?.find { it.nameItem == path.nameSubsubCategory }
                nodeOld = findStructureByName(nodeOld?.toList(), structureSubsubCategoryNew)?.children

                if (path.nameQuiz != "") {
                    val structureQuizNew =
                        structureSubsubCategoryNew?.children?.find { it.nameItem == path.nameQuiz }
                    nodeOld = findStructureByName(nodeOld?.toList(), structureQuizNew)?.children
                }
            }
        }

        return nodeOld
    }

    fun List<StructureInfoEntity>.findInfoByPath(path: PathStructure) = this.find {
        it.pathStructure == path
    }

    fun List<StructureInfoEntity>.findChangeByPath(path: PathStructure) = this.find {
        it.pathStructure == path
    }

    fun processStructureDataDifferences(
        structureNodeListNew: MutableList<StructureDataLocal>,
        structureNodeListOld: MutableList<StructureDataLocal>?,
        event: EventQuiz,
        callback: CallbackDifferences,
        currentPathNew: PathStructure = PathStructure(
            nameEvent = event.name,
            nameCategory = "",
            nameSubCategory = "",
            nameSubsubCategory = "",
            nameQuiz = ""
        )
    ) {

        structureNodeListNew.forEach { structureNodeNew ->
            val nodeId = structureNodeNew.nameItem
            val structureNodeOld = findStructureByName(
                structureNodeListOld ?: mutableListOf(),
                structureNodeNew
            )

            PathStructureUtils.updatePath(currentPathNew, nodeId)
            when {
                structureNodeOld == null -> {
                    callback.onMissingOldStructure(
                        structureNodeListOld,
                        structureNodeNew,
                        currentPathNew
                    )
                }

                structureNodeNew.children?.isNotEmpty() == true -> {

                    callback.onHasChildren(
                        mutableListOf(structureNodeOld),
                        structureNodeNew,
                        currentPathNew
                    )
                    processStructureDataDifferences(
                        structureNodeNew.children!!,
                        structureNodeOld.children,
                        event,
                        callback,
                        currentPathNew
                    )
                }

                else -> {

                    callback.onNoChildren(
                        mutableListOf(structureNodeOld),
                        structureNodeNew,
                        currentPathNew
                    )
                }
            }
            PathStructureUtils.resetPath(currentPathNew)
        }
    }

    fun MutableList<StructureEditData>.add(fromPath: PathStructure, toPath: PathStructure) {
        this.add(
            StructureEditData(
                1,
                fromPath.nameEvent,
                fromPath.nameCategory,
                fromPath.nameSubCategory,
                fromPath.nameSubsubCategory,
                fromPath.nameQuiz,
                toPath.nameEvent,
                toPath.nameCategory,
                toPath.nameSubCategory,
                toPath.nameSubsubCategory,
                toPath.nameQuiz,               true,
                false
            )
        )
    }

    fun MutableList<QuestionEntity>.addList(questionList: List<QuestionEntity>): List<QuestionEntity> {
        questionList.forEach {
            this.add(it)
        }
        this.sortBy { it.id }
        return this
    }

    fun findStructureDataOld(
        structureCategoryDataListOld: MutableList<StructureDataLocal>,
        structureCategoryDataListNew: MutableList<StructureDataLocal>,
        currentPath: PathStructure
    ): OldStructureResult {

        val categoryNew = structureCategoryDataListNew.find { it.nameItem == currentPath.nameCategory }
        val categoryOld = findStructureByName(structureCategoryDataListOld, categoryNew)

        val subCategoryNew = categoryNew?.findChildren(currentPath.nameSubCategory)
        val subCategoryOld =
            if (currentPath.nameSubCategory == "") categoryOld else findStructureByName(
                categoryOld?.children,
                subCategoryNew
            )

        val subsubCategoryNew = subCategoryNew?.findChildren(currentPath.nameSubsubCategory)
        val subsubCategoryOld =
            if (currentPath.nameSubsubCategory == "") subCategoryOld else findStructureByName(
                subCategoryOld?.children,
                subsubCategoryNew
            )

        val quizNew = subsubCategoryNew?.findChildren(currentPath.nameQuiz)
        val quizOld = if (currentPath.nameQuiz == "") subsubCategoryOld else findStructureByName(
            subsubCategoryOld?.children,
            quizNew
        )

        val oldPath = PathStructure(
            nameEvent = currentPath.nameEvent,
            nameCategory = categoryOld?.nameItem ?: "",
            nameSubCategory = if (currentPath.nameSubCategory == "") "" else subCategoryOld?.nameItem ?: "",
            nameSubsubCategory = if (currentPath.nameSubsubCategory == "") "" else subsubCategoryOld?.nameItem
                ?: "",
            nameQuiz = if (currentPath.nameQuiz =="") "" else quizOld?.nameItem ?: ""
        )

        return OldStructureResult(oldPath, quizOld)
    }



    //Если "" то возвращаем этот же обьект, так как "" в пути означакет что не нужно углублятся
    fun StructureDataLocal.findChildren(nameItem: String): StructureDataLocal? {
        if (nameItem == "") return this
        return this.children?.find {
            it.nameItem == nameItem
        }
    }

    fun MutableList<StructureDataLocal>.addNodeByPath(
        node: StructureDataLocal,
        path: PathStructure,
    ): MutableList<StructureDataLocal> {
        this.find { it.nameItem == path.nameCategory }
            ?.findChildren(path.nameSubCategory)
            ?.findChildren(path.nameSubsubCategory)?.let { quiz ->
                if (quiz.children == null) {
                    quiz.children = mutableListOf()
                }
                quiz.children?.add(node)
            }

        return this
    }

    fun MutableList<StructureDataLocal>.removeNodeByPath(
        path: PathStructure
    ): Boolean {
        val category = this.find { it.nameItem== path.nameCategory }

        when {
            path.nameQuiz != "" -> {
                // Удаляем квиз
                val subCategory = category?.findChildren(path.nameSubCategory)
                val subsubCategory = subCategory?.findChildren(path.nameSubsubCategory)

                subsubCategory?.children?.removeIf { it.nameItem == path.nameQuiz }
                return true
            }

            path.nameSubsubCategory != "" -> {
                // Удаляем подподкатегорию
                val subCategory = category?.findChildren(path.nameSubCategory)

                subCategory?.children?.removeIf { it.nameItem == path.nameSubsubCategory }
                return true
            }

            path.nameSubCategory != "" -> {
                // Удаляем подкатегорию
                category?.children?.removeIf { it.nameItem == path.nameSubCategory }
                return true
            }

            path.nameCategory != "" -> {
                // Удаляем категорию
                this.removeIf { it.nameItem == path.nameCategory }
                return true
            }
        }

        return false
    }

    fun MutableList<StructureDataLocal>.updateNodeByPath(
        node: StructureDataLocal,
        path: PathStructure
    ): MutableList<StructureDataLocal> {
        this.find { it.nameItem == path.nameCategory }
            ?.findChildren(path.nameSubCategory)
            ?.findChildren(path.nameSubsubCategory)
            ?.findChildren(path.nameQuiz)?.let { existingNode ->
                val currentId = existingNode.nameItem

                StructureDataLocal::class.memberProperties
                    .filterIsInstance<KMutableProperty1<StructureDataLocal, Any?>>()
                    .filter { it.name != "nameItem" }
                    .forEach { prop ->
                        val value = prop.get(node)
                        if (value != null) {
                            prop.set(existingNode, value)
                        }
                    }

                existingNode.nameItem = currentId
            }
        return this
    }
}
