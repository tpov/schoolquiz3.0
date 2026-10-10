package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecyclePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltyPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision

/**
 * Клиентская половина TZ06B: пять читающих view-коллблов и кураторский переход.
 *
 * Чтение синхронное и без локальной половины — это проекции сервера, а не состояние приложения
 * (AD-25): кэш проекции разъехался бы с серверной историей, а единственный честный источник —
 * сам view. Отказ сервера — [Result.failure] с [com.tpov.schoolquiz.shared.core.network.SyncFailure];
 * отказ перехода внутри квитанции — НЕ failure, а [LessonTransitionDecision.Rejected].
 */
interface LessonLifecycleRepository {
    /** Размещения уроков автора (view `listOwnedLessonLifecycle`). */
    suspend fun listOwned(
        ownerUid: String,
        afterLessonId: String?,
        pageSize: Int,
    ): Result<LessonLifecyclePage>

    /** Все размещения для куратора (view `listCuratorLessonLifecycle`); не-куратору — отказ. */
    suspend fun listCurator(
        ownerUid: String,
        afterLessonId: String?,
        pageSize: Int,
    ): Result<LessonLifecyclePage>

    /** История переходов одного урока (view `listLessonLifecycleHistory`). */
    suspend fun listHistory(
        ownerUid: String,
        lessonId: String,
        cursor: String?,
        pageSize: Int,
    ): Result<LessonHistoryPage>

    /** Авторские роялти арены (view `listMyArenaRoyalty`). */
    suspend fun listRoyalty(
        ownerUid: String,
        cursor: String?,
        pageSize: Int,
    ): Result<LessonRoyaltyPage>

    /**
     * Кураторский переход (callable `setLessonPlacement`). [mutationId] рождается с намерением
     * куратора и переживает повтор попытки (AD-2); новый замысел после окончательного отказа —
     * новое намерение с новым ключом.
     */
    suspend fun transition(
        ownerUid: String,
        command: LessonTransitionCommand,
        mutationId: String,
    ): Result<LessonTransitionDecision>
}
