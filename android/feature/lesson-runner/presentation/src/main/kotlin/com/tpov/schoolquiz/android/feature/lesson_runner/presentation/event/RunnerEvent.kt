package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event

enum class SaveError { IoError, Unknown }

sealed interface RunnerEvent {
    data class SaveAttemptFailed(val error: SaveError) : RunnerEvent

    data object SaveRatingFailed : RunnerEvent

    data object NavigateBack : RunnerEvent

    /** Host should open the runner for the next lesson of the same theme (design decision F3). */
    data class OpenNextLesson(val lessonId: String) : RunnerEvent
}
