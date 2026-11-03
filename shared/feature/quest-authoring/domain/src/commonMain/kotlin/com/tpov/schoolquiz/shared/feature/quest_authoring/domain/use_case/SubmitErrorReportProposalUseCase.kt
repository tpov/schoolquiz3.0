package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitErrorReportProposalCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository
import kotlinx.coroutines.CancellationException

/**
 * «Сообщить об ошибке» как предложение автору (CAP-6/27, история 4.22).
 *
 * Отчёт уходит в общую очередь (AD-3/6): текст про весь урок и его id уже лежат в намерении,
 * сети ждать незачем. Идентификатор намерения назначается здесь, в момент нажатия (AD-2).
 * Границы повторяют парсер приёмника 4.21: отказ до постановки в очередь — запись в outbox
 * не появляется вовсе.
 */
class SubmitErrorReportProposalUseCase(
    private val repository: LessonProposalRepository,
    private val idProvider: QuestAuthoringIdProvider,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) {
    suspend operator fun invoke(
        ownerUid: String,
        command: SubmitErrorReportProposalCommand,
    ): Result<Unit> {
        return try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            require(command.lessonId.isNotBlank()) { "lessonId must not be blank" }
            val text = command.text.trim()
            require(text.isNotEmpty()) { "report text must not be blank" }
            require(text.length <= MAX_REPORT_TEXT_CHARS) {
                "report text must stay within the server bound ($MAX_REPORT_TEXT_CHARS)"
            }
            val ownVariant = command.ownVariant?.trim().takeUnless { it.isNullOrEmpty() }
            require(ownVariant == null || ownVariant.length <= MAX_REPORT_TEXT_CHARS) {
                "own variant must stay within the server bound ($MAX_REPORT_TEXT_CHARS)"
            }
            repository.queueErrorReportProposal(
                ErrorReportProposal(
                    id = idProvider.nextId("lesson-proposal"),
                    ownerUid = ownerUid,
                    requestedAtMs = timestampProvider.nowMs(),
                    command = SubmitErrorReportProposalCommand(command.lessonId, text, ownVariant),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    companion object {
        /** Граница парсера приёмника 4.21 (MAX_REPORT_TEXT_CHARS в lesson-proposals.js). */
        const val MAX_REPORT_TEXT_CHARS = 2000
    }
}
