package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.CopyEditProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopy
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote

/**
 * Предложения автору (AD-33, истории 4.22–4.24): подачи — общая очередь (AD-6),
 * цитата платы и редактируемая копия — читающие callable (UX-23).
 *
 * Локальной половины нет ни у одной из операций (AD-25): приёмник 4.21 создаёт запись из тела,
 * очередь ничего в таблицах фичи не меняет.
 */
interface LessonProposalRepository {
    suspend fun queueErrorReportProposal(proposal: ErrorReportProposal): Result<Unit>

    suspend fun queueNewLessonProposal(proposal: NewLessonProposal): Result<Unit>

    suspend fun queueCopyEditProposal(proposal: CopyEditProposal): Result<Unit>

    suspend fun fetchLessonEditCopy(lessonId: String): Result<LessonEditCopy>

    suspend fun quoteProposalFee(
        kind: String,
        changedQuestions: Int,
    ): Result<LessonProposalFeeQuote>
}
