package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

sealed interface InitFailureReason {
    data object EmptyPool : InitFailureReason
    data object NoValidQuestions : InitFailureReason
    data object LessonNotFound : InitFailureReason
    data object AuthRequired : InitFailureReason
}
