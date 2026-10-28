package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state

import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic.EditCopyMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopyRow

/** Состояние подачи правки копии (история 4.23): по образцу отчёта 4.22. */
enum class CopyEditSubmissionState {
    Idle,
    Sending,
    Done,
    Failed,
}

/** Состояние загрузки копии: callable собирает её на сервере, экран ждёт. */
enum class CopyEditLoadState {
    Idle,
    Loading,
    Loaded,
    Failed,
}

/** Строка копии на экране редактора: контент публичной половины и отметка предложившего. */
data class CopyEditRowState(
    val row: LessonEditCopyRow,
    /** Разобранная публичная половина; null — payload не разобрался, строка показывается запертой. */
    val parsed: RedactedQuestionContent?,
    val mark: EditCopyMark?,
) {
    /** Отметка валидна и может уйти в предложении (у множественного выбора их минимум два). */
    val marked: Boolean
        get() = when (val mark = mark) {
            null -> false
            is EditCopyMark.MultipleChoice -> mark.correctOptionIds.size >= 2
            is EditCopyMark.Ordering -> mark.orderedItemIds.size >= 2
            is EditCopyMark.FillBlank -> rowParsedBlanks().isNotEmpty() &&
                mark.correctCandidateByBlank.size == rowParsedBlanks().size
            is EditCopyMark.SingleChoice -> mark.correctOptionId.isNotBlank()
        }

    private fun rowParsedBlanks(): List<String> =
        (parsed as? RedactedQuestionContent.FillBlank)?.blanks ?: emptyList()
}

/** Состояние шторки «Предложить правку»: загрузка копии, строки с отметками, подача. */
data class CopyEditSheetState(
    val load: CopyEditLoadState = CopyEditLoadState.Idle,
    val rows: List<CopyEditRowState> = emptyList(),
    val submission: CopyEditSubmissionState = CopyEditSubmissionState.Idle,
) {
    /** Изменённые вопросы — отмеченные строки; плата 100×N и editedQuestionIds считаются из них. */
    val editedRows: List<CopyEditRowState>
        get() = rows.filter { it.marked }

    val editedCount: Int
        get() = editedRows.size
}
