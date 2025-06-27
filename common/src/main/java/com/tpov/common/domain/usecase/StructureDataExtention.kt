package com.tpov.common.domain.usecase

import com.tpov.common.data.RepositoryStructureImpl
import com.tpov.common.data.model.entity.StructureInfoEntity
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.domain.DomainExceptions
import com.tpov.common.domain.model.ChangeVersionStructure
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.SyncStage
import com.tpov.common.domain.model.SyncState
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.domain.utils.CallbackDifferences
import com.tpov.common.domain.utils.QuestionUtils.isDownloadQuestionForOptimization
import com.tpov.common.domain.utils.StructureDataUtils.addNodeByPath
import com.tpov.common.domain.utils.StructureDataUtils.findInfoByPath
import com.tpov.common.domain.utils.StructureDataUtils.findStructureDataOld
import com.tpov.common.domain.utils.StructureDataUtils.isUpdateStructure
import com.tpov.common.domain.utils.StructureDataUtils.processStructureDataDifferences
import com.tpov.common.domain.utils.StructureDataUtils.removeNodeByPath
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

    suspend fun SyncState.initStateStructureData(structureUseCase: StructureUseCase): SyncState {
        if (exception != null) return this
        this.currentStage = SyncStage.STRUCTURE_FETCH
        
        android.util.Log.d("StructureSync", "🔄 initStateStructureData started for: ${this.eventId.name}")
        
        this.structureCategoryDataListRemote =
            structureUseCase.fetchStructureCategoryDataList(this.eventId)
                ?.toMutableList() ?: exceptionHandler.exceptionInitStructureRemoteData()

        android.util.Log.d("StructureSync", "📡 Remote data fetched: ${this.structureCategoryDataListRemote.size} categories")
        for (category in this.structureCategoryDataListRemote) {
            android.util.Log.d("StructureSync", "  📂 Remote: ${category.nameItem} with ${category.children?.size ?: 0} children")
        }

        this.structureCategoryDataListLocal =
            structureUseCase.getStructureEventData(this.eventId)?.toMutableList() ?: mutableListOf()

        android.util.Log.d("StructureSync", "💾 Local data fetched: ${this.structureCategoryDataListLocal.size} categories")
        for (category in this.structureCategoryDataListLocal) {
            android.util.Log.d("StructureSync", "  📂 Local: ${category.nameItem} with ${category.children?.size ?: 0} children")
        }

        return this
    }

    fun SyncState.updateLocalStructureData(): SyncState {
        if (exception != null) return this
        this.currentStage = SyncStage.STRUCTURE_LOCAL_SYNC

        android.util.Log.d("StructureSync", "🔄 updateLocalStructureData started")
        android.util.Log.d("StructureSync", "📡 Remote categories to process: ${this.structureCategoryDataListRemote.size}")
        android.util.Log.d("StructureSync", "💾 Local categories before processing: ${this.structureCategoryDataListLocal.size}")

        processStructureDataDifferences(
            structureNodeListNew = this.structureCategoryDataListRemote,
            structureNodeListOld = this.structureCategoryDataListLocal,
            event = this.eventId,
            callback = CallbackDifferences(
                onMissingOldStructure = { _, structureNodeNew, currentPath ->
                    android.util.Log.d("StructureSync", "➕ Adding missing structure: ${structureNodeNew.nameItem} at path: $currentPath")
                    
                    this.structureCategoryDataListLocal.addNodeByPath(structureNodeNew, currentPath)
                    android.util.Log.d("StructureSync", "✅ Added. Local categories now: ${this.structureCategoryDataListLocal.size}")
                },
                onHasChildren = { structureNodeOld, structureNodeNew, currentPath ->
                    android.util.Log.d("StructureSync", "🔄 Processing existing structure with children: ${structureNodeNew.nameItem}")
                    
                    if (isUpdateStructure(structureNodeOld?.first(), structureNodeNew)) {
                        android.util.Log.d("StructureSync", "📝 Updating structure: ${structureNodeNew.nameItem}")
                        this.structureCategoryDataListLocal
                            .updateLocalInfoData(
                                this.structureCategoryDataListRemote,
                                currentPath
                            )
                    }
                },
                onNoChildren = { structureNodeOld, structureNodeNew, currentPath ->
                    android.util.Log.d("StructureSync", "📄 Processing leaf structure: ${structureNodeNew.nameItem}")
                    
                    if (isUpdateStructure(structureNodeOld?.first(), structureNodeNew)) {
                        android.util.Log.d("StructureSync", "📝 Updating leaf structure: ${structureNodeNew.nameItem}")
                        this.structureCategoryDataListLocal
                            .updateLocalInfoData(
                                this.structureCategoryDataListRemote,
                                currentPath
                            )
                    }
                }
            ),
        )
        
        android.util.Log.d("StructureSync", "✅ updateLocalStructureData completed")
        android.util.Log.d("StructureSync", "💾 Local categories after processing: ${this.structureCategoryDataListLocal.size}")
        
        return this
    }

    /**
     * Эта функция генерирует список вопросов который нужно загрузить в локальную БД.
     * Некоторые вопросы нужно обновить, некоторые добавить.
     * Подходит для всех типов квестов
     */
    fun SyncState.syncChangeListQuestionsLocal(): SyncState {
        if (exception != null) return this
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
        this.currentStage = SyncStage.QUESTION_CHANGE_LIST
        if (this.eventId == EventQuiz.QUIZ_BY_USER) {
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
        return this
    }

    /**
     * Заполняем поле с глобальными инфо в структуре
     */
    fun SyncState.syncInfoGlobal(): SyncState {
        if (exception != null) return this
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
        return this
    }

    /**
     * Редактируем вопросы согласно списку changedListQuestionLocal
     */
    fun SyncState.updateLocalQuestion(questionUseCase: QuestionUseCase): SyncState {
        if (exception != null) return this
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE
        runBlocking {
            this@updateLocalQuestion.changedListQuestionLocal.forEach { change ->
                val remoteQuestions =
                    questionUseCase.fetchQuestion(change.pathStructure, settingsConfig.languages)
                val pathLocal = findStructureDataOld(
                    this@updateLocalQuestion.structureCategoryDataListLocal,
                    this@updateLocalQuestion.structureCategoryDataListRemote,
                    change.pathStructure
                ).pathOld

                if (!change.isCreate) questionUseCase.deleteQuestionByPath(pathLocal)

                remoteQuestions.forEach {
                    runBlocking {
                        questionUseCase.insertQuestion(it.copy(pathStructure = pathLocal))
                    }
                }

            }
        }
        return this
    }

    /**
     * Редактируем вопросы согласно списку changedListQuestionRemote
     */
    fun SyncState.updateRemoteQuestion(questionUseCase: QuestionUseCase): SyncState {
        if (exception != null) return this
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE
        runBlocking {
            this@updateRemoteQuestion.changedListQuestionRemote.forEach { change ->
                if (change.name == "-1") {
                    questionUseCase.deleteQuestionByPath(change.pathStructure)
                    if (!change.isCreate) questionUseCase.deleteQuestionByPath(change.pathStructure)
                } else {
                    val localQuestions = questionUseCase.getQuestionByPath(change.pathStructure)

                    if (!change.isCreate) questionUseCase.deleteQuestionByPath(change.pathStructure)

                    localQuestions.forEach { questionUseCase.pushQuestion(it) }
                }

            }
        }

        return this
    }


    fun SyncState.updateStructureLocalNumberQuestion(questionUseCase: QuestionUseCase): SyncState {
        if (exception != null) return this
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

        return this
    }

    fun SyncState.unlockServer(repositoryStructureImpl: RepositoryStructureImpl): SyncState {
        if (exception != null) return this
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE

        return this
    }

    fun SyncState.lockServer(repositoryStructureImpl: RepositoryStructureImpl): SyncState {
        if (exception != null) return this
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE


        return this
    }


    fun SyncState.clearStructureLocal(
        questionUseCase: QuestionUseCase,
        questionDetailUseCase: QuestionDetailUseCase
    ): SyncState {
        if (exception != null) return this
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
                        structureCategoryDataListLocal.removeNodeByPath(currentPath)
                        runBlocking {
                            questionUseCase.deleteQuestionByPath(currentPath)
                            questionDetailUseCase.deleteRemoteQuestionDetailByPath(
                                currentPath
                            )
                        }
                    }

                },
                onNoChildren = { _, structureDataNew, currentPath ->
                    if (structureDataNew.dataUpdateLocal == "-1") {
                        structureCategoryDataListLocal.removeNodeByPath(currentPath)
                        runBlocking {
                            questionUseCase.deleteQuestionByPath(currentPath)
                            questionDetailUseCase.deleteRemoteQuestionDetailByPath(
                                currentPath
                            )
                        }
                    }
                }
            ),
        )
        return this
    }

    /**
     * Помечаем квесты которые нужно удалить на сервере
     */
    fun SyncState.addEditIdsStructureRemote(structureUseCase: StructureUseCase): SyncState {
        if (exception != null) return this
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
                                currentPath.nameEvent,
                                currentPath.nameCategory,
                                currentPath.nameSubCategory,
                                currentPath.nameSubsubCategory,
                                currentPath.nameQuiz,
                                "",
                                "",
                                "",
                                "",
                                "",
                                true,
                                true
                            )
                            structureUseCase.insertEditStructure(structureEditRemote)
                        }
                    }
                },

                onNoChildren = { _, structureDataNew, currentPath ->
                    if (structureDataNew.dataUpdateGlobal == "-1") {
                        runBlocking {
                            val structureEditRemote = StructureEditData(
                                null,
                                currentPath.nameEvent,
                                currentPath.nameCategory,
                                currentPath.nameSubCategory,
                                currentPath.nameSubsubCategory,
                                currentPath.nameQuiz,
                                "",
                                "",
                                "",
                                "",
                                "",
                                true,
                                true
                            )
                            structureUseCase.insertEditStructure(structureEditRemote)
                        }
                    }
                }
            ),
        )

        return this
    }

    fun SyncState.syncQuestionDetails(questionDetailUseCase: QuestionDetailUseCase): SyncState {
        if (exception != null) return this
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
                        val questionDetailListRemote = questionDetailUseCase.fetchQuestionDetail(currentPath)
                        val questionDetailListLocal = questionDetailUseCase.getQuestionDetailByPath(currentPath)

                        questionDetailListRemote.forEach { questionDetailRemote ->
                            if (questionDetailListLocal?.find { it.data == questionDetailRemote.data } == null)
                                questionDetailUseCase.saveQuestionDetail(questionDetailRemote)
                        }

                        questionDetailListLocal?.forEach { questionDetailLocal ->
                            if (!questionDetailLocal.sync) questionDetailUseCase.pushQuestionDetail(
                                questionDetailLocal
                            )
                        }
                    }
                }
            ),
        )
        return this
    }

    fun SyncState.editStructureRemote(structureUseCase: StructureUseCase): SyncState {
        if (exception != null) return this
        this.currentStage = SyncStage.INFO_UPDATE_REMOTE
        runBlocking {
            structureUseCase.getEditStructure().forEach {
                structureUseCase.pushEditStructure(it)
            }
            structureUseCase.clearStructureEdit()
        }
        return this
    }

    fun SyncState.updateStructureInfoGlobal(): SyncState {
        if (exception != null) return this
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
        return this
    }

    fun SyncState.updateStructureInfoLocal(structureUseCase: StructureUseCase): SyncState {
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
                        val changeLocal = structureUseCase.fetchStructureInfo(currentPathRemote.copy())

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
                        val changeLocal = structureUseCase.fetchStructureInfo(currentPathRemote.copy())

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
                settingsConfig.tpovId,
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
                settingsConfig.tpovId,
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
        return this
    }

    suspend fun SyncState.getResult(structureUseCase: StructureUseCase): SyncStructureResult {
        return if (exception != null) {
            SyncStructureResult.Error(currentStage, exception!!)
        } else {
            try {
                android.util.Log.d("StructureSync", "💾 Saving ${structureCategoryDataListLocal.size} categories to local database for event: ${eventId.name}")
                structureUseCase.updateStructureDataList(structureCategoryDataListLocal, eventId)
                android.util.Log.d("StructureSync", "✅ Successfully saved structure data to local database")
            } catch (e: Exception) {
                android.util.Log.e("StructureSync", "❌ Error saving structure data to local database: ${e.message}", e)
                return SyncStructureResult.Error(currentStage, e.message ?: "Unknown error")
            }
            SyncStructureResult.Success(currentStage, this)
        }
    }
}
