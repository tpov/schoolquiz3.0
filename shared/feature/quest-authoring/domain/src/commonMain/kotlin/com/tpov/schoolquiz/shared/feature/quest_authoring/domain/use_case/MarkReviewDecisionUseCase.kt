package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.ReviewDecisionsRepository

/**
 * Отметка админа на вопросе вердикта (история 4.17, UX-22): ✓/✗ с необязательным комментарием.
 *
 * Сценарий — обёртка: валидацию пары (вердикт, вопрос) и право делает сервер; экранное ожидание
 * «Отправляется…» гасится возвращённой сохранённой отметкой.
 */
class MarkReviewDecisionUseCase(
    private val repository: ReviewDecisionsRepository,
) {
    suspend operator fun invoke(
        verdictId: String,
        questionId: String,
        mark: ReviewDecisionMarkValue,
        comment: String,
    ): Result<ReviewDecisionMark> = repository.mark(verdictId, questionId, mark, comment)
}
