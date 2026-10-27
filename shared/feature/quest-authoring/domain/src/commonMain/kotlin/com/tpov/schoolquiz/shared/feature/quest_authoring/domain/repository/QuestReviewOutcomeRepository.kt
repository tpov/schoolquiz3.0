package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestReviewOutcomeSnapshot

/**
 * Читающая проекция исходов модельной проверки (AD-20): владелец выводит сервер, идентификаторы
 * — только адрес запроса. Отказ — не крах экрана: автор остаётся со списком черновиков.
 */
interface QuestReviewOutcomeRepository {
    /** Последний исход на каждый названный черновик/заявку; ограниченный запрос, не скан истории. */
    suspend fun fetch(
        submissionIds: Set<String>,
        legacyDraftIds: Set<String>,
    ): Result<List<QuestReviewOutcomeSnapshot>>

    /** Довести заявку с готовым исходом до авторского статуса (публикация/отказ, 4.16). */
    suspend fun advance(submissionId: String): Result<Unit>
}
