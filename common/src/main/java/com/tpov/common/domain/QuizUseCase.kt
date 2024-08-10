package com.tpov.common.domain

import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.domain.repository.RepositoryQuiz
import javax.inject.Inject

class QuizUseCase @Inject constructor(private val repositoryQuiz: RepositoryQuiz) {

    suspend fun fetchQuiz(
        typeId: Int,
        categoryId: Int,
        subcategoryId: Int,
        subsubcategoryId: Int,
        starsMaxLocal: Int,
        starsAverageLocal: Int,
        ratingLocal: Int
    ) = repositoryQuiz.fetchQuizzes(typeId, categoryId, subcategoryId, subsubcategoryId)
        .map { quiz ->
            quiz.toQuizEntity(
                getQuizIdByPathPicture(quiz.picture),
                categoryId,
                subcategoryId,
                subsubcategoryId,
                starsMaxLocal,
                starsAverageLocal,
                ratingLocal
            )
        }

    private fun getQuizIdByPathPicture(picture: String) =
        picture.substringAfterLast("/").toIntOrNull() ?: 0

    suspend fun insertQuiz(quizEntity: QuizEntity) {
        repositoryQuiz.insertQuiz(quizEntity)
    }

    suspend fun saveQuiz(quizEntity: QuizEntity) {
        repositoryQuiz.saveQuiz(quizEntity)
    }

    suspend fun pushQuiz(
        quizEntity: QuizEntity,
        idQuiz: Int,
        categoryId: Int,
        subcategoryId: Int,
        subsubcategoryId: Int
    ) {
        repositoryQuiz.pushQuiz(quizEntity.toQuizRemote(), idQuiz, categoryId, subcategoryId, subsubcategoryId)
    }

    suspend fun getQuizzes(): List<QuizEntity>? {
        return repositoryQuiz.getQuizzes()
    }
    suspend fun getQuizById(id: Int): QuizEntity? {
        return getQuizzes()?.filter { it.id == id }?.get(0)
    }

    suspend fun deleteQuizById(idQuiz: Int) {
        repositoryQuiz.deleteQuizById(idQuiz)
    }

    suspend fun deleteRemoteQuizById(quizEntity: QuizEntity, idQuiz: Int, categoryId: Int, subcategoryId: Int, subsubcategoryId: Int) {
        repositoryQuiz.deleteRemoteQuizById(quizEntity.toQuizRemote(), idQuiz, categoryId, subcategoryId, subsubcategoryId)
    }

}
