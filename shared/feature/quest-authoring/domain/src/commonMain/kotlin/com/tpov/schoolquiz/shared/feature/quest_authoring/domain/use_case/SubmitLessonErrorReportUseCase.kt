package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonErrorReportSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitLessonErrorReportCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.InboxRepository
import kotlinx.coroutines.CancellationException

/**
 * Приватный отчёт об ошибке урока уходит в очередь, а не в callable (AD-3): текст про весь урок
 * и его id уже лежат в намерении, и ждать сети незачем. Идентификатор намерения назначается
 * здесь, в момент нажатия, и больше не меняется (AD-2).
 */
class SubmitLessonErrorReportUseCase(
    private val repository: InboxRepository,
    private val idProvider: QuestAuthoringIdProvider,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) {
    suspend operator fun invoke(
        ownerUid: String,
        command: SubmitLessonErrorReportCommand,
    ): Result<Unit> {
        return try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            require(command.lessonId.isNotBlank()) { "lessonId must not be blank" }
            val text = command.text.trim()
            require(text.isNotEmpty()) { "report text must not be blank" }
            require(text.length <= 1000) { "report text must stay within the server bound" }
            repository.queueErrorReport(
                LessonErrorReportSubmission(
                    id = idProvider.nextId("lesson-error"),
                    ownerUid = ownerUid,
                    requestedAtMs = timestampProvider.nowMs(),
                    command = SubmitLessonErrorReportCommand(command.lessonId, text),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }
}
