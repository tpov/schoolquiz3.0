package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

/** Frame-owned callbacks. Arena supplies a captured revision, ordinary runner retains its existing callbacks. */
internal class QuestionInteraction(
    val readDraft: () -> UserAnswerDraft?,
    val changeDraft: (UserAnswerDraft) -> Unit,
    val selectAnswer: (UserAnswerDraft) -> Unit,
    val hint: HintControl,
    val feedback: (AnswerFeedback) -> Unit,
)
