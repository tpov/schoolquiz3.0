package com.tpov.common.domain.usecase

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

open class StructureUseCase @Inject constructor(
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

    suspend fun fetchStructureData(event: Int) = StructureDataLocal(children = repositoryStructureImpl.fetchStructureDataList(event)?.toMutableList())

    suspend fun getStructureData(event: Int) =
        repositoryStructureImpl.getStructureData(event)

    @SuppressLint("SuspiciousIndentation")
    suspend fun syncStructureDataAndQuestions(eventId: Int): List<ChangeVersionStructure> {
        val changedQuizzes = mutableListOf<ChangeVersionStructure>()

        // Логируем начало синхронизации
        Log.d("SYNC", "Начало syncStructureDataAndQuestions для eventId = $eventId")

        // Получение remote данных
        val dataRemote: MutableList<StructureDataLocal> = try {
            repositoryStructureImpl.fetchStructureDataList(eventId)?.toMutableList()!!
        } catch (e: Exception) {
            Log.d("SYNC", "Ошибка при fetchStructureDataList: ${e.message}")
            throw e
        }
        Log.d("SYNC", "dataRemote: $dataRemote")

        // Получение local данных
        var dataLocal: MutableList<StructureDataLocal?>? = try {
            repositoryStructureImpl.getStructureData(eventId, -1)?.children?.toMutableList()
        } catch (e: Exception) {
            Log.d("SYNC", "Ошибка при getStructureData: ${e.message}")
            throw e
        }
        Log.d("SYNC", "dataLocal: $dataLocal")

        // Проходим по remote данным и сравниваем с local
        dataRemote.forEachIndexed { index, categoryRemote ->
            val categoryLocal = dataLocal?.getOrNull(index)
            val initialPath = PathStructure(
                idEvent = eventId,
                idCategory = -1,
                idSubCategory = -1,
                idSubsubCategory = -1,
                idQuiz = -1
            )
            Log.d("SYNC", "Обработка категории index=$index, remote = $categoryRemote, local = $categoryLocal")
            filledChangedListAndFetchQuestion(categoryRemote, categoryLocal, changedQuizzes, initialPath)
        }

        if (eventId == 1) {
            dataLocal?.forEach { structureDataLocal ->
                val initialPath = PathStructure(
                    idEvent = eventId,
                    idCategory = structureDataLocal?.id ?: -1,
                    idSubCategory = -1,
                    idSubsubCategory = -1,
                    idQuiz = -1
                )
                Log.d("SYNC", "Обработка structureDataLocal = $structureDataLocal с initialPath = $initialPath")
                val hasMissingBranches = updateRemoteDataAndPushQuestions(structureDataLocal, dataRemote, initialPath)
                Log.d("SYNC", "hasMissingBranches для ${structureDataLocal?.nameItem}: $hasMissingBranches")
                if (hasMissingBranches) {
                    dataRemote.forEach { remoteNode ->
                        Log.d("SYNC", "Вызов pushStructureData для ${structureDataLocal?.nameItem} с remoteNode.id = ${remoteNode.id}")
                        repositoryStructureImpl.pushStructureData(structureDataLocal!!, remoteNode.id!!)
                    }
                }
            }
        }
        dataLocal = dataRemote.toMutableList()

        withContext(Dispatchers.IO) {
            Log.d("SYNC", "Начало fetchQuizInfo в корутине")
            dataLocal = fetchQuizInfo(dataLocal!!, eventId)
            Log.d("SYNC", "После fetchQuizInfo, dataLocal = $dataLocal")
            Log.d("SYNC", "Сохранение структуры данных для eventId = $eventId")
            repositoryStructureImpl.saveStructureData(StructureDataLocal().copy(children = dataLocal), eventId)
            Log.d("SYNC", "Сохранение структуры данных завершено")
        }

        Log.d("SYNC", "Завершение syncStructureDataAndQuestions, changedQuizzes = $changedQuizzes")
        return changedQuizzes
    }

    object Log {
        fun d(tag: String, msg: String): Int {
            println("$tag: $msg")
            return 0
        }
    }
    suspend fun fetchQuizInfo(
        dataLocal: MutableList<StructureDataLocal?>?,
        eventId: Int
    ): MutableList<StructureDataLocal?>? {

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

            if (updatedNode.children.isNullOrEmpty()) {
                return updatedNode
            }

            val updatedChildren = updatedNode.children!!.map { child ->
                val newPath = when {
                    path.idCategory == -1 -> path.copy(idCategory = child?.id!!)
                    path.idSubCategory == -1 -> path.copy(idSubCategory = child?.id!!)
                    path.idSubsubCategory == -1 -> path.copy(idSubsubCategory = child?.id!!)
                    else -> path.copy(idQuiz = child?.id!!)
                }
                updateQuizInfoRecursively(child, newPath)
            }

            return updatedNode.copy(children = updatedChildren.toMutableList())
        }

        return dataLocal?.mapIndexed { index, node ->
            val initialPath = PathStructure(
                idEvent = eventId,
                idCategory = index,
                idSubCategory = -1,
                idSubsubCategory = -1,
                idQuiz = -1
            )
            updateQuizInfoRecursively(node!!, initialPath)
        }?.toMutableList()
    }

    fun updateRemoteDataAndPushQuestions(
        localNode: StructureDataLocal?,
        remoteNode: MutableList<StructureDataLocal>,
        currentPath: PathStructure
    ): Boolean {
        var hasZeroDate = false

        if (localNode == null) return false

        if (localNode.children.isNullOrEmpty()) {
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
                    children = mutableListOf()
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
            localNode.children!!.forEachIndexed { index, child ->
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
            localNode.children?.forEach { parent ->
                ensureAncestorsExistAndUpdate(parent!!, remoteNode)
            }
            remoteNode.add(
                StructureDataLocal(
                    id = getNewIdCategory(remoteNode),
                    nameItem = localNode.nameItem,
                    dataUpdate = localNode.dataUpdate,
                    children = mutableListOf()
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
                        children = existingNode.children
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

        // Логируем вход в функцию с текущими значениями
        Log.d("FILLED_CHANGED", "Вход в filledChangedListAndFetchQuestion: remoteNode = $remoteNode, localNode = $localNode, currentPath = $currentPath")

        if (remoteNode.children.isNullOrEmpty()) {
            if (localNode?.isShowDownload == true && localNode.dataUpdate.toLong() < remoteNode.dataUpdate.toLong()) {
                Log.d("FILLED_CHANGED", "Условие для обновления вопросов выполнено для currentPath = $currentPath. Запускаем асинхронную операцию.")
                GlobalScope.launch {
                    Log.d("FILLED_CHANGED", "Начало корутины для currentPath = $currentPath")
                    repositoryQuestionImpl.deleteQuestionByIdQuiz(currentPath.idQuiz)
                    val questions = repositoryQuestionImpl.fetchQuestion(currentPath, "en")
                    Log.d("FILLED_CHANGED", "Получены вопросы: $questions для currentPath = $currentPath")
                    questions.forEach {
                        repositoryQuestionImpl.saveQuestion(it)
                        Log.d("FILLED_CHANGED", "Вызван saveQuestion для вопроса: $it, currentPath = $currentPath")
                    }
                    Log.d("FILLED_CHANGED", "Завершение корутины для currentPath = $currentPath")
                }
                changedQuizzes.add(
                    ChangeVersionStructure(
                        name = remoteNode.nameItem,
                        pathStructure = currentPath
                    )
                )
                Log.d("FILLED_CHANGED", "Добавлен ChangeVersionStructure для currentPath = $currentPath")
                return true
            }
            Log.d("FILLED_CHANGED", "Условие для обновления вопросов НЕ выполнено для currentPath = $currentPath")
            return false
        }

        remoteNode.children!!.forEach { childRemote ->
            val childLocal = localNode?.children?.getOrNull(childRemote?.id!!)
            val newPath = currentPath.copy(
                idCategory = currentPath.idCategory,
                idSubCategory = if (currentPath.idSubCategory == -1) childRemote?.id!! else currentPath.idSubCategory,
                idSubsubCategory = if (currentPath.idSubCategory != -1 && currentPath.idSubsubCategory == -1) childRemote?.id!! else currentPath.idSubsubCategory,
                idQuiz = if (currentPath.idSubsubCategory != -1) childRemote?.id!! else -1
            )
            Log.d("FILLED_CHANGED", "Рекурсивный вызов filledChangedListAndFetchQuestion для newPath = $newPath, childRemote = $childRemote, childLocal = $childLocal")
            val childChanged = filledChangedListAndFetchQuestion(
                childRemote!!,
                childLocal,
                changedQuizzes,
                newPath
            )
            allChildrenChanged = allChildrenChanged && childChanged
        }

        Log.d("FILLED_CHANGED", "Выход из filledChangedListAndFetchQuestion для currentPath = $currentPath, allChildrenChanged = $allChildrenChanged")
        return allChildrenChanged
    }

    suspend fun updateStructureData(structureDataLocal: StructureDataLocal, eventId: Int) {
        repositoryStructureImpl.updateStructureData(structureDataLocal.toStructureDataEntity()!!, eventId)
    }

}