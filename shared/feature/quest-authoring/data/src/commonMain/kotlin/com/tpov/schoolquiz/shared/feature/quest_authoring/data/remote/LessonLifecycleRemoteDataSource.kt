package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecyclePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltyPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision

/**
 * Транспортная половина TZ06B: пять читающих view-коллблов и `setLessonPlacement`.
 *
 * Владелец сессии выводится сервером из Firebase Auth — в полезной нагрузке его нет. Ответ
 * приводится к доменным проекциям строгим парсером; расхождение конверта — отказ чтения.
 */
interface LessonLifecycleRemoteDataSource {
    suspend fun listOwned(
        afterLessonId: String?,
        pageSize: Int,
    ): LessonLifecyclePage

    suspend fun listCurator(
        afterLessonId: String?,
        pageSize: Int,
    ): LessonLifecyclePage

    suspend fun listHistory(
        lessonId: String,
        cursor: String?,
        pageSize: Int,
    ): LessonHistoryPage

    suspend fun listRoyalty(
        cursor: String?,
        pageSize: Int,
    ): LessonRoyaltyPage

    suspend fun transition(
        command: LessonTransitionCommand,
        mutationId: String,
    ): LessonTransitionDecision
}
