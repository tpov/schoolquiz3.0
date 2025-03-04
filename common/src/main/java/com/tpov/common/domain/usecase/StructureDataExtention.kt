package com.tpov.common.domain.usecase

import com.tpov.common.Core.tpovId
import com.tpov.common.EventQuiz
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.domain.DomainExceptions
import com.tpov.common.domain.model.ChangeVersionStructure
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.model.SyncStage
import com.tpov.common.domain.model.SyncState
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.utils.CallbackDifferences
import com.tpov.common.domain.utils.StructureDataUtils.addNode
import com.tpov.common.domain.utils.StructureDataUtils.findStructureDataOld
import com.tpov.common.domain.utils.StructureDataUtils.isUpdateStructureLocal
import com.tpov.common.domain.utils.StructureDataUtils.isUpdateStructureRemote
import com.tpov.common.domain.utils.StructureDataUtils.processStructureDataDifferences
import com.tpov.common.domain.utils.StructureDataUtils.updateLocalInfoData
import com.tpov.common.presentation.model.PathStructure

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

    fun SyncState.syncStateLocalStructureData(): SyncState {
        if (exception != null) return this
        try {
            this.currentStage = SyncStage.STRUCTURE_LOCAL_SYNC
            StructureDataLocal(children = structureCategoryDataListRemote).printFullStructure("syncStateLocalStructureData before")

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

    fun SyncState.syncStateChangeListQuestions(): SyncState {
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
                                    this.changedListLocal.add(
                                        ChangeVersionStructure(
                                            structureNodeNew.nameItem, currentPath.copy(), false
                                        )
                                    )
                                }
                            } else {
                                this.changedListLocal.add(
                                    ChangeVersionStructure(
                                        structureNodeNew.nameItem, currentPath.copy(), true
                                    )
                                )
                            }
                        }
                    }
                ),
            )

            processStructureDataDifferences(
                this.structureCategoryDataListLocal,
                this.structureCategoryDataListLocal,
                this.eventId,
                callback = CallbackDifferences(
                    onMissingOldStructure = { structureNodeListOld, structureNodeNew, currentPath ->
                        structureNodeNew.printFullStructure("structureNodeNew")
                        structureNodeListOld?.get(0)?.printFullStructure("structureNodeListOld")
                        StructureUseCase.Log.d("onMissingOldStructure", "currentPath: $currentPath: ")
                                            },
                    onHasChildren = { _, _, _ -> },
                    onNoChildren = { structureNodeListOld, structureNodeNew, currentPath ->

                        StructureUseCase.Log.d("onNoChildren", "currentPath isUpdateStructureRemote: $currentPath")
                        val findRemoteQuizByPath = findStructureDataOld(
                            this.structureCategoryDataListRemote,
                            this.structureCategoryDataListLocal,
                            currentPath
                        )
                        findRemoteQuizByPath.structureData?.printFullStructure("onNoChildren")
                        StructureUseCase.Log.d("onNoChildren", "findRemoteQuizByPath: $findRemoteQuizByPath")
                        if (findRemoteQuizByPath.structureData != null) {
                            if (isUpdateStructureRemote(findRemoteQuizByPath.structureData, structureNodeNew)) {
                                StructureUseCase.Log.d("syncStateChangeListQuestions", "currentPath isUpdateStructureRemote: ")
                                StructureUseCase.Log.d("syncStateChangeListQuestions", "currentPath isUpdateStructureRemote: $currentPath")
                                this.changedListRemote.add(
                                    ChangeVersionStructure(
                                        structureNodeNew.nameItem, currentPath.copy(), false
                                    )
                                )
                            }
                        } else {
                            StructureUseCase.Log.d("syncStateChangeListQuestions", "currentPath isUpdateStructureRemote: ")
                            StructureUseCase.Log.d("syncStateChangeListQuestions", "currentPath !isUpdateStructureRemote: $currentPath")
                            this.changedListRemote.add(
                                ChangeVersionStructure(
                                    structureNodeNew.nameItem, currentPath.copy(), true
                                )
                            )
                        }
                    }
                ),
            )

      //  } catch (e: Exception) {
       //     exceptionHandler.exceptionSyncQuestionRemote(e.message ?: "")
      //  }
        return this
    }

    fun SyncState.syncStateInfoRemote(): SyncState {
        if (exception != null) return this
        try {
            this.currentStage = SyncStage.INFO_UPDATE_REMOTE
            processStructureDataDifferences(
                this.structureCategoryDataListLocal,
                this.structureCategoryDataListLocal,
                this.eventId,
                callback = CallbackDifferences(
                    onMissingOldStructure = { _, _, _ -> },
                    onHasChildren = { _, structureDataNew, currentPath ->
                        this.updateInfoRemote(currentPath,structureDataNew)
                   },
                    onNoChildren = { _, structureDataNew, currentPath ->
                       this.updateInfoRemote(currentPath,structureDataNew)
                    }
                ),
            )
        } catch (e: Exception) {
            exceptionHandler.exceptionSyncInfo(e.message ?: "")
        }
        return this
    }

    private fun SyncState.updateInfoRemote(
        currentPath: PathStructure,
        structureData: StructureDataLocal
    ): SyncState {
            this.structureInfoRemote.add(
                StructureInfoEntity(
                    null,
                    currentPath.copy(),
                    tpovId,
                    structureData.ratingGlobal,
                    structureData.starsMaxGlobal,
                    structureData.starsAverageGlobal,
                    0
                )
            )
        return this
    }

    private fun SyncState.updateInfoLocal(
        currentPath: PathStructure,
        structureDataLocal: StructureDataLocal
    ): SyncState {
            this.structureInfoLocal.add(
                StructureInfoEntity(
                    null,
                    currentPath.copy(),
                    tpovId,
                    structureDataLocal.ratingLocal,
                    structureDataLocal.starsMaxLocal,
                    structureDataLocal.starsAverageLocal,
                    0
                )
            )
        return this
    }

    fun SyncState.syncStateInfoLocal(): SyncState {
        if (this.exception != null) return this
        try {
            this.currentStage = SyncStage.INFO_UPDATE_LOCAL
            processStructureDataDifferences(
                this.structureCategoryDataListLocal,
                this.structureCategoryDataListLocal,
                this.eventId,
                callback = CallbackDifferences(
                    onMissingOldStructure = { _, _, _ -> },
                    onHasChildren = { _, structureDataNew , currentPath ->
                        this.updateInfoLocal(currentPath, structureDataNew)
                    },
                    onNoChildren = { _,  structureDataNew, currentPath ->
                        this.updateInfoLocal(currentPath, structureDataNew)
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