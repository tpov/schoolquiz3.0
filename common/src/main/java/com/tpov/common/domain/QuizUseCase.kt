package com.tpov.common.domain

import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
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
        ratingLocal: Int,
        idQuiz: Int
    ): QuizEntity {
        val quizRemote = repositoryQuiz.fetchQuizzes(typeId, categoryId, subcategoryId, subsubcategoryId, idQuiz)

        return quizRemote.toQuizEntity(
            getQuizIdByPathPicture(quizRemote.picture),
            categoryId,
            subcategoryId,
            subsubcategoryId,
            starsMaxLocal,
            starsAverageLocal,
            ratingLocal
        )
    }

    suspend fun pushQuiz(quizEntity: QuizEntity) {
        repositoryQuiz.pushQuiz(quizEntity.toQuizRemote(), quizEntity.id ?:0)
    }

    private fun getQuizIdByPathPicture(picture: String) =
        picture.substringAfterLast("/").toIntOrNull() ?: 0

    suspend fun insertQuiz(quizEntity: QuizEntity) {
        repositoryQuiz.insertQuiz(quizEntity)
    }

    suspend fun saveQuiz(quizEntity: QuizEntity) {
        repositoryQuiz.saveQuiz(quizEntity)
    }

    suspend fun pushStructureCategory(
        structureCategoryDataEntity: StructureCategoryDataEntity
    ) = repositoryQuiz.pushStructureCategory(structureCategoryDataEntity)

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
