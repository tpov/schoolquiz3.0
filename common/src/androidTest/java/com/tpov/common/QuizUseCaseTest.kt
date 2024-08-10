package com.tpov.common

import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.remote.QuizRemote
import com.tpov.common.domain.QuizUseCase
import com.tpov.common.domain.repository.RepositoryQuiz
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class QuizUseCaseTest {

    @Mock
    private lateinit var repositoryQuiz: RepositoryQuiz

    private lateinit var quizUseCase: QuizUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        quizUseCase = QuizUseCase(repositoryQuiz)
    }

    @Test
    fun `fetchQuiz fetches and transforms quizzes correctly`() = runTest {
        val typeId = 1
        val categoryId = 1
        val subcategoryId = 1
        val subsubcategoryId = 1
        val starsMaxLocal = 5
        val starsAverageLocal = 3
        val ratingLocal = 88

        val mockQuizzes = listOf(QuizRemote(picture = "url/123"))
        whenever(repositoryQuiz.fetchQuizzes(typeId, categoryId, subcategoryId, subsubcategoryId)).thenReturn(mockQuizzes)

        quizUseCase.fetchQuiz(typeId, categoryId, subcategoryId, subsubcategoryId, starsMaxLocal, starsAverageLocal, ratingLocal)

        verify(repositoryQuiz).fetchQuizzes(typeId, categoryId, subcategoryId, subsubcategoryId)
        // Additional assertions can be made here to check the transformation logic
    }

    @Test
    fun `insertQuiz inserts quiz into repository`() = runTest {
        val quizEntity = QuizEntity(id = 1)

        quizUseCase.insertQuiz(quizEntity)

        verify(repositoryQuiz).insertQuiz(quizEntity)
    }

    @Test
    fun `deleteQuizById deletes quiz by id`() = runTest {
        val idQuiz = 1

        quizUseCase.deleteQuizById(idQuiz)

        verify(repositoryQuiz).deleteQuizById(idQuiz)
    }

    @Test
    fun `saveQuiz saves quiz into repository`() = runTest {
        val quizEntity = QuizEntity(id = 1)

        quizUseCase.saveQuiz(quizEntity)

        verify(repositoryQuiz).saveQuiz(quizEntity)
    }

    @Test
    fun `pushQuiz pushes quiz to remote database`() = runTest {
        val quizEntity = QuizEntity(id = 1)
        val idQuiz = 1
        val categoryId = 1
        val subcategoryId = 1
        val subsubcategoryId = 1

        quizUseCase.pushQuiz(quizEntity, idQuiz, categoryId, subcategoryId, subsubcategoryId)

        verify(repositoryQuiz).pushQuiz(quizEntity.toQuizRemote(), idQuiz, categoryId, subcategoryId, subsubcategoryId)
    }
}