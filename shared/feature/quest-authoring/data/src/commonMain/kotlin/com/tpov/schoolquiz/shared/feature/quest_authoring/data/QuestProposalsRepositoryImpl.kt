package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestProposalsWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestProposalsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestProposalsRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ProposalDecisionTransport
import kotlinx.coroutines.CancellationException

/**
 * «Предложения» (история 4.25, UX-22): тонкий перевод строгой серверной формы в домен и
 * решение автора тем же телом приёмника 4.21. Право решает сервер — отказ остаётся отказом.
 */
class QuestProposalsRepositoryImpl(
    private val remote: QuestProposalsRemoteDataSource,
) : QuestProposalsRepository,
    ProposalDecisionTransport {
    override suspend fun fetch(
        box: QuestProposalBox,
        limit: Int,
        cursor: String?,
    ): Result<QuestProposalPage> =
        try {
            Result.success(remote.fetchProposals(box, limit, cursor))
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }

    override suspend fun decideProposal(
        proposalId: String,
        decision: String,
        comment: String,
    ): Result<Unit> =
        try {
            remote.decideProposal(proposalId, decision, comment)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Result.failure(e)
        }
}

/** Имя view-конверта для сторожей транспортного модуля. */
object QuestProposalsWire {
    const val VIEW_VERSION: String = QuestProposalsWireParser.VIEW_VERSION
}
