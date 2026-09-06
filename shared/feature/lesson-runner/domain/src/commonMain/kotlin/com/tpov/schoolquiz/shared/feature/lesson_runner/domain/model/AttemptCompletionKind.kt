package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/**
 * Explicit terminal intent, frozen in the same outbox transaction as the attempt.
 *
 * Old database rows and queued payloads have no such value. Absence remains unknown; it must
 * never be inferred from codeAnswer, percentScore, elapsed time or the historical completedAt name.
 */
enum class AttemptCompletionKind {
    COMPLETED,
    ABORTED,
}
