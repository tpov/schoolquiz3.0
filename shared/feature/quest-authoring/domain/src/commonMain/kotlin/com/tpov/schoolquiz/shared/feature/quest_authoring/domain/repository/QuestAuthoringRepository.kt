package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftConflict
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import kotlinx.coroutines.flow.Flow

/**
 * Хранилище авторской работы над квестом.
 *
 * Подавление порога числа методов осознанное: двенадцатым стал [activateDraft], без которого
 * выбор черновика в списке не доходил до редактора. Дробить интерфейс ради счётчика значило бы
 * развести по разным местам операции над одной и той же сущностью.
 */
@Suppress("TooManyFunctions")
interface QuestAuthoringRepository {
    fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>>

    fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?>

    suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle?

    suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle?

    /**
     * Делает выбранный черновик активным.
     *
     * Экран создания открывает активный черновик, поэтому выбор в списке «Мои квесты» — это
     * именно смена активного, а не передача идентификатора через навигацию.
     */
    suspend fun activateDraft(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Result<Unit>

    suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit>

    suspend fun upsertQuestion(question: DraftQuestion): Result<Unit>

    suspend fun queueArenaSubmission(submission: QuestArenaSubmission): Result<Unit> =
        Result.failure(UnsupportedOperationException("Arena submission queue is not implemented"))

    /**
     * Расходится ли черновик с сервером прямо сейчас (AD-24).
     *
     * Ответ собирается по очереди, а не по локальному статусу черновика: число, поверх которого
     * автор будет отправлять заново, знает только запись, которую сервер отверг.
     */
    fun observeDraftConflict(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Flow<QuestDraftConflict?>

    /** Принимает серверную версию и ставит новую заявку вместо отвергнутой (AD-24, AD-2). */
    suspend fun resolveDraftConflict(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Result<Unit>

    suspend fun setDraftStatus(
        draftId: QuestDraftId,
        status: QuestDraftStatus,
        updatedAtMs: Long,
    ): Result<Unit>
}
