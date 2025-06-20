package com.tpov.common.domain.usecase

import com.tpov.common.ExceptionInteractor
import com.tpov.common.domain.DomainExceptions
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.SyncState
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.usecase.StructureDataExtention.addEditIdsStructureRemote
import com.tpov.common.domain.usecase.StructureDataExtention.clearStructureLocal
import com.tpov.common.domain.usecase.StructureDataExtention.editStructureRemote
import com.tpov.common.domain.usecase.StructureDataExtention.getResult
import com.tpov.common.domain.usecase.StructureDataExtention.init
import com.tpov.common.domain.usecase.StructureDataExtention.initStateStructureData
import com.tpov.common.domain.usecase.StructureDataExtention.syncChangeListQuestionsLocal
import com.tpov.common.domain.usecase.StructureDataExtention.syncChangeListQuestionsRemote
import com.tpov.common.domain.usecase.StructureDataExtention.syncInfoGlobal
import com.tpov.common.domain.usecase.StructureDataExtention.syncInfoLocal
import com.tpov.common.domain.usecase.StructureDataExtention.syncQuestionDetails
import com.tpov.common.domain.usecase.StructureDataExtention.updateLocalQuestion
import com.tpov.common.domain.usecase.StructureDataExtention.updateLocalStructureData
import com.tpov.common.domain.usecase.StructureDataExtention.updateRemoteQuestion
import com.tpov.common.domain.usecase.StructureDataExtention.updateStructureInfoGlobal
import com.tpov.common.domain.usecase.StructureDataExtention.updateStructureInfoLocal
import com.tpov.common.domain.usecase.StructureDataExtention.updateStructureLocalNumberQuestion
import javax.inject.Inject

open class SyncInteractor @Inject constructor(
    private val settingServerDBUseCase: SettingServerDBUseCase,
    private val settingLocalDBUseCase: SettingLocalDBUseCase,
    private val structureUseCase: StructureUseCase,
    private val questionUseCase: QuestionUseCase,
    private val questionDetailUseCase: QuestionDetailUseCase
) {

    fun unlockStructureData(event: EventQuiz) = settingServerDBUseCase.unlockStructureData(event)
    fun lockStructureData(event: EventQuiz) = settingServerDBUseCase.lockStructureData(event)
    fun isLockServer(event: EventQuiz) = settingServerDBUseCase.isLockServer(event)
    fun rollbackStructureData(event: EventQuiz) = settingLocalDBUseCase.rollbackStructureData(event)

    suspend fun syncQuizes(eventQuiz: EventQuiz, exceptionInteractor: ExceptionInteractor): SyncStructureResult {
        val syncState = SyncState(eventQuiz)
        init(
            DomainExceptions(
                beforeException = { syncState.exception = it },
                afterException = {},
                exceptionInteractor
            )
        )

        return syncState
            .initStateStructureData(structureUseCase)
            .syncChangeListQuestionsLocal()
            .syncChangeListQuestionsRemote()
            .syncInfoLocal()
            .updateLocalStructureData()
            .syncInfoGlobal()
            .updateStructureInfoGlobal()
            .updateStructureInfoLocal(structureUseCase)
            .updateLocalQuestion(questionUseCase)
            .addEditIdsStructureRemote(structureUseCase)
            .clearStructureLocal(questionUseCase, questionDetailUseCase)
            .updateStructureLocalNumberQuestion(questionUseCase)
            .syncQuestionDetails(questionDetailUseCase)
            .updateRemoteQuestion(questionUseCase)
            .editStructureRemote(structureUseCase)
            .getResult()
    }
}

