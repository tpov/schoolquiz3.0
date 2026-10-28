package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository

/**
 * Цитата платы предложения до отправки (UX-23, история 4.22).
 *
 * Отказ цитаты — не отказ подачи: вход показывает «—», а плату приёмка 4.21 считает сама из тела
 * реальной подачи. Для COPY_EDIT изменённые вопросы обязательны (цитата без них не существует).
 */
class QuoteLessonProposalFeeUseCase(
    private val repository: LessonProposalRepository,
) {
    suspend operator fun invoke(
        kind: String,
        changedQuestions: Int = 0,
    ): Result<LessonProposalFeeQuote> {
        if (changedQuestions < 0) {
            return Result.failure(IllegalArgumentException("changedQuestions must not be negative"))
        }
        return repository.quoteProposalFee(kind, changedQuestions)
    }
}
