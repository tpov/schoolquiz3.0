package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

sealed interface SaveError {
    data class IoFailure(val throwable: Throwable) : SaveError
    data class UnknownError(val throwable: Throwable) : SaveError
}
