package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

sealed interface InitFailureReason {
    data object EmptyPool : InitFailureReason
    data object NoValidQuestions : InitFailureReason

    /**
     * The questions that would have been played arrived without their answer key — the public half
     * that `functions/question-redaction.js` publishes — and this client cannot yet play one.
     *
     * **Temporary, and named so.** `RedactedNotSupported` says "the runner does not support these
     * yet", not "this lesson is redacted": step 9 of the E2 plan admits redacted questions into the
     * hard pool and deletes this case along with the branch in `StartLessonAttemptUseCase` that
     * returns it. Whoever does that should expect to remove this declaration, its two presentation
     * mirrors and its string in all three locales, not to repurpose them.
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
