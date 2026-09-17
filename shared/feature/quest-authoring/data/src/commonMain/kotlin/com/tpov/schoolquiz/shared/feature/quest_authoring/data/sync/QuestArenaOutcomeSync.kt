package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import kotlinx.coroutines.CancellationException

/**
 * Вторая половина круга: вердикт рецензентов возвращается автору.
 *
 * Первую половину — отправку самой заявки — делает движок очереди: заявка уезжает единственным
 * приёмником (AD-6), и прежний `QuestArenaSubmissionSync` вместе с прямой записью в Firestore
 * снят. Вердикт же движок принести не может: это не отправка отложенного действия, а чтение
 * серверного состояния, и без него автор не узнаёт ни об отказе, ни о его причине.
 */
class QuestArenaOutcomeSync(
    private val local: QuestAuthoringLocalDataSource,
    private val remote: QuestArenaSubmissionRemoteDataSource,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) : Syncable {
    override suspend fun sync(): Result<Unit> =
        try {
            // Владелец берётся из самих черновиков, а не из авторизации: этот модуль не должен
            // зависеть от app-shell, а черновик, ждущий рецензии, уже знает, чей он.
            for (ownerUid in local.findOwnerUidsAwaitingReview()) {
                for (outcome in remote.fetchOutcomes(ownerUid)) {
                    // Оба исхода терминальны, и оба выводят черновик из ожидания. Пропускать
                    // публикацию значило бы запереть удачную заявку в REVIEW_QUEUED навсегда:
                    // движок не сообщает фиче «уехало», а больше об этом узнать неоткуда.
                    when {
                        outcome.isRejected ->
                            local.applyRejection(
                                draftId = outcome.draftId,
                                reason = outcome.rejectionReason,
                                updatedAtMs = timestampProvider.nowMs(),
                            )

                        outcome.isPublished ->
                            local.applyPublication(
                                draftId = outcome.draftId,
                                updatedAtMs = timestampProvider.nowMs(),
                            )

                        else -> Unit // ещё на проверке — ждём
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
