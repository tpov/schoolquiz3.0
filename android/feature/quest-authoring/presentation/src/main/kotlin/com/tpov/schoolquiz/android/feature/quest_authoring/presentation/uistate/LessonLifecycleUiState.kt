package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecycleEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltySummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision

/**
 * Экран держит доменные проекции TZ06B как есть — это значения, а не поведение; форматирование
 * и подписи решает Compose. Отказ кураторского перехода — часть состояния, а не ошибка экрана:
 * куратор должен увидеть код отказа сервера.
 */
data class CuratorUiState(
    val isLoading: Boolean = true,
    val errorMessage: UiMessage? = null,
    val permissionDenied: Boolean = false,
    val entries: List<LessonLifecycleEntry> = emptyList(),
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val selectedLessonId: String? = null,
    val history: List<LessonHistoryEntry> = emptyList(),
    val historyIsLoading: Boolean = false,
    val reasonDraft: String = "",
    val isTransitioning: Boolean = false,
    val lastDecision: LessonTransitionDecision? = null,
    /** Урок, чьё меню переходов открыто long press (AD-21); null — меню закрыто. */
    val menuLessonId: String? = null,
    /** Назначенный из меню переход, ждущий подтверждения с причиной. */
    val pendingConfirmation: TransitionConfirmation? = null,
)

/**
 * Назначенный переход под подтверждением: урок и цель зафиксированы меню, причина — черновик
 * состояния. Решение уходит серверу только «Подтвердить» (AD-21).
 */
data class TransitionConfirmation(
    val lessonId: String,
    val target: LessonShelf,
)

data class AuthorLifecycleUiState(
    val isLoading: Boolean = true,
    val errorMessage: UiMessage? = null,
    val entries: List<LessonLifecycleEntry> = emptyList(),
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val royalty: LessonRoyaltySummary? = null,
    val selectedLessonId: String? = null,
    val history: List<LessonHistoryEntry> = emptyList(),
    val historyIsLoading: Boolean = false,
)
