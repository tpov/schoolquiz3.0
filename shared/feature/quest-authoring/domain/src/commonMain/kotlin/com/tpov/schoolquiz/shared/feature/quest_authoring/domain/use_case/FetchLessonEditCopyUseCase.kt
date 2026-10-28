package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopy
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonProposalRepository
import kotlinx.coroutines.CancellationException

/**
 * Редактируемая копия чужого урока для «Предложить правку» (AD-33, история 4.23, UX-23).
 *
 * Копию собирает сервер из опубликованных вопросов: редактируемые строки — публичные половины
 * без ключа, запертые — как опубликованы. Копию своего урока сервер не отдаёт
 * (`PROPOSAL_TARGET_OWN`): править свой урок автор может и так.
 */
class FetchLessonEditCopyUseCase(
    private val repository: LessonProposalRepository,
) {
    suspend operator fun invoke(lessonId: String): Result<LessonEditCopy> {
        if (lessonId.isBlank() || lessonId.contains('/')) {
            return Result.failure(IllegalArgumentException("lessonId is required"))
        }
        return try {
            repository.fetchLessonEditCopy(lessonId.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            Result.failure(e)
        }
    }
}
