package com.tpov.schoolquiz.domain.repository

import androidx.lifecycle.LiveData
import com.tpov.schoolquiz.data.database.entities.QuizEntity

interface RepositoryQuiz {
    fun getQuiz(id: Int): QuizEntity

    fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int): Int

    suspend fun getQuizListLiveData(id: Int): LiveData<List<QuizEntity>>

    fun getQuizListLiveData(): LiveData<List<QuizEntity>>

    suspend fun getQuizList(): List<QuizEntity>

    suspend fun getQuizList(tpovId: Int): List<QuizEntity>

    fun updateQuiz(quiz: QuizEntity)

    fun insertQuiz(quiz: QuizEntity)

    fun deleteQuiz(id: Int)

    fun deleteQuiz()


    suspend fun downloadQuizHome()

    suspend fun unloadQuiz()

    suspend fun downloadQuizes()

}