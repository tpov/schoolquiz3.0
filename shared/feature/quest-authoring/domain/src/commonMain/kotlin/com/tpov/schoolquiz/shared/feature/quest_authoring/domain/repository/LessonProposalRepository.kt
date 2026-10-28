package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote

/**
 * Предложения автору (AD-33, история 4.22): подача отчёта об ошибке — общая очередь (AD-6),
 * цитата платы — читающий callable (UX-23).
 *
 * Локальной половины нет ни у одной из операций (AD-25): приёмник 4.21 создаёт запись из тела,
 * очередь ничего в таблицах фичи не меняет.
 */
interface LessonProposalRepository {
    suspend fun queueErrorReportProposal(proposal: ErrorReportProposal): Result<Unit>

    suspend fun quoteProposalFee(
        kind: String,
        changedQuestions: Int,
    ): Result<LessonProposalFeeQuote>
}
