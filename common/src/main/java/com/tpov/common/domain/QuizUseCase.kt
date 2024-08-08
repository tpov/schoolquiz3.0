package com.tpov.common.domain

import com.tpov.common.domain.repository.RepositoryQuiz
import javax.inject.Inject

class QuizUseCase @Inject constructor(private val repositoryQuiz: RepositoryQuiz) {
fun fetchQuiz8() {
    repositoryQuiz.fetchQuizzes()
}