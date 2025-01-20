package com.tpov.common.domain.usecase

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.domain.model.ChangeVersionStructure
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.Values.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class StructureUseCase @Inject constructor(
    private val repositoryStructureImpl: RepositoryStuctureImpl,
    private val repositoryQuestionImpl: RepositoryQuestionImpl
) {

    fun savePicture(fileName: String, bitmap: Bitmap) {
        val file = File(application.filesDir, fileName)
        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
            Toast.makeText(application, "Image saved to $fileName in Pictures", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fileOutputStream?.close()
        }
    }

    fun logger(i: Int) {
        Log.d("logger", i.toString())
    }

    suspend fun fetchStructureData(event: Int) = StructureDataLocal(childes = repositoryStructureImpl.fetchStructureDataList(event)?.toMutableList())

    suspend fun getStructureData(event: Int) =
        repositoryStructureImpl.getStructureData(event)

    @SuppressLint("SuspiciousIndentation")
    suspend fun syncStructureDataAndQuestions(eventId : Int): List<ChangeVersionStructure> {
        val changedQuizzes = mutableListOf<ChangeVersionStructure>()

            val dataRemote: MutableList<StructureDataLocal> = try {
                repositoryStructureImpl.fetchStructureDataList(eventId)?.toMutableList()!!
            } catch (e: Exception) {
                throw e
            }

            var dataLocal: MutableList<StructureDataLocal> = try {
                repositoryStructureImpl.getStructureData(eventId, -1)?.childes!!.toMutableList()
            } catch (e: Exception) {
                throw e
            }

            dataRemote?.forEachIndexed { index, categoryRemote ->
                val categoryLocal = dataLocal.getOrNull(index)
                val initialPath = PathStructure(
                    idEvent = eventId,
                    idCategory = -1,
                    idSubCategory = -1,
                    idSubsubCategory = -1,
                    idQuiz = -1
                )
                filledChangedListAndFetchQuestion(
                    categoryRemote,
                    categoryLocal,
                    changedQuizzes,
                    initialPath
                )
            }
            if (eventId == 1) {
                dataLocal.forEach { structureDataLocal ->
                    val initialPath = PathStructure(
                        idEvent = eventId,
                        idCategory = structureDataLocal.id!!,
                        idSubCategory = -1,
                        idSubsubCategory = -1,
                        idQuiz = -1
                    )

                    val hasMissingBranches = updateRemoteDataAndPushQuestions(
                        structureDataLocal,
                        dataRemote,
                        initialPath
                    )

                    if (hasMissingBranches) {
                        dataRemote.forEach { remoteNode ->
                            repositoryStructureImpl.pushStructureData(
                                structureDataLocal,
                                remoteNode.id!!
                            )
                        }
                    }
                }
            }
            dataLocal = dataRemote.toMutableList()
            CoroutineScope(Dispatchers.IO).launch {
                dataLocal = fetchQuizInfo(dataLocal, eventId)
                repositoryStructureImpl.saveStructureData(
                    StructureDataLocal().copy(childes = dataLocal),
                    eventId
                )
            }

        return changedQuizzes
    }

    suspend fun fetchQuizInfo(
        dataLocal: MutableList<StructureDataLocal>,
        eventId: Int
    ): MutableList<StructureDataLocal> {

        suspend fun updateQuizInfoRecursively(
            node: StructureDataLocal,
            path: PathStructure
        ): StructureDataLocal {
            val currentNodeInfo = try {
                repositoryStructureImpl.fetchStructureInfo(path)
            } catch (e: Exception) {
                null
            }

            val updatedNode = currentNodeInfo?.let { info ->
                node.copy(
                    ratingLocal = info.rating,
                    starsMaxLocal = info.starsMax,
                    starsAverageLocal = info.starsAverage,
                )
            } ?: node

            if (updatedNode.childes.isNullOrEmpty()) {
                return updatedNode
            }

            val updatedChildren = updatedNode.childes!!.map { child ->
                val newPath = when {
                    path.idCategory == -1 -> path.copy(idCategory = child.id!!)
                    path.idSubCategory == -1 -> path.copy(idSubCategory = child.id!!)
                    path.idSubsubCategory == -1 -> path.copy(idSubsubCategory = child.id!!)
                    else -> path.copy(idQuiz = child.id!!)
                }
                updateQuizInfoRecursively(child, newPath)
            }

            return updatedNode.copy(childes = updatedChildren.toMutableList())
        }

        return dataLocal.mapIndexed { index, node ->
            val initialPath = PathStructure(
                idEvent = eventId,
                idCategory = index,
                idSubCategory = -1,
                idSubsubCategory = -1,
                idQuiz = -1
            )
            updateQuizInfoRecursively(node, initialPath)
        }.toMutableList()
    }

    fun updateRemoteDataAndPushQuestions(
        localNode: StructureDataLocal?,
        remoteNode: MutableList<StructureDataLocal>,
        currentPath: PathStructure
    ): Boolean {
        var hasZeroDate = false

        if (localNode == null) return false

        if (localNode.childes.isNullOrEmpty()) {
            ensureAncestorsExistAndUpdate(localNode, remoteNode)

            val existingNode = remoteNode.find { it.nameItem == localNode.nameItem }
            if (existingNode == null || localNode.dataUpdate.toLong() == 0L ||
                localNode.dataUpdate.toLong() > existingNode.dataUpdate.toLong()
            ) {
                hasZeroDate = true
                val updatedNode = StructureDataLocal(
                    getNewIdCategory(remoteNode),
                    nameItem = localNode.nameItem,
                    dataUpdate = localNode.dataUpdate,
                    childes = mutableListOf()
                )
                if (existingNode == null) {
                    remoteNode.add(updatedNode)

                    pushQuestion(currentPath)
                } else {
                    remoteNode.remove(existingNode)
                    remoteNode.add(updatedNode)

                    pushQuestion(currentPath, true)
                }

            }
        } else {
            localNode.childes!!.forEachIndexed { index, child ->
                val childPath = currentPath.copy(
                    idSubCategory = if (currentPath.idSubCategory == -1) index else currentPath.idSubCategory,
                    idSubsubCategory = if (currentPath.idSubCategory != -1 && currentPath.idSubsubCategory == -1) index else currentPath.idSubsubCategory,
                    idQuiz = if (currentPath.idSubsubCategory != -1) index else -1
                )
                val childHasZeroDate = updateRemoteDataAndPushQuestions(
                    localNode = child,
                    remoteNode = remoteNode,
                    currentPath = childPath
                )
                hasZeroDate = hasZeroDate || childHasZeroDate
            }
        }

        return hasZeroDate
    }

    private fun pushQuestion(path: PathStructure, isUpdate: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            val questions = repositoryQuestionImpl.getQuestionsByPath(path)
            questions.forEach { question ->
                repositoryQuestionImpl.pushQuestion(question, isUpdate)
            }
        }
    }

    fun ensureAncestorsExistAndUpdate(
        localNode: StructureDataLocal,
        remoteNode: MutableList<StructureDataLocal>
    ) {
        val existingNode = remoteNode.find { it.nameItem == localNode.nameItem }

        if (existingNode == null) {
            localNode.childes?.forEach { parent ->
                ensureAncestorsExistAndUpdate(parent, remoteNode)
            }
            remoteNode.add(
                StructureDataLocal(
                    id = getNewIdCategory(remoteNode),
                    nameItem = localNode.nameItem,
                    dataUpdate = localNode.dataUpdate,
                    childes = mutableListOf()
                )
            )
        } else {
            if (localNode.dataUpdate.toLong() > existingNode.dataUpdate.toLong()) {
                remoteNode.remove(existingNode)
                remoteNode.add(
                    StructureDataLocal(
                        id = getNewIdCategory(remoteNode),
                        nameItem = localNode.nameItem,
                        dataUpdate = localNode.dataUpdate,
                        childes = existingNode.childes
                    )
                )
            }
        }
    }

    private fun getNewIdCategory(remoteNode: MutableList<StructureDataLocal>): Int? {
        return 2
    }


    private fun isShowDownload(newEventId: Int): Boolean {
        return newEventId == 1 || newEventId == 8
    }

    private fun isShowArhive(): Boolean {
        return true
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun filledChangedListAndFetchQuestion(
        remoteNode: StructureDataLocal,
        localNode: StructureDataLocal?,
        changedQuizzes: MutableList<ChangeVersionStructure>,
        currentPath: PathStructure
    ): Boolean {
        var allChildrenChanged = true

        if (remoteNode.childes.isNullOrEmpty()) {
            if (localNode?.isShowDownload == true && localNode.dataUpdate.toLong() < remoteNode.dataUpdate.toLong()) {
                GlobalScope.launch {
                    repositoryQuestionImpl.deleteQuestionByIdQuiz(currentPath.idQuiz)
                    repositoryQuestionImpl.fetchQuestion(currentPath, "en").forEach {
                        repositoryQuestionImpl.saveQuestion(it)
                    }
                }
                changedQuizzes.add(
                    ChangeVersionStructure(
                        name = remoteNode.nameItem,
                        pathStructure = currentPath
                    )
                )
                return true
            }
            return false
        }

        remoteNode.childes!!.forEach { childRemote ->
            val childLocal = localNode?.childes?.getOrNull(childRemote.id!!)
            val newPath = currentPath.copy(
                idCategory = currentPath.idCategory,
                idSubCategory = if (currentPath.idSubCategory == -1) childRemote.id!! else currentPath.idSubCategory,
                idSubsubCategory = if (currentPath.idSubCategory != -1 && currentPath.idSubsubCategory == -1) childRemote.id!! else currentPath.idSubsubCategory,
                idQuiz = if (currentPath.idSubsubCategory != -1) childRemote.id!! else -1
            )
            val childChanged = filledChangedListAndFetchQuestion(
                childRemote,
                childLocal,
                changedQuizzes,
                newPath
            )
            allChildrenChanged = allChildrenChanged && childChanged
        }

        return allChildrenChanged
    }

    suspend fun updateStructureData(structureDataLocal: StructureDataLocal) {
        repositoryStructureImpl.updateStructureData(structureDataLocal.toStructureDataEntity())
    }

}