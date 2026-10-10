package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.CuratorUiState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import kotlinx.coroutines.flow.StateFlow

/**
 * Поверхность куратора: размещения всех уроков (view `listCuratorLessonLifecycle`), история
 * решений выбранного урока и переход по полкам (`setLessonPlacement`).
 *
 * Переход открывает только меню по long press карточки урока (или равнозначное semantic action —
 * AD-21): короткий тап открывает урок (статус и историю решений), пункт меню назначает цель, и
 * только подтверждение в диалоге с причиной отправляет решение серверу. Разрешения и реальный
 * исход проверяет сервер (TZ06B): не-куратор видит отказ чтения как [CuratorUiState.permissionDenied].
 */
interface CuratorComponent {
    val state: StateFlow<CuratorUiState>

    fun onRefreshClick()

    fun onLoadMoreClick()

    /** Короткий тап: открыть урок — статус, флаги и историю его решений. */
    fun onLessonSelected(lessonId: String)

    /** Long press карточки (и равнозначный semantic action): открыть меню переходов урока. */
    fun onLessonMenuRequested(lessonId: String)

    fun onMenuDismissed()

    /** Пункт меню: назначить цель перехода и открыть подтверждение с причиной. */
    fun onTransitionRequested(target: LessonShelf)

    fun onConfirmationDismissed()

    fun onReasonChanged(value: String)

    /** «Подтвердить» в диалоге: отправить назначенный переход под ключом намерения (AD-2). */
    fun onTransitionClick(target: LessonShelf)
}
