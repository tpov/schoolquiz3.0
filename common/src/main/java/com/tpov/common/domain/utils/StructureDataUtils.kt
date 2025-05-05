package com.tpov.common.domain.utils

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.domain.model.OldStructureResult
import com.tpov.common.domain.model.StructureDataLocal
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

    fun List<StructureInfoEntity>.findInfoByPath(path: PathStructure) = this.find {
        it.pathStructure == path
    }

    fun List<StructureInfoEntity>.findChangeByPath(path: PathStructure) = this.find {
        it.pathStructure == path
    }

    fun processStructureDataDifferences(
        structureNodeListNew: MutableList<StructureDataLocal>,
        structureNodeListOld: MutableList<StructureDataLocal>?,
        eventId: Int,
        callback: CallbackDifferences,
        currentPathNew: PathStructure = PathStructure(
            idEvent = eventId,
            idCategory = -1,
            idSubCategory = -1,
            idSubsubCategory = -1,
            idQuiz = -1
        )
    ) {

        structureNodeListNew.forEach { structureNodeNew ->
            //StructureUseCase.Log.d("onHasChildren processStructureDataDifferences", "1 structureData.dataUpdateLocal: ${structureNodeNew.dataUpdateLocal}")
            val nodeId = structureNodeNew.id!!
            val structureNodeOld = findStructureByName(
                structureNodeListOld ?: mutableListOf(),
                structureNodeNew
            )

            // StructureUseCase.Log.d("onHasChildren processStructureDataDifferences", "2 structureData.dataUpdateLocal: ${structureNodeNew.dataUpdateLocal}")
            PathStructureUtils.updatePath(currentPathNew, nodeId)
            // StructureUseCase.Log.d("updatePath", "$currentPath")
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
                        eventId,
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
                    //  StructureUseCase.Log.d("onHasChildren processStructureDataDifferences", "4 structureData.dataUpdateLocal: ${structureNodeNew.dataUpdateLocal}")
                }
            }
            PathStructureUtils.resetPath(currentPathNew)
        }
    }

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
        val subCategoryOld =
            if (currentPath.idSubCategory == -1) categoryOld else findStructureByName(
                categoryOld?.children,
                subCategoryNew
            )

        val subsubCategoryNew = subCategoryNew?.findChildren(currentPath.idSubsubCategory)
        val subsubCategoryOld =
            if (currentPath.idSubsubCategory == -1) subCategoryOld else findStructureByName(
                subCategoryOld?.children,
                subsubCategoryNew
            )

        val quizNew = subsubCategoryNew?.findChildren(currentPath.idQuiz)
        val quizOld = if (currentPath.idQuiz == -1) subsubCategoryOld else findStructureByName(
            subsubCategoryOld?.children,
            quizNew
        )

        val oldPath = PathStructure(
            idEvent = currentPath.idEvent,
            idCategory = categoryOld?.id ?: -1,
            idSubCategory = if (currentPath.idSubCategory == -1) -1 else subCategoryOld?.id ?: -1,
            idSubsubCategory = if (currentPath.idSubsubCategory == -1) -1 else subsubCategoryOld?.id
                ?: -1,
            idQuiz = if (currentPath.idQuiz == -1) -1 else quizOld?.id ?: -1
        )

        return OldStructureResult(oldPath, quizOld)
    }

    fun getPathPositionByPathStructure(
        structureDataLocal: StructureDataLocal,
        pathStructure: PathStructure
    ): PathStructure {
        return PathStructure(
            idEvent = pathStructure.idEvent,

            idCategory = structureDataLocal.children
                ?.indexOfFirst { it.id == pathStructure.idCategory }
                ?.takeIf { it >= 0 }
                ?.let { it + 1 } ?: -1,

            idSubCategory = structureDataLocal.findChildren(pathStructure.idCategory)?.children
                ?.indexOfFirst { it.id == pathStructure.idSubCategory }
                ?.takeIf { it >= 0 }
                ?.let { it + 1 } ?: -1,

            idSubsubCategory = structureDataLocal.findChildren(pathStructure.idCategory)
                ?.findChildren(pathStructure.idSubCategory)?.children
                ?.indexOfFirst { it.id == pathStructure.idSubsubCategory }
                ?.takeIf { it >= 0 }
                ?.let { it + 1 } ?: -1,


            idQuiz = structureDataLocal.findChildren(pathStructure.idCategory)
                ?.findChildren(pathStructure.idSubCategory)
                ?.findChildren(pathStructure.idSubsubCategory)?.children
                ?.indexOfFirst { it.id == pathStructure.idQuiz }
                ?.takeIf { it >= 0 }
                ?.let { it + 1 } ?: -1,
        )
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

    fun MutableList<StructureDataLocal>.removeNode(
        path: PathStructure
    ): Boolean {
        val category = this.find { it.id == path.idCategory }

        when {
            path.idQuiz > 0 -> {
                // Удаляем квиз
                val subCategory = category?.findChildren(path.idSubCategory)
                val subsubCategory = subCategory?.findChildren(path.idSubsubCategory)

                subsubCategory?.children?.removeIf { it.id == path.idQuiz }
                return true
            }

            path.idSubsubCategory > 0 -> {
                // Удаляем подподкатегорию
                val subCategory = category?.findChildren(path.idSubCategory)

                subCategory?.children?.removeIf { it.id == path.idSubsubCategory }
                return true
            }

            path.idSubCategory > 0 -> {
                // Удаляем подкатегорию
                category?.children?.removeIf { it.id == path.idSubCategory }
                return true
            }

            path.idCategory > 0 -> {
                // Удаляем категорию
                this.removeIf { it.id == path.idCategory }
                return true
            }
        }

        return false
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

    fun StructureDataLocal.editThisIds(editIds: StructureEditData) {
        this.id = if (editIds.idSubCategoryTo == -1) editIds.idCategoryTo
        else if (editIds.idSubsubCategoryTo == -1) editIds.idSubCategoryTo
        else if (editIds.idQuizTo == -1) editIds.idSubsubCategoryTo
        else editIds.idQuizTo
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