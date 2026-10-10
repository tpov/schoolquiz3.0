package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestQuestionEditorUiState

/**
 * Undo/redo одной сессии редактирования вопроса (UX-O6 §4, UX-18, AD-27).
 *
 * Единица истории — целый снимок состояния редактора, а не диф текста: текст и его метки
 * (пробелы, защита перевода, связки с ответами) живут в одном значении и восстанавливаются
 * вместе, «правка не сдвигает blank к другому ответу» держится построением. Стек очищается
 * при установке нового вопроса в редактор: история — про сессию правки одного вопроса,
 * а не про весь черновик.
 *
 * Служебные переходы (сохранение, ошибки, превью) в историю не попадают: их заводит
 * компонент, и только содержательные правки автора проходят через [push].
 */
internal class QuestionEditorHistory(
    private val limit: Int = DEFAULT_LIMIT,
) {
    private val undoStack = ArrayDeque<QuestQuestionEditorUiState>()
    private val redoStack = ArrayDeque<QuestQuestionEditorUiState>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    /** Запомнить состояние ПЕРЕД правкой. Новая правка обрывает ветку redo. */
    fun push(state: QuestQuestionEditorUiState) {
        undoStack.addLast(state)
        while (undoStack.size > limit) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    /** Вернуть состояние до последней правки; [current] уходит в стек redo. */
    fun undo(current: QuestQuestionEditorUiState): QuestQuestionEditorUiState? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return previous
    }

    /** Вернуть состояние, отменённое последним undo; [current] возвращается в стек undo. */
    fun redo(current: QuestQuestionEditorUiState): QuestQuestionEditorUiState? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }

    /** Новая сессия редактирования: прежняя история чужому вопросу не достаётся. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /** Проставить актуальные флаги в снимок, который уходит на экран. */
    fun attach(state: QuestQuestionEditorUiState): QuestQuestionEditorUiState =
        state.copy(canUndo = canUndo, canRedo = canRedo)

    companion object {
        internal const val DEFAULT_LIMIT: Int = 100
    }
}
