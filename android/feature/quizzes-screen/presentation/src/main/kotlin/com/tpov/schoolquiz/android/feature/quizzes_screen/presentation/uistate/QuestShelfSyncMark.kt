package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestShelfSyncState

/**
 * Пометка на карточке квеста о переносе на полку, который ещё не подтверждён сервером.
 *
 * Полка локально не меняется (AD-25), поэтому карточка не может показать «квест уже на арене» —
 * она показывает, что перенос ждёт отправки или что сервер его отверг. Источник — ответ очереди по
 * ссылке на сущность (история 4.4), не таблица квестов.
 */
enum class QuestShelfSyncMark {
    /** Перенос лежит в очереди и уедет сам. */
    Pending,

    /** Сервер отверг перенос насовсем; запись больше не уедет (AD-28). */
    Quarantined,
}

fun QuestShelfSyncState.toShelfSyncMark(): QuestShelfSyncMark? =
    when (this) {
        QuestShelfSyncState.NONE -> null
        QuestShelfSyncState.PENDING -> QuestShelfSyncMark.Pending
        QuestShelfSyncState.QUARANTINED -> QuestShelfSyncMark.Quarantined
    }
