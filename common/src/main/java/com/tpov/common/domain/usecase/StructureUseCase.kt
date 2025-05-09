package com.tpov.common.domain.usecase

import com.tpov.common.Interactor
import com.tpov.common.data.RepositoryQuestionDetailImpl
import com.tpov.common.data.RepositoryQuestionImpl
import com.tpov.common.data.RepositoryStructureImpl
import com.tpov.common.domain.DomainExceptions
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.model.SyncState
import com.tpov.common.domain.model.SyncStructureResult
import com.tpov.common.domain.repository.RepositoryException
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

open class StructureUseCase @Inject constructor(
    private val repositoryStructureImpl: RepositoryStructureImpl,
    private val repositoryQuestionImpl: RepositoryQuestionImpl,
    private val repositoryQuestionDetailImpl: RepositoryQuestionDetailImpl,
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
            .syncInfoGlobal()
            .updateStructureInfoGlobal()
            .updateStructureInfoLocal(repositoryStructureImpl)
            .updateLocalQuestion(repositoryQuestionImpl)
            .addEditIdsStructureRemote(repositoryStructureImpl)
            .clearStructureLocal(repositoryQuestionImpl, repositoryQuestionDetailImpl)
            .updateStructureLocalNumberQuestion(repositoryQuestionImpl)
            .syncQuestionDetails(repositoryQuestionDetailImpl)
            .updateRemoteQuestion(repositoryQuestionImpl)
            .editStructureRemote(repositoryStructureImpl)
            //.syncEditStructureIdsListLocal()
            //.updateIdsStructureDataLocal()
            //.updateIdsQuestions()
            //.updateIdsQuestionDetail()
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

