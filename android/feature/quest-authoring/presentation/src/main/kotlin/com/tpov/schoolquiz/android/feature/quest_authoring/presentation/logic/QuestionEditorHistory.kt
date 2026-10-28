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
 *
 * «Одна правка — один шаг истории» (QuestCreateComponent): серия набора текста — один шаг,
 * а не нажатие (ретро 2026-10-25, L8). Текстовые правки подряд помечаются [push] с textual = true
 * и продолжаются [coalesceTextualEdit] — стек не растёт, но redo-ветку правка обрывает, как
 * всякая другая. Переключение защиты ответа — дискретное действие, в текстовую серию не сливается.
 */
internal class QuestionEditorHistory(
    private val limit: Int = DEFAULT_LIMIT,
) {
    private val undoStack = ArrayDeque<QuestQuestionEditorUiState>()
    private val redoStack = ArrayDeque<QuestQuestionEditorUiState>()

    /** Признак последнего шага: текстовая серия продолжается без нового снимка. */
    private var lastPushWasTextual = false

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    /** Текстовая серия открыта: очередную текстовую правку можно слить с последним шагом. */
    val canCoalesceTextual: Boolean
        get() = lastPushWasTextual

    /** Запомнить состояние ПЕРЕД правкой. Новая правка обрывает ветку redo. */
    fun push(
        state: QuestQuestionEditorUiState,
        textual: Boolean = false,
    ) {
        undoStack.addLast(state)
        while (undoStack.size > limit) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        lastPushWasTextual = textual
    }

    /**
     * Продолжение текстовой серии: снимок в историю не кладётся (серия — один шаг), но ветку redo
     * правка обрывает — отменённое дальше содержание против набранного текста не восстановится.
     */
    fun coalesceTextualEdit() {
        redoStack.clear()
    }

    /** Вернуть состояние до последней правки; [current] уходит в стек redo. */
    fun undo(current: QuestQuestionEditorUiState): QuestQuestionEditorUiState? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastPushWasTextual = false
        return previous
    }

    /** Вернуть состояние, отменённое последним undo; [current] возвращается в стек undo. */
    fun redo(current: QuestQuestionEditorUiState): QuestQuestionEditorUiState? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastPushWasTextual = false
        return next
    }

    /** Новая сессия редактирования: прежняя история чужому вопросу не достаётся. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastPushWasTextual = false
    }

    /** Проставить актуальные флаги в снимок, который уходит на экран. */
    fun attach(state: QuestQuestionEditorUiState): QuestQuestionEditorUiState =
        state.copy(canUndo = canUndo, canRedo = canRedo)

    companion object {
        internal const val DEFAULT_LIMIT: Int = 100
    }
}
