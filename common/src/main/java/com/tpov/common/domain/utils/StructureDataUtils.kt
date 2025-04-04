package com.tpov.common.domain.utils

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.usecase.StructureUseCase
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

    fun isUpdateStructureLocal(
        structureDataOld: StructureDataLocal?,
        structureDataNew: StructureDataLocal
    ) = false

    fun isUpdateStructureRemote(
        structureDataLocal: StructureDataLocal,
        structureDataRemote: StructureDataLocal
    ) = false

    fun MutableList<StructureDataLocal>.updateLocalInfoData(
        structureDataNew: List<StructureDataLocal>,
        path: PathStructure
    ): MutableList<StructureDataLocal>? {
        val structureCategoryNew = structureDataNew.find { it.id == path.idCategory }
        var nodeOld = findStructureByName(this, structureCategoryNew)?.children

        if (path.idSubCategory != -1) {
            val structureSubCategoryNew =
                structureCategoryNew?.children?.find { it.id == path.idSubCategory }
            nodeOld = findStructureByName(nodeOld?.toList(), structureSubCategoryNew)?.children

            if (path.idSubsubCategory != -1) {
                val structureSubsubCategoryNew =
                    structureSubCategoryNew?.children?.find { it.id == path.idSubsubCategory }
                nodeOld =
                    findStructureByName(nodeOld?.toList(), structureSubsubCategoryNew)?.children

                if (path.idQuiz != -1) {
                    val structureQuizNew =
                        structureSubsubCategoryNew?.children?.find { it.id == path.idQuiz }
                    nodeOld = findStructureByName(nodeOld?.toList(), structureQuizNew)?.children
                }
            }
        }

        return nodeOld
    }

    fun processStructureDataDifferences(
        structureNodeListNew: MutableList<StructureDataLocal>,
        structureNodeListOld: MutableList<StructureDataLocal>?,
        eventId: Int,
        callback: CallbackDifferences,
        currentPath: PathStructure = PathStructure(
            idEvent = eventId,
            idCategory = -1,
            idSubCategory = -1,
            idSubsubCategory = -1,
            idQuiz = -1
        )
    ) {
        StructureUseCase.Log.d("currentPath", "$currentPath")

        structureNodeListNew.forEach { structureNodeNew ->
            structureNodeNew.printFullStructure("structureNodeNew")
            val nodeId = structureNodeNew.id!!
            val structureNodeOld = findStructureByName(
                structureNodeListOld ?: mutableListOf(),
                structureNodeNew
            )
            PathStructureUtils.updatePath(currentPath, nodeId)
            StructureUseCase.Log.d("updatePath", "$currentPath")
            when {
                structureNodeOld == null -> {
                    callback.onMissingOldStructure(
                        structureNodeListOld,
                        structureNodeNew,
                        currentPath
                    )
                }

                structureNodeNew.children?.isNotEmpty() == true -> {
                    callback.onHasChildren(
                        mutableListOf(structureNodeOld),
                        structureNodeNew,
                        currentPath
                    )
                    processStructureDataDifferences(
                        structureNodeNew.children!!,
                        structureNodeOld.children,
                        eventId,
                        callback,
                        currentPath
                    )
                }

                else -> {
                    callback.onNoChildren(
                        mutableListOf(structureNodeOld),
                        structureNodeNew,
                        currentPath
                    )
                }
            }
            PathStructureUtils.resetPath(currentPath)
        }
    }

    data class OldStructureResult(
        val pathOld: PathStructure,
        val structureData: StructureDataLocal?
    )

    fun MutableList<StructureEditData>.add(fromPath: PathStructure, toPath: PathStructure) {
        this.add(
            StructureEditData(
                1,
                fromPath.idEvent,
                fromPath.idCategory,
                fromPath.idSubCategory,
                fromPath.idSubsubCategory,
                fromPath.idQuiz,
                toPath.idEvent,
                toPath.idCategory,
                toPath.idSubCategory,
                toPath.idSubsubCategory,
                toPath.idQuiz,
                "", "", "", "", "",
                true,
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

        val categoryNew = structureCategoryDataListNew.find { it.id == currentPath.idCategory }
        val categoryOld = findStructureByName(structureCategoryDataListOld, categoryNew)

        val subCategoryNew = categoryNew?.findChildren(currentPath.idSubCategory)
        val subCategoryOld = findStructureByName(categoryOld?.children, subCategoryNew)

        val subsubCategoryNew = subCategoryNew?.findChildren(currentPath.idSubsubCategory)
        val subsubCategoryOld = findStructureByName(subCategoryOld?.children, subsubCategoryNew)

        val quizNew = subsubCategoryNew?.findChildren(currentPath.idQuiz)
        val quizOld = findStructureByName(subsubCategoryOld?.children, quizNew)

        val oldPath = PathStructure(
            idEvent = currentPath.idEvent,
            idCategory = categoryOld?.id ?: -1,
            idSubCategory = subCategoryOld?.id ?: -1,
            idSubsubCategory = subsubCategoryOld?.id ?: -1,
            idQuiz = quizOld?.id ?: -1
        )

        if (currentPath.idCategory == 3 && currentPath.idSubCategory == 3 && currentPath.idSubsubCategory == 1) {
            StructureUseCase.Log.d(
                "findStructureDataOld",
                "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
            )

            StructureUseCase.Log.d("oldPath", "$oldPath")
            StructureUseCase.Log.d("currentPath", "$currentPath")

            categoryNew?.printFullStructure("categoryNew")
            categoryOld?.printFullStructure("categoryOld")

            subCategoryNew?.printFullStructure("subCategoryNew")
            subCategoryOld?.printFullStructure("subCategoryOld")

            subsubCategoryNew?.printFullStructure("subsubCategoryNew")
            subsubCategoryOld?.printFullStructure("subsubCategoryOld")

            quizNew?.printFullStructure("quizNew")
            quizOld?.printFullStructure("quizOld $")

            StructureUseCase.Log.d(
                "findStructureDataOld",
                "-------------------------------------------------------------"
            )

            StructureUseCase.Log.d("oldPath", "$oldPath")
            StructureUseCase.Log.d("currentPath", "$currentPath")
        }
        return OldStructureResult(oldPath, quizOld)
    }

    //Если -1 то возвращаем этот же обьект, так как -1 в пути означакет что не нужно углублятся
    fun StructureDataLocal.findChildren(id: Int): StructureDataLocal? {
        if (id == -1) return this
        return this.children?.find { it.id == id }
    }

    fun MutableList<StructureDataLocal>.addNode(
        node: StructureDataLocal,
        path: PathStructure,
    ): MutableList<StructureDataLocal> {
        this.find { it.id == path.idCategory }
            ?.findChildren(path.idSubCategory)
            ?.findChildren(path.idSubsubCategory)?.let { quiz ->
                if (quiz.children == null) {
                    quiz.children = mutableListOf()
                }
                val newId = quiz.children?.last()?.id?.plus(1) ?: 1
                quiz.children?.add(node.copy(id = newId))
            }

        return this
    }

    fun MutableList<StructureDataLocal>.updateEditIdsRemote(
        path: PathStructure,
        editIdsList: MutableMap<PathStructure, PathStructure>
    ) {
        this.find { it.id == path.idCategory }
            ?.findChildren(path.idSubCategory)
            ?.findChildren(path.idSubsubCategory)?.let { quiz ->
                if (quiz.children == null) {
                    quiz.children = mutableListOf()
                }
                val newId = quiz.children?.last()?.id?.plus(1) ?: 1

                val newPath = path

                if (path.idEvent == -1) {
                    newPath.idEvent = newId
                } else if (path.idCategory == -1) {
                    newPath.idCategory = newId
                } else if (path.idSubCategory == -1) {
                    newPath.idSubCategory = newId
                } else if (path.idSubsubCategory == -1) {
                    newPath.idSubsubCategory = newId
                } else {
                    newPath.idQuiz = newId
                }
                editIdsList[path] = newPath
            }
    }

    fun MutableList<StructureDataLocal>.updateEditIdsLocal(
        path: PathStructure,
        editIdsList: MutableMap<PathStructure, PathStructure>
    ) {
        this.find { it.id == path.idCategory }
            ?.findChildren(path.idSubCategory)
            ?.findChildren(path.idSubsubCategory)?.let { quiz ->
                if (quiz.children == null) {
                    quiz.children = mutableListOf()
                }
                val newId = quiz.children?.last()?.id?.plus(1) ?: 1

                val newPath = path

                if (path.idEvent == -1) {
                    newPath.idEvent = newId
                } else if (path.idCategory == -1) {
                    newPath.idCategory = newId
                } else if (path.idSubCategory == -1) {
                    newPath.idSubCategory = newId
                } else if (path.idSubsubCategory == -1) {
                    newPath.idSubsubCategory = newId
                } else {
                    newPath.idQuiz = newId
                }
                editIdsList[path] = newPath
            }
    }

    fun MutableList<StructureDataLocal>.updateNode(
        node: StructureDataLocal,
        path: PathStructure
    ): MutableList<StructureDataLocal> {
        this.find { it.id == path.idCategory }
            ?.findChildren(path.idSubCategory)
            ?.findChildren(path.idSubsubCategory)
            ?.findChildren(path.idQuiz)?.let { existingNode ->
                val currentId = existingNode.id

                StructureDataLocal::class.memberProperties
                    .filterIsInstance<KMutableProperty1<StructureDataLocal, Any?>>()
                    .filter { it.name != "id" }
                    .forEach { prop ->
                        val value = prop.get(node)
                        if (value != null) {
                            prop.set(existingNode, value)
                        }
                    }

                existingNode.id = currentId
            }
        return this
    }
}