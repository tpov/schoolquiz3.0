package com.tpov.common.domain.usecase

import com.tpov.common.Core.tpovId
import com.tpov.common.EventQuiz
import com.tpov.common.data.RepositoryQuestionDetailImpl
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStructureImpl
import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.domain.DomainExceptions
import com.tpov.common.domain.model.ChangeVersionStructure
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.model.SyncStage
import com.tpov.common.domain.model.SyncState
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.utils.CallbackDifferences
import com.tpov.common.domain.utils.QuestionUtils.isDownloadQuestionForOptimization
import com.tpov.common.domain.utils.StructureDataUtils.addNode
import com.tpov.common.domain.utils.StructureDataUtils.editThisIds
import com.tpov.common.domain.utils.StructureDataUtils.findInfoByPath
import com.tpov.common.domain.utils.StructureDataUtils.findStructureDataOld
import com.tpov.common.domain.utils.StructureDataUtils.getPathPositionByPathStructure
import com.tpov.common.domain.utils.StructureDataUtils.isUpdateStructure
import com.tpov.common.domain.utils.StructureDataUtils.processStructureDataDifferences
import com.tpov.common.domain.utils.StructureDataUtils.removeNode
import com.tpov.common.domain.utils.StructureDataUtils.updateLocalInfoData
import com.tpov.common.presentation.model.PathStructure
import kotlinx.coroutines.runBlocking

object StructureDataExtention {
    private lateinit var exceptionHandler: DomainExceptions

    fun init(
        handler: DomainExceptions
    ) {
        exceptionHandler = handler
    }

    suspend fun SyncState.initStateStructureData(repositoryStructureImpl: RepositoryStructureImpl): SyncState {
        if (exception != null) return this
        try {
            this.currentStage = SyncStage.STRUCTURE_FETCH
            this.structureCategoryDataListRemote =
                repositoryStructureImpl.fetchStructureCategoryDataList(this.eventId)
                    ?.toMutableList() ?: exceptionHandler.exceptionInitStructureRemoteData()

            this.structureCategoryDataListLocal =
                repositoryStructureImpl.getStructureEventData(this.eventId)?.toMutableList() ?: mutableListOf()

        } catch (e: Exception) {
            exceptionHandler.exceptionInitStructureRemoteData(e.message ?: "")
        }

        return this
    }

    fun SyncState.updateLocalStructureData(): SyncState {
        if (exception != null) return this
        try {
            this.currentStage = SyncStage.STRUCTURE_LOCAL_SYNC

            processStructureDataDifferences(
                structureNodeListNew = this.structureCategoryDataListRemote,
                structureNodeListOld = this.structureCategoryDataListLocal,
                eventId = this.eventId,
                callback = CallbackDifferences(
                    onMissingOldStructure = { _, structureNodeNew, currentPath ->
                        if (currentPath.idSubCategory == -1) currentPath.idCategory = -1
                        else if (currentPath.idSubsubCategory == -1) currentPath.idSubCategory = -1
                        else if (currentPath.idQuiz == -1) currentPath.idSubsubCategory = -1

                        this.structureCategoryDataListLocal.addNode(structureNodeNew, currentPath)

                    },
                    onHasChildren = { structureNodeOld, structureNodeNew, currentPath ->
                        if (isUpdateStructure(structureNodeOld?.first(), structureNodeNew)) {

                            this.structureCategoryDataListLocal
                                .updateLocalInfoData(
                                    this.structureCategoryDataListRemote,
                                    currentPath
                                )
                        }
                    },
                    onNoChildren = { structureNodeOld, structureNodeNew, currentPath ->
                        if (isUpdateStructure(structureNodeOld?.first(), structureNodeNew)) {
                            this.structureCategoryDataListLocal
                                .updateLocalInfoData(
                                    this.structureCategoryDataListRemote,
                                    currentPath
                                )
                        }
                    }
                )
            )

            StructureDataLocal(children = structureCategoryDataListRemote).printFullStructure("syncStateLocalStructureData after")
        } catch (e: Exception) {
            exceptionHandler.exceptionSyncLocalStructureData(e.message ?: "")
        }
        return this
    }

    /**
     * Эта функция генерирует список вопросов который нужно загрузить в локальную БД.
     * Некоторые вопросы нужно обновить, некоторые добавить.
     * Подходит для всех типов квестов
     */
    fun SyncState.syncChangeListQuestionsLocal(): SyncState {
        if (exception != null) return this
        // try {
        this.currentStage = SyncStage.QUESTION_CHANGE_LIST
        processStructureDataDifferences(
            this.structureCategoryDataListRemote,
            this.structureCategoryDataListRemote,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, _, _ -> },
                onNoChildren = { structureNodeListOld, structureNodeNew, currentPath ->
                    val findLocalQuizByName = findStructureDataOld(
                        this.structureCategoryDataListLocal,
                        this.structureCategoryDataListRemote,
                        currentPath
                    )
                    if (structureNodeNew.isShowArchive) {
                        if ((findLocalQuizByName.structureData?.isShowDownload == true)) {
                            if (isUpdateStructure(
                                    findLocalQuizByName.structureData,
                                    structureNodeNew
                                )
                            ) {
                                this.changedListQuestionLocal.add(
                                    ChangeVersionStructure(
                                        structureNodeNew.nameItem, currentPath.copy(), false
                                    )
                                )
                            }
                        } else {
                            if (isDownloadQuestionForOptimization(
                                    currentPath,
                                    structureNodeNew.ratingGlobal,
                                    changedListQuestionLocal.size
                                )
                            ) {
                                this.changedListQuestionLocal.add(
                                    ChangeVersionStructure(
                                        structureNodeNew.nameItem,
                                        currentPath.copy(),
                                        findLocalQuizByName.structureData == null
                                    )
                                )
                            }
                        }
                    }
                }
            ),
        )


        //  } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncQuestionRemote(e.message ?: "")
        //  }
        return this
    }

    /**
     * Эта функция генерирует список вопросов который нужно загрузить в удаленную БД.
     * Некоторые вопросы нужно обновить, некоторые добавить.
     * Подходит для квестов типа - созданным юзером, для остальных типов используем EditStructureData
     *      для перемещения квеста по категориям
     */
    fun SyncState.syncChangeListQuestionsRemote(): SyncState {
        if (exception != null) return this
        // try {
        this.currentStage = SyncStage.QUESTION_CHANGE_LIST
        if (this.eventId == EventQuiz.QUIZ_BY_USER.id) {
            processStructureDataDifferences(
                this.structureCategoryDataListLocal,
                this.structureCategoryDataListLocal,
                this.eventId,
                callback = CallbackDifferences(
                    onMissingOldStructure = { _, _, _ -> },
                    onHasChildren = { _, _, _ -> },
                    onNoChildren = { _, structureNodeNew, currentPath ->

                        val findRemoteQuizByName = findStructureDataOld(
                            this.structureCategoryDataListRemote,
                            this.structureCategoryDataListLocal,
                            currentPath
                        )

                        if (findRemoteQuizByName.structureData != null) {
                            if (isUpdateStructure(
                                    findRemoteQuizByName.structureData,
                                    structureNodeNew
                                )
                            ) {

                                this.changedListQuestionRemote.add(
                                    ChangeVersionStructure(
                                        if (structureNodeNew.dataUpdateLocal == "-1") "-1" //For deleted in local and remote
                                        else structureNodeNew.nameItem,
                                        currentPath.copy(), false
                                    )
                                )
                            }
                        } else {
                            this.changedListQuestionRemote.add(
                                ChangeVersionStructure(
                                    structureNodeNew.nameItem, currentPath.copy(), true
                                )
                            )
                        }
                    }
                ),
            )
        }
        //  } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncQuestionRemote(e.message ?: "")
        //  }
        return this
    }

    /**
     * Заполняем поле с глобальными инфо в структуре
     */
    fun SyncState.syncInfoGlobal(): SyncState {
        if (exception != null) return this
        try {
            this.currentStage = SyncStage.INFO_UPDATE_REMOTE
            processStructureDataDifferences(
                this.structureCategoryDataListRemote,
                this.structureCategoryDataListLocal,
                this.eventId,
                callback = CallbackDifferences(
                    onMissingOldStructure = { _, _, _ -> },
                    onHasChildren = { _, structureDataNew, currentPath ->
                        this.structureInfoGlobal.addInfoGlobal(currentPath, structureDataNew)
                    },
                    onNoChildren = { _, structureDataNew, currentPath ->
                        this.structureInfoGlobal.addInfoGlobal(currentPath, structureDataNew)
                    }
                ),
            )
        } catch (e: Exception) {
            exceptionHandler.exceptionSyncInfo(e.message ?: "")
        }

        return this
    }

    /**
     * Редактируем вопросы согласно списку changedListQuestionLocal
     */
    fun SyncState.updateLocalQuestion(repositoryQuestionImpl: RepositoryQuestionImpl): SyncState {
        if (exception != null) return this
        // try {
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE
        runBlocking {
            this@updateLocalQuestion.changedListQuestionLocal.forEach { change ->
                try {
                    val remoteQuestions =
                        repositoryQuestionImpl.fetchQuestion(change.pathStructure, "ru|ua|en")
                    val pathLocal = findStructureDataOld(
                        this@updateLocalQuestion.structureCategoryDataListLocal,
                        this@updateLocalQuestion.structureCategoryDataListRemote,
                        change.pathStructure
                    ).pathOld

                    if (!change.isCreate) repositoryQuestionImpl.deleteQuestionByPath(pathLocal)

                    remoteQuestions.forEach {
                        repositoryQuestionImpl.saveQuestion(
                            it.copy(
                                idCategory = pathLocal.idCategory,
                                idSubsubCategory = pathLocal.idSubsubCategory,
                                idSubCategory = pathLocal.idSubCategory,
                                idQuiz = pathLocal.idQuiz
                            )
                        )
                    }
                } catch (e: Exception) {

                }

            }
        }

        // } catch (e: Exception) {
        //   exceptionHandler.exceptionSyncInfo(e.message ?: "")
        // }

        return this
    }

    /**
     * Редактируем вопросы согласно списку changedListQuestionRemote
     */
    fun SyncState.updateRemoteQuestion(repositoryQuestionImpl: RepositoryQuestionImpl): SyncState {
        if (exception != null) return this
        // try {
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE
        runBlocking {
            this@updateRemoteQuestion.changedListQuestionRemote.forEach { change ->
                try {
                    if (change.name == "-1") {
                        repositoryQuestionImpl.deleteQuestionByPath(change.pathStructure)
                        if (!change.isCreate) repositoryQuestionImpl.deleteQuestionByPath(change.pathStructure)
                    } else {
                        val localQuestions =
                            repositoryQuestionImpl.getQuestionsByPath(change.pathStructure)

                        if (!change.isCreate) repositoryQuestionImpl.deleteQuestionByPath(change.pathStructure)

                        localQuestions.forEach { repositoryQuestionImpl.pushQuestion(it) }
                    }
                } catch (e: Exception) {

                }

            }
        }

        //  } catch (e: Exception) {
        //      exceptionHandler.exceptionSyncInfo(e.message ?: "")
        // }

        return this
    }


    fun SyncState.updateStructureLocalNumberQuestion(repositoryQuestionImpl: RepositoryQuestionImpl): SyncState {
        if (exception != null) return this
        //  try {
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        processStructureDataDifferences(
            this.structureCategoryDataListLocal,
            this.structureCategoryDataListLocal,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, structureDataNew, currentPath ->
                    structureDataNew.numQ = 0
                    structureDataNew.numHQ = 0
                },
                onNoChildren = { _, structureDataNew, currentPath ->
                    runBlocking {
                        //           getNumsQuestion(repositoryQuestionImpl.getQuestionsByPath(currentPath)) //Todo apply this functions
                        structureDataNew.numQ = 0
                        structureDataNew.numHQ = 0
                    }

                }
            ),
        )
        // } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
        //   }

        return this
    }

    fun SyncState.unlockServer(repositoryStructureImpl: RepositoryStructureImpl): SyncState {
        if (exception != null) return this
        //  try {
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE


        // } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
        //   }

        return this
    }

    fun SyncState.lockServer(repositoryStructureImpl: RepositoryStructureImpl): SyncState {
        if (exception != null) return this
        //  try {
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        // } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
        //   }

        return this
    }


    fun SyncState.clearStructureLocal(
        repositoryQuestionImpl: RepositoryQuestionImpl,
        repositoryQuestionDetailImpl: RepositoryQuestionDetailImpl
    ): SyncState {
        if (exception != null) return this
        //  try {
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        val structureCategoryDataListLocalNew = this.structureCategoryDataListLocal.toMutableList()
        processStructureDataDifferences(
            structureCategoryDataListLocalNew,
            structureCategoryDataListLocalNew,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, structureDataNew, currentPath ->
                    if (structureDataNew.dataUpdateLocal == "-1") {
                        structureCategoryDataListLocal.removeNode(currentPath)
                        runBlocking {
                            repositoryQuestionImpl.deleteQuestionByPath(currentPath)
                            repositoryQuestionDetailImpl.deleteRemoteQuestionDetailByPath(
                                currentPath
                            )
                        }
                    }

                },
                onNoChildren = { _, structureDataNew, currentPath ->
                    if (structureDataNew.dataUpdateLocal == "-1") {
                        structureCategoryDataListLocal.removeNode(currentPath)
                        runBlocking {
                            repositoryQuestionImpl.deleteQuestionByPath(currentPath)
                            repositoryQuestionDetailImpl.deleteRemoteQuestionDetailByPath(
                                currentPath
                            )
                        }
                    }
                }
            ),
        )

        // } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
        //   }
        return this
    }

    /**
     * Помечаем квесты которые нужно удалить на сервере
     */
    fun SyncState.addEditIdsStructureRemote(repositoryStructureImpl: RepositoryStructureImpl): SyncState {
        if (exception != null) return this
        //  try {
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        processStructureDataDifferences(
            structureCategoryDataListLocal,
            structureCategoryDataListLocal,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, structureDataNew, currentPath ->
                    if (structureDataNew.dataUpdateGlobal == "-1") {
                        runBlocking {
                            val structureEditRemote = StructureEditData(
                                null,
                                currentPath.idEvent,
                                currentPath.idCategory,
                                currentPath.idSubCategory,
                                currentPath.idSubsubCategory,
                                currentPath.idQuiz,
                                currentPath.idEvent,
                                currentPath.idCategory,
                                currentPath.idSubCategory,
                                currentPath.idSubsubCategory,
                                currentPath.idQuiz,
                                "",
                                "",
                                "",
                                "",
                                "",
                                true,
                                true
                            )
                            repositoryStructureImpl.insertEditStructure(structureEditRemote)
                        }
                    }
                },

                onNoChildren = { _, structureDataNew, currentPath ->
                    if (structureDataNew.dataUpdateGlobal == "-1") {
                        runBlocking {
                            val structureEditRemote = StructureEditData(
                                null,
                                currentPath.idEvent,
                                currentPath.idCategory,
                                currentPath.idSubCategory,
                                currentPath.idSubsubCategory,
                                currentPath.idQuiz,
                                currentPath.idEvent,
                                currentPath.idCategory,
                                currentPath.idSubCategory,
                                currentPath.idSubsubCategory,
                                currentPath.idQuiz,
                                "",
                                "",
                                "",
                                "",
                                "",
                                true,
                                true
                            )
                            repositoryStructureImpl.insertEditStructure(structureEditRemote)
                        }
                    }
                }
            ),
        )

        // } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
        //   }
        return this
    }

    fun SyncState.syncQuestionDetails(repositoryQuestionDetailImpl: RepositoryQuestionDetailImpl): SyncState {
        if (exception != null) return this
        //  try {

        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        processStructureDataDifferences(
            this.structureCategoryDataListLocal,
            this.structureCategoryDataListLocal,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, _, _ -> },
                onNoChildren = { _, structureDataNew, currentPath ->
                    runBlocking {
                        val questionDetailListRemote =
                            repositoryQuestionDetailImpl.fetchQuestionDetails(currentPath)
                        val questionDetailListLocal =
                            repositoryQuestionDetailImpl.getQuestionDetailByPath(currentPath)

                        questionDetailListRemote.forEach { questionDetailRemote ->
                            if (questionDetailListLocal.find { it.data == questionDetailRemote.data } == null)
                                repositoryQuestionDetailImpl.saveQuestionDetail(questionDetailRemote)
                        }

                        questionDetailListLocal.forEach { questionDetailLocal ->
                            if (!questionDetailLocal.synth) repositoryQuestionDetailImpl.pushQuestionDetails(
                                questionDetailLocal
                            )
                        }
                    }
                }
            ),
        )
        // } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
        //   }

        return this
    }

    fun SyncState.editStructureRemote(repositoryStructureImpl: RepositoryStructureImpl): SyncState {
        if (exception != null) return this
        //  try {

        this.currentStage = SyncStage.INFO_UPDATE_REMOTE
        runBlocking {
            repositoryStructureImpl.getEditStructure().forEach {
                repositoryStructureImpl.pushEditStructure(it)
            }
            repositoryStructureImpl.clearStructureEdit()
            // } catch (e: Exception) {
            //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
            //   }
        }
        return this
    }

    fun SyncState.syncEditStructureIdsListLocal(): SyncState {
        if (exception != null) return this
        //  try {

        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        processStructureDataDifferences(
            this.structureCategoryDataListLocal,
            this.structureCategoryDataListLocal,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, structureData, currentPath ->
                    val positionPath = getPathPositionByPathStructure(
                        StructureDataLocal(children = this.structureCategoryDataListLocal),
                        currentPath
                    )
                    if (positionPath != currentPath) {
                        this.editIdsList.add(
                            StructureEditData(
                                null,
                                currentPath.idEvent,
                                currentPath.idCategory,
                                currentPath.idSubCategory,
                                currentPath.idSubsubCategory,
                                currentPath.idQuiz,

                                positionPath.idEvent,
                                positionPath.idCategory,
                                positionPath.idSubCategory,
                                positionPath.idSubsubCategory,
                                positionPath.idQuiz,

                                "", "", "", "", "", true, false
                            )
                        )
                    }
                },
                onNoChildren = { _, structureData, currentPath ->
                    val positionPath = getPathPositionByPathStructure(
                        StructureDataLocal(children = this.structureCategoryDataListLocal),
                        currentPath
                    )
                    if (positionPath != currentPath) {
                        this.editIdsList.add(
                            StructureEditData(
                                null,
                                currentPath.idEvent,
                                currentPath.idCategory,
                                currentPath.idSubCategory,
                                currentPath.idSubsubCategory,
                                currentPath.idQuiz,

                                positionPath.idEvent,
                                positionPath.idCategory,
                                positionPath.idSubCategory,
                                positionPath.idSubsubCategory,
                                positionPath.idQuiz,

                                "", "", "", "", "", true, false
                            )
                        )
                    }
                }
            ),
        )

        return this
    }

    fun SyncState.updateIdsStructureDataLocal(): SyncState {
        if (exception != null) return this
        //  try {
        //залогировать тут все, почему-то нчиего не меняет
        this.editIdsList.forEach { editIds ->
            val findCategory =
                this.structureCategoryDataListLocal.find { it.id == editIds.idCategoryFrom }
            if (editIds.idSubCategoryTo == -1) {
                if (findCategory != null) {
                    findCategory.editThisIds(editIds)
                }
            } else {
                val findSubCategory =
                    findCategory?.children?.find { it.id == editIds.idSubCategoryFrom }
                if (editIds.idSubsubCategoryTo == -1) {
                    if (findSubCategory != null) {
                        findSubCategory.editThisIds(editIds)
                    }
                } else {
                    val findSubsubCategoryFrom =
                        findSubCategory?.children?.find { it.id == editIds.idSubsubCategoryFrom }
                    if (editIds.idQuizTo == -1) {
                        if (findSubsubCategoryFrom != null) {
                            findSubsubCategoryFrom.editThisIds(editIds)
                        }
                    } else {
                        val findQuizEventFrom =
                            findSubsubCategoryFrom?.children?.find { it.id == editIds.idQuizFrom }
                        if (findQuizEventFrom != null) {
                            findQuizEventFrom.editThisIds(editIds)
                        }
                    }
                }
            }
        }
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        return this
    }

    fun SyncState.editStructureDataRemoteByStructureEdit(repositoryStructureImpl: RepositoryStructureImpl): SyncState {
        if (exception != null) return this
        //  try {

        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        // } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
        //   }

        return this
    }

    fun SyncState.updateStructureInfoGlobal(): SyncState {
        if (exception != null) return this
        // try {
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE
        processStructureDataDifferences(
            this.structureCategoryDataListRemote,
            this.structureCategoryDataListLocal,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { structureDataNew, _, currentPath ->
                    val newInfoGlobal = this.structureInfoGlobal.findInfoByPath(currentPath)
                    if (newInfoGlobal != null) structureDataNew!![0].updateInfoGlobal(
                        newInfoGlobal
                    )
                },
                onNoChildren = { structureDataNew, _, currentPath ->
                    val newInfoGlobal = this.structureInfoGlobal.findInfoByPath(currentPath)
                    if (newInfoGlobal != null) structureDataNew!![0].updateInfoGlobal(
                        newInfoGlobal
                    )
                }
            ),
        )
        // } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
        // }

        return this
    }

    fun SyncState.updateStructureInfoLocal(repositoryStructureImpl: RepositoryStructureImpl): SyncState {
        if (exception != null) return this
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        processStructureDataDifferences(
            this.structureCategoryDataListRemote,
            this.structureCategoryDataListLocal,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { structureDataLocal, structureDataRemote, currentPathRemote ->
                    runBlocking {
                        val changeLocal =
                            repositoryStructureImpl.fetchStructureInfo(currentPathRemote.copy())

                        changeLocal?.let {

                            if (changeLocal.dateUpdate > structureDataLocal?.get(0)!!.dataUpdateLocal
                                || structureDataLocal?.get(0)!!.dataUpdateLocal == ""
                            ) {
                                runBlocking {
                                    structureDataLocal.get(0).updateInfoLocal(changeLocal.copy())
                                }
                            }
                        }

                    }
                },

                onNoChildren = { structureDataLocal, structureDataRemote, currentPathRemote ->

                    runBlocking {
                        val changeLocal =
                            repositoryStructureImpl.fetchStructureInfo(currentPathRemote.copy())

                        changeLocal?.let {

                            if (changeLocal.dateUpdate > structureDataLocal?.get(0)!!.dataUpdateLocal
                                || structureDataLocal?.get(0)!!.dataUpdateLocal == ""
                            ) {
                                runBlocking {
                                    structureDataLocal.get(0).updateInfoLocal(changeLocal.copy())
                                }
                            }

                        }

                    }
                }
            ),
        )

        return this
    }

    fun SyncState.pushInfoLocal(): SyncState {
        if (exception != null) return this
        try {
            this.currentStage = SyncStage.INFO_UPDATE_REMOTE
            processStructureDataDifferences(
                this.structureCategoryDataListRemote,
                this.structureCategoryDataListLocal,
                this.eventId,
                callback = CallbackDifferences(
                    onMissingOldStructure = { _, _, _ ->

                    },
                    onHasChildren = { _, structureDataNew, currentPath ->
                        this.structureInfoGlobal.addInfoGlobal(currentPath, structureDataNew)
                    },
                    onNoChildren = { _, structureDataNew, currentPath ->
                        this.structureInfoGlobal.addInfoGlobal(currentPath, structureDataNew)
                    }
                ),
            )
        } catch (e: Exception) {
            exceptionHandler.exceptionSyncInfo(e.message ?: "")
        }

        return this
    }

    private fun MutableList<StructureInfoEntity>.addInfoGlobal(
        currentPath: PathStructure,
        structureData: StructureDataLocal
    ) {
        this.add(
            StructureInfoEntity(
                null,
                currentPath.copy(),
                structureData.dataUpdateGlobal,
                tpovId,
                structureData.ratingGlobal,
                structureData.starsMaxGlobal,
                structureData.starsAverageGlobal,
                0,
                structureData.languages,
                structureData.isShowArchive
            )
        )
    }

    fun StructureDataLocal.updateInfoGlobal(structureInfoEntity: StructureInfoEntity) {
        this.dataUpdateGlobal = structureInfoEntity.dateUpdate
        this.starsAverageGlobal = structureInfoEntity.starsAverage
        this.starsMaxGlobal = structureInfoEntity.starsMax
        this.ratingGlobal = structureInfoEntity.rating
        this.dataUpdateGlobal = structureInfoEntity.dateUpdate
    }

    fun StructureDataLocal.updateInfoLocal(structureInfoEntity: StructureInfoEntity) {
        this.dataUpdateLocal = structureInfoEntity.dateUpdate
        this.starsAverageLocal = structureInfoEntity.starsAverage
        this.starsMaxLocal = structureInfoEntity.starsMax
        this.ratingLocal = structureInfoEntity.rating
        this.dataUpdateLocal = structureInfoEntity.dateUpdate
    }


    private fun SyncState.addInfoLocal(
        currentPath: PathStructure,
        structureDataLocal: StructureDataLocal
    ): SyncState {
        if (exception != null) return this
        this.structureInfoLocal.add(
            StructureInfoEntity(
                null,
                currentPath.copy(),
                structureDataLocal.dataUpdateLocal,
                tpovId,
                structureDataLocal.ratingLocal,
                structureDataLocal.starsMaxLocal,
                structureDataLocal.starsAverageLocal,
                0,
                structureDataLocal.languages,
                structureDataLocal.isShowArchive
            )
        )
        return this
    }

    fun SyncState.syncInfoLocal(): SyncState {
        if (this.exception != null) return this
        try {
            this.currentStage = SyncStage.INFO_UPDATE_LOCAL
            processStructureDataDifferences(
                this.structureCategoryDataListLocal,
                this.structureCategoryDataListRemote,
                this.eventId,
                callback = CallbackDifferences(
                    onMissingOldStructure = { _, _, _ -> },
                    onHasChildren = { _, structureDataNew, currentPath ->
                        this.addInfoLocal(currentPath, structureDataNew)
                    },
                    onNoChildren = { _, structureDataNew, currentPath ->
                        this.addInfoLocal(currentPath, structureDataNew)
                    }
                ),
            )
        } catch (e: Exception) {
            exceptionHandler.exceptionSyncInfo(e.message ?: "")
        }

        return this
    }

    fun SyncState.getResult(): SyncStructureResult {
        return if (exception != null) {
            SyncStructureResult.Error(currentStage, exception!!)
        } else {
            SyncStructureResult.Success(currentStage, this)
        }
    }
}
