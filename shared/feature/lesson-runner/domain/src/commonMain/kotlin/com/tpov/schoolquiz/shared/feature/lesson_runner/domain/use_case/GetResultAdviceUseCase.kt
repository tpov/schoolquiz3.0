package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ResultAdvice
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.resultAdvice
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository

/**
 * What to study after an attempt, looked up here rather than on the screen.
 *
 * The recommendation needs the lesson's neighbours, and reaching for those from the presentation
 * layer would drag the quest catalogue's types across a module boundary that deliberately does not
 * carry them. The screen asks a question and gets an answer with no ids in it.
 */
class GetResultAdviceUseCase(
    private val lessonRepository: LessonRepository,
) {
    suspend operator fun invoke(attempt: Attempt): ResultAdvice? =
        resultAdvice(attempt.codeAnswer, lessonRepository.titlesTaughtBefore(attempt.lessonId))
}
