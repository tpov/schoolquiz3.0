package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

enum class ReviewQueueFilter {
    ALL,
    TESTING,
    LOGIC,
    TRANSLATION,
    TRANSLATION_REVIEW,
}

enum class ReviewQueueKindUi {
    TESTING,
    LOGIC,
    TRANSLATION,
    TRANSLATION_REVIEW,
}

/**
 * Что очередь знает про решение по заданию (AD-14). Карантин — не откат, а пометка: локальной
 * половины у решения нет (AD-25), сервер ответил окончательно, и карточка говорит об этом словами.
 */
enum class ReviewAssignmentSyncUi {
    NONE,
    PENDING,
    QUARANTINED,
}

enum class ReviewSegmentLabelKind {
    TEXT,
    OPTION,
    ITEM,
    CANDIDATE,
    INFO,
}

data class ReviewLanguagesUi(
    val source: List<String> = emptyList(),
    val translationTargets: List<String> = emptyList(),
    val reviewTargets: List<String> = emptyList(),
)

data class ReviewQueueUiState(
    val isLoading: Boolean = true,
    val filterMenuExpanded: Boolean = false,
    val selectedFilter: ReviewQueueFilter = ReviewQueueFilter.ALL,
    val availableFilters: List<ReviewQueueFilter> = listOf(ReviewQueueFilter.ALL),
    val assignments: List<ReviewAssignmentListItemUiState> = emptyList(),
    val detail: ReviewAssignmentDetailUiState? = null,
    val errorMessage: UiMessage? = null,
    val successMessage: UiMessage? = null,
)

data class ReviewAssignmentListItemUiState(
    val id: String,
    val title: String,
    val kinds: List<ReviewQueueKindUi>,
    val languages: ReviewLanguagesUi,
    val questionCount: Int,
    val testingScore: String?,
    val logicScore: String?,
    val translationScore: String?,
    val syncState: ReviewAssignmentSyncUi = ReviewAssignmentSyncUi.NONE,
)

data class ReviewAssignmentDetailUiState(
    val assignmentId: String,
    val lessonId: String,
    val title: String,
    val kind: ReviewQueueKindUi,
    val selectedLanguage: String?,
    val availableLanguages: List<String>,
    val selectedScore: Int?,
    val questions: List<ReviewQuestionUiState>,
)

data class ReviewQuestionUiState(
    val id: String,
    val order: Int,
    val language: String,
    val text: String,
    val segments: List<ReviewSegmentUiState> = emptyList(),
)

data class ReviewSegmentUiState(
    val questionId: String,
    val key: String,
    val labelKind: ReviewSegmentLabelKind,
    val labelArg: String? = null,
    val sourceText: String,
    val translatedText: String,
    val accepted: Boolean = true,
)
