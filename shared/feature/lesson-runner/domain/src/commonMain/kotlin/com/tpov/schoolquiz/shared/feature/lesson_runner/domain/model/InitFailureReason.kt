package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

sealed interface InitFailureReason {
    data object EmptyPool : InitFailureReason

    data object NoValidQuestions : InitFailureReason

    /**
     * The questions that would have been played arrived without their answer key — the public half
     * that `functions/question-redaction.js` publishes — without a supported start capability.
     *
     * Raw HARD is supported. This temporary gate remains for ordinary raw EASY (the legacy
     * intake requires a keyed local result) and public payloads with unknown difficulty.
     * Ticket-backed EASY retains its key and hints; ticket admission is a separate start path.
     *
     * Distinct from [NoValidQuestions] on purpose. That one means the bytes are broken and nobody
     * can read them; this one means they were read perfectly well and the answer was deliberately
     * not in them. A single message for both is what made a whole redacted lesson indistinguishable
     * from a corrupt one.
     *
     * Also distinct from [EmptyPool], which says "there is nothing here for this difficulty"
     * without saying why. Reported instead of it only when the redacted questions are plausibly
     * *why* — see the difficulty scoping in `StartLessonAttemptUseCase`.
     */
    data object RedactedNotSupported : InitFailureReason

    data object LessonNotFound : InitFailureReason

    data object AuthRequired : InitFailureReason
}
