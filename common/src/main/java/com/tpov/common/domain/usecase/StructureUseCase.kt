package com.tpov.common.domain.usecase

import com.tpov.common.Interactor
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.domain.DomainExceptions
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.model.SyncState
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.repository.RepositoryException
import com.tpov.common.domain.usecase.StructureDataExtention.getResult
import com.tpov.common.domain.usecase.StructureDataExtention.init
import com.tpov.common.domain.usecase.StructureDataExtention.initStateStructureData
import com.tpov.common.domain.usecase.StructureDataExtention.syncChangeListQuestionsLocal
import com.tpov.common.domain.usecase.StructureDataExtention.syncChangeListQuestionsRemote
import com.tpov.common.domain.usecase.StructureDataExtention.syncEditListIdsRemoteQuestion
import com.tpov.common.domain.usecase.StructureDataExtention.syncInfoLocal
import com.tpov.common.domain.usecase.StructureDataExtention.syncInfoRemote
import com.tpov.common.domain.usecase.StructureDataExtention.syncQuestionDetails
import com.tpov.common.domain.usecase.StructureDataExtention.updateLocalQuestion
import com.tpov.common.domain.usecase.StructureDataExtention.updateLocalStructureData
import com.tpov.common.domain.usecase.StructureDataExtention.updateRemoteQuestion
import com.tpov.common.domain.usecase.StructureDataExtention.updateStructureInfoGlobal
import com.tpov.common.domain.usecase.StructureDataExtention.updateStructureInfoLocal
import com.tpov.common.domain.usecase.StructureDataExtention.updateStructureNumberQuestion
import com.tpov.common.domain.usecase.StructureDataExtention.updateStructureRemote
import javax.inject.Inject

open class StructureUseCase @Inject constructor(
    private val repositoryStructureImpl: RepositoryStuctureImpl,
    private val repositoryQuestionImpl: RepositoryQuestionImpl,
    private val repositoryException: RepositoryException,
    private val interactor: Interactor
) {

    fun logger(i: Int) {
        Log.d("logger", i.toString())
    }

    suspend fun fetchStructureData(event: Int) = StructureDataLocal(
        children = repositoryStructureImpl.fetchStructureCategoryDataList(event)?.toMutableList()
    )

    suspend fun getStructureCategoryList(event: Int) =
        repositoryStructureImpl.getStructureEventData(event)

    suspend fun syncStructureDataAndGetChangeLists(eventId: Int): SyncStructureResult {
        val syncState = SyncState(eventId)
        init(
            DomainExceptions(
                beforeException = { syncState.exception = it },
                afterException = {},
                interactor
            )
        )
        return syncState
            .initStateStructureData(repositoryStructureImpl)
            .syncChangeListQuestionsLocal()
            .syncChangeListQuestionsRemote()
            .syncInfoLocal()
            .updateLocalStructureData()
            .syncEditListIdsRemoteQuestion()
            .syncInfoRemote()
            .updateStructureInfoGlobal()
            .updateStructureInfoLocal(repositoryStructureImpl)
            .updateLocalQuestion(repositoryQuestionImpl)
            .updateRemoteQuestion(repositoryQuestionImpl)
            .updateStructureNumberQuestion(repositoryQuestionImpl)
            .updateStructureRemote()
            .syncQuestionDetails()
            .getResult()
    }

    suspend fun updateStructureData(structureDataLocal: StructureDataLocal, eventId: Int) {
        repositoryStructureImpl.updateStructureData(
            structureDataLocal.toStructureDataEntity()!!,
            eventId
        )
    }

    object Log {
        fun d(tag: String, msg: String): Int {
            println("$tag: $msg")
            return 0
        }
    }
}

