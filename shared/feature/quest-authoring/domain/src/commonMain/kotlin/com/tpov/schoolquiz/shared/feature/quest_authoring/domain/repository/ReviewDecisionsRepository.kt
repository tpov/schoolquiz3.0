package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionCursor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionPage

/**
 * «Решения» админов (история 4.17, UX-22): чтение по-вопросных вердиктов модели и отметки ✓/✗.
 *
 * Право решает сервер (requireVerifier): отказ доступа — не сбой, а ответ «этот пользователь —
 * не админ», и вызывающий показывает авторскую сторону вкладки (только «Предложения»).
 */
interface ReviewDecisionsRepository {
    /** Страница строк по фильтру; курсор продолжает выборку без потерь и повторов. */
    suspend fun fetch(
        filter: ReviewDecisionFilter,
        cursor: ReviewDecisionCursor?,
    ): Result<ReviewDecisionPage>

    /** Отметка админа на вопросе вердикта; повторная отметка перезаписывает прежнюю. */
    suspend fun mark(
        verdictId: String,
        questionId: String,
        mark: ReviewDecisionMarkValue,
        comment: String,
    ): Result<ReviewDecisionMark>
}
