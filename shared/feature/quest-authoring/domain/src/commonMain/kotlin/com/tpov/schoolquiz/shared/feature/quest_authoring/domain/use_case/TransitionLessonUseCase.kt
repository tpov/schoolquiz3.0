package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.LessonLifecycleRepository

/**
 * Кураторский переход урока по полкам (callable `setLessonPlacement`).
 *
 * Ключ мутации приходит снаружи и не меняется между попытками одного намерения (AD-2): повтор
 * уехавшего в unknown перехода обязан отвечать той же квитанцией, а не плодить второе решение.
 * Причина обязательна — переход без названной причины сервер не примет, клиент проверяет это
 * раньше транспорта.
 */
class TransitionLessonUseCase(
    private val repository: LessonLifecycleRepository,
) {
    suspend operator fun invoke(
        ownerUid: String,
        command: LessonTransitionCommand,
        mutationId: String,
    ): Result<LessonTransitionDecision> {
        require(command.reason.isNotBlank()) { "Transition requires a named reason" }
        require(command.reason.encodeToByteArray().size <= MAX_REASON_BYTES) {
            "Transition reason exceeds $MAX_REASON_BYTES bytes"
        }
        require(mutationId.isNotBlank()) { "Transition requires a mutation key" }
        return repository.transition(ownerUid, command, mutationId)
    }

    companion object {
        /** Одна цифра с серверным потолком авторных причин (views, MAX_AUTHOR_REJECTION_REASON_BYTES). */
        const val MAX_REASON_BYTES = 1000
    }
}
