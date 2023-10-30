package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.repository.RepositoryQuiz
import javax.inject.Inject

class QuizUseCase @Inject constructor(private val repositoryQuiz: RepositoryQuiz) {
    fun getQuiz(id: Int) = repositoryQuiz.getQuiz(id)

    suspend fun getQuizListLiveData(tpovId: Int) = repositoryQuiz.getQuizListLiveData(tpovId)

    fun getQuizListLiveData() = repositoryQuiz.getQuizListLiveData()

    fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int) =
        repositoryQuiz.getIdQuizByNameQuiz(nameQuiz, tpovId)

    suspend fun getQuizList() = repositoryQuiz.getQuizList()

    suspend fun getQuizList(tpovId: Int) = repositoryQuiz.getQuizList(tpovId)

    fun updateQuiz(quiz: QuizEntity) = repositoryQuiz.updateQuiz(quiz)

    suspend fun downloadQuizHome() = repositoryQuiz.downloadQuizHome()

    suspend fun unloadQuizUseCase() = repositoryQuiz.unloadQuiz()

    fun insertQuiz(quiz: QuizEntity) {
        repositoryQuiz.insertQuiz(quiz)
    }

    suspend fun unloadQuiz() {
        repositoryQuiz.unloadQuiz()
    }

    fun deleteQuiz(id: Int) {
        repositoryQuiz.deleteQuiz(id)
    }

    fun deleteQuiz() {
        repositoryQuiz.deleteQuiz()
    }

    suspend fun downloadQuizes() {
        repositoryQuiz.downloadQuizes()
    }
}