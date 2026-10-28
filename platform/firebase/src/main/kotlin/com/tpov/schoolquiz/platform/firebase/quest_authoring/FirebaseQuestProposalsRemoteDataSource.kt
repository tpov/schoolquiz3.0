package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestProposalsWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestProposalsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalPage
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт «Предложений» (история 4.25, UX-22): страница прав и решение автора.
 *
 * Имена коллблов — ровно регистрации в `functions/index.js` (сторож —
 * [QuestProposalsCallableNamesTest]); отказ транспорта едет как [SyncFailure], отказ строгого
 * декодера — тоже отказ чтения.
 */
class FirebaseQuestProposalsRemoteDataSource(
    private val functions: FirebaseFunctions,
) : QuestProposalsRemoteDataSource {
    override suspend fun fetchProposals(
        box: QuestProposalBox,
        limit: Int,
        cursor: String?,
    ): QuestProposalPage =
        try {
            val payload = buildMap<String, Any> {
                put(BOX, QuestProposalsWireParser.boxName(box))
                put(LIMIT, limit)
                if (cursor != null) put(CURSOR, cursor)
            }
            val reply =
                functions
                    .getHttpsCallable(QuestProposalsCallableNames.FETCH)
                    .withAppTimeout()
                    .call(payload)
                    .await()
                    .data
            QuestProposalsWireParser.questProposalsPage(reply)
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            throw SyncFailure(error.toSyncError(), error)
        }

    override suspend fun decideProposal(
        proposalId: String,
        decision: String,
        comment: String,
    ) {
        try {
            functions
                .getHttpsCallable(QuestProposalsCallableNames.DECIDE)
                .withAppTimeout()
                .call(mapOf(PROPOSAL_ID to proposalId, DECISION to decision, COMMENT to comment))
                .await()
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            throw SyncFailure(error.toSyncError(), error)
        }
    }
}

/** Имена callable-поверхности «Предложений» — ровно регистрации в `functions/index.js`. */
internal object QuestProposalsCallableNames {
    const val FETCH = "fetchQuestProposals"
    const val DECIDE = "decideLessonProposal"
}

private const val BOX = "box"
private const val LIMIT = "limit"
private const val CURSOR = "cursor"
private const val PROPOSAL_ID = "proposalId"
private const val DECISION = "decision"
private const val COMMENT = "comment"
