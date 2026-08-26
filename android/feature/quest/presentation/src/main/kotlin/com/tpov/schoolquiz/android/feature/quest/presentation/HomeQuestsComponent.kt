package com.tpov.schoolquiz.android.feature.quest.presentation

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import kotlinx.coroutines.flow.StateFlow

/**
 * Presentation contract for the "Квесты" (home) screen.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.2
 * ADR-CMP-51: Decompose Component pattern.
 */
interface HomeQuestsComponent {
    val state: StateFlow<HomeQuestsUiState>

    fun onCatalogClick(
        id: CatalogId,
        name: String,
    )

    /** Opens the runner for the lesson the continue card points at. */
    fun onContinueClick()

    fun onGiftBoxFabClick() = Unit

    fun onGiftBoxDismiss() = Unit
}

/**
 * UI state snapshot for the home (catalog grid) screen.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.2 HomeQuestsUiState
 */
data class HomeQuestsUiState(
    val catalogs: List<CatalogDisplayItem> = emptyList(),
    val isLoading: Boolean = false,
    val giftBoxCount: Int = 0,
    val giftBoxStreakDays: Int = 0,
    val giftBoxOpening: HomeGiftBoxOpeningState? = null,
    /** The lesson the player was last mid-way through, or null when there is nothing to resume. */
    val continueLesson: ContinueLessonUi? = null,
)

/** What the continue card needs to draw: the lesson and the section it belongs to. */
data class ContinueLessonUi(
    val lessonId: LessonId,
    val title: String,
    /** Breadcrumb path — quest › section › theme — the way the quizzes screens name the way down. */
    val path: String,
    /** One segment per lesson of the section, in teaching order. */
    val lessonSegments: List<LessonSegmentUi>,
)

/** A single slot on the continue card's strip: one lesson of the section. */
data class LessonSegmentUi(
    val lessonId: LessonId,
    val title: String,
    val completed: Boolean,
    val isCurrent: Boolean,
)

sealed interface HomeGiftBoxOpeningState {
    data class Opening(
        val startedBoxCount: Int,
    ) : HomeGiftBoxOpeningState

    data class Opened(
        val reward: GiftBoxReward,
        val remainingBoxCount: Int,
        val profileSynced: Boolean,
    ) : HomeGiftBoxOpeningState

    data class Failed(
        val reason: HomeGiftBoxFailure,
        val remainingBoxCount: Int,
    ) : HomeGiftBoxOpeningState
}

/**
 * Neutral failure data for the gift box overlay; the screen resolves it to localized copy.
 */
sealed interface HomeGiftBoxFailure {
    data object NoBoxes : HomeGiftBoxFailure

    /** Unexpected backend failure; [detail] is raw technical data, not curated copy. */
    data class Unexpected(
        val detail: String?,
    ) : HomeGiftBoxFailure
}
