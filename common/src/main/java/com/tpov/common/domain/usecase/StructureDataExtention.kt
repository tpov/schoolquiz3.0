package com.tpov.common.domain.usecase

import com.tpov.common.Core.tpovId
import com.tpov.common.EventQuiz
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.domain.DomainExceptions
import com.tpov.common.domain.model.ChangeVersionStructure
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.model.SyncStage
import com.tpov.common.domain.model.SyncState
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.utils.CallbackDifferences
import com.tpov.common.domain.utils.StructureDataUtils.add
import com.tpov.common.domain.utils.StructureDataUtils.addNode
import com.tpov.common.domain.utils.StructureDataUtils.findInfoByPath
import com.tpov.common.domain.utils.StructureDataUtils.findStructureDataOld
import com.tpov.common.domain.utils.StructureDataUtils.isUpdateStructureLocal
import com.tpov.common.domain.utils.StructureDataUtils.isUpdateStructureRemote
import com.tpov.common.domain.utils.StructureDataUtils.processStructureDataDifferences
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

    suspend fun SyncState.initStateStructureData(repositoryStructureImpl: RepositoryStuctureImpl): SyncState {
        if (exception != null) return this
        try {
            this.currentStage = SyncStage.STRUCTURE_FETCH
            this.structureCategoryDataListRemote =
                repositoryStructureImpl.fetchStructureCategoryDataList(this.eventId)
                    ?.toMutableList() ?: exceptionHandler.exceptionInitStructureRemoteData()

            this.structureCategoryDataListLocal =
                repositoryStructureImpl.getStructureEventData(this.eventId).toMutableList()

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
                        if (isUpdateStructureLocal(structureNodeOld?.first(), structureNodeNew)) {

                            this.structureCategoryDataListLocal
                                .updateLocalInfoData(
                                    this.structureCategoryDataListRemote,
                                    currentPath
                                )
                        }
                    },
                    onNoChildren = { structureNodeOld, structureNodeNew, currentPath ->
                        if (isUpdateStructureLocal(structureNodeOld?.first(), structureNodeNew)) {
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
                    val findLocalQuizByPath = findStructureDataOld(
                        this.structureCategoryDataListLocal,
                        this.structureCategoryDataListRemote,
                        currentPath
                    )
                    if (findLocalQuizByPath.structureData?.isShowDownload == true
                        || this.eventId == EventQuiz.QUIZ_BY_USER.id
                    ) {
                        if (findLocalQuizByPath.structureData != null) {
                            if (isUpdateStructureRemote(
                                    findLocalQuizByPath.structureData,
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
                            this.changedListQuestionLocal.add(
                                ChangeVersionStructure(
                                    structureNodeNew.nameItem, currentPath.copy(), true
                                )
                            )
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

    fun SyncState.syncChangeListQuestionsRemote(): SyncState {
        if (exception != null) return this
        // try {
        this.currentStage = SyncStage.QUESTION_CHANGE_LIST

        processStructureDataDifferences(
            this.structureCategoryDataListLocal,
            this.structureCategoryDataListLocal,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, _, _ -> },
                onNoChildren = { _, structureNodeNew, currentPath ->


                    val findRemoteQuizByPath = findStructureDataOld(
                        this.structureCategoryDataListRemote,
                        this.structureCategoryDataListLocal,
                        currentPath
                    )
                    if (
                        currentPath.idCategory == 3
                        && currentPath.idSubCategory == 3
                        && currentPath.idSubsubCategory == 2
                    ) {
                        StructureUseCase.Log.d(
                            "syncChangeListQuestionsRemote 332",
                            "currentPath: $currentPath"
                        )
                        StructureUseCase.Log.d(
                            "syncChangeListQuestionsRemote 332",
                            "findRemoteQuizByPath: $findRemoteQuizByPath"
                        )

                    }

                    if (findRemoteQuizByPath.structureData != null) {
                        StructureUseCase.Log.d(
                            "syncChangeListQuestionsRemote",
                            "findRemoteQuizByPath.structureData != null"
                        )
                        if (isUpdateStructureRemote(
                                findRemoteQuizByPath.structureData,
                                structureNodeNew
                            )
                        ) {
                            StructureUseCase.Log.d(
                                "syncChangeListQuestionsRemote",
                                "isUpdateStructureRemote"
                            )
                            this.changedListQuestionRemote.add(
                                ChangeVersionStructure(
                                    structureNodeNew.nameItem, currentPath.copy(), false
                                )
                            )
                        }
                    } else {
                        StructureUseCase.Log.d(
                            "syncChangeListQuestionsRemote",
                            "findRemoteQuizByPath.structureData == null"
                        )
                        this.changedListQuestionRemote.add(
                            ChangeVersionStructure(
                                structureNodeNew.nameItem, currentPath.copy(), true
                            )
                        )
                    }
                    this.changedListQuestionRemote.forEach {
                        StructureUseCase.Log.d("onNoChildren", "before currentPathList: $it")
                    }
                }
            ),
        )
        this.changedListQuestionRemote.forEach {
            StructureUseCase.Log.d("onNoChildren", "after currentPathList: $it")
        }
        //  } catch (e: Exception) {
        //     exceptionHandler.exceptionSyncQuestionRemote(e.message ?: "")
        //  }
        return this
    }

    fun SyncState.syncInfoRemote(): SyncState {
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

    fun SyncState.updateLocalQuestion(repositoryQuestionImpl: RepositoryQuestionImpl): SyncState {
        if (exception != null) return this
            // try {
            this.currentStage = SyncStage.INFO_UPDATE_REMOTE
            runBlocking {
                this@updateLocalQuestion.changedListQuestionLocal.forEach { change ->
                    try {
                        val remoteQuestions =
                            repositoryQuestionImpl.fetchQuestion(change.pathStructure, "ru|ua|en")

                        if (!change.isCreate) repositoryQuestionImpl.deleteQuestionByPath(change.pathStructure)

                        remoteQuestions.forEach { repositoryQuestionImpl.saveQuestion(it)}
                    } catch (e: Exception) {

                    }

                }
            }

       // } catch (e: Exception) {
         //   exceptionHandler.exceptionSyncInfo(e.message ?: "")
       // }

        return this
    }

    fun SyncState.updateRemoteQuestion(repositoryQuestionImpl: RepositoryQuestionImpl): SyncState {
        if (exception != null) return this
       // try {
            this.currentStage = SyncStage.INFO_UPDATE_REMOTE
            runBlocking {
                this@updateRemoteQuestion.changedListQuestionRemote.forEach { change ->
                    try {
                        val localQuestions =
                            repositoryQuestionImpl.getQuestionsByPath(change.pathStructure)

                        if (!change.isCreate) repositoryQuestionImpl.deleteQuestionByPath(change.pathStructure)

                        localQuestions.forEach { repositoryQuestionImpl.pushQuestion(it)}
                    } catch (e: Exception) {

                    }

                }
            }

      //  } catch (e: Exception) {
      //      exceptionHandler.exceptionSyncInfo(e.message ?: "")
       // }

        return this
    }

    fun SyncState.updateStructureNumberQuestion(repositoryQuestionImpl: RepositoryQuestionImpl): SyncState {
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
fun SyncState.updateStructureRemote(): SyncState {
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

                    },
                    onNoChildren = { _, structureDataNew, currentPath ->
                        runBlocking {

                        }

                    }
                ),
            )
       // } catch (e: Exception) {
       //     exceptionHandler.exceptionSyncInfo(e.message ?: "")
     //   }

        return this
    }
fun SyncState.syncQuestionDetails(): SyncState {
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

                    },
                    onNoChildren = { _, structureDataNew, currentPath ->
                        runBlocking {

                        }

                    }
                ),
            )
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

    fun SyncState.updateStructureInfoLocal(repositoryStructureImpl: RepositoryStuctureImpl): SyncState {
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
                                || structureDataLocal?.get(0)!!.dataUpdateLocal == "") {
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
                                || structureDataLocal?.get(0)!!.dataUpdateLocal == "") {
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
                0
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

    fun SyncState.syncEditListIdsRemoteQuestion(
    ): SyncState {
        if (exception != null) return this

        processStructureDataDifferences(
            this.structureCategoryDataListLocal,
            this.structureCategoryDataListRemote,
            this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, _, _ -> },
                onHasChildren = { _, _, toPath -> },
                onNoChildren = { _, _, toPath ->
                    val fromPath = findStructureDataOld(
                        this.structureCategoryDataListRemote,
                        this.structureCategoryDataListLocal,
                        toPath
                    ).pathOld
                    if (fromPath != toPath) this.editIdsList.add(fromPath, toPath)
                }
            ),
        )

        editIdsList.forEach {
            StructureUseCase.Log.d(
                "syncEditListIdsRemoteQuestion editIdsList",
                "${it.idCategoryFrom} - ${it.idCategoryTo}, ${it.idSubCategoryFrom} - ${it.idSubCategoryTo}, ${it.idSubsubCategoryFrom} - ${it.idSubsubCategoryTo}, ${it.idQuizFrom} - ${it.idQuizTo}"
            )
        }

        return this
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
                0
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