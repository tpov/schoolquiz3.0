package com.tpov.schoolquiz.domain.repository

import androidx.lifecycle.LiveData
import com.tpov.schoolquiz.data.database.entities.*
import kotlinx.coroutines.flow.Flow

// TODO: 25.07.2022 LiveData -> Flow
interface RepositoryDB {
    fun insertQuizDetail(questionDetailEntity: QuestionDetailEntity)

    fun insertProfile(profile: ProfileEntity)

    fun insertQuiz(quizEntity: QuizEntity)

    fun insertQuestion(questionEntity: QuestionEntity)

    fun insertListApiQuestion(apiQuestion: List<ApiQuestion>)


    fun getQuestionDetailListByNameQuiz(nameQuiz: String): List<QuestionDetailEntity>

    fun getQuestionDetailList(): List<QuestionDetailEntity>

    fun getProfileFlow(tpovId: Int): Flow<ProfileEntity>

    fun getTpovIdByEmail(email: String): Int

    fun getProfile(tpovId: Int): ProfileEntity

    fun getAllProfiles(): List<ProfileEntity>

    fun getQuizList(tpovId: Int): List<QuizEntity>

    fun getQuizById(id: Int, tpovId: Int): QuizEntity

    fun getQuizEvent(): List<QuizEntity>

    fun getTranslateEvent(): List<QuestionEntity>

    fun getQuizLiveData(tpovId: Int): LiveData<List<QuizEntity>>

    fun getQuestionList(): List<QuestionEntity>

    fun getQuestionListByIdQuiz(id: Int): List<QuestionEntity>

    fun getListApiQuestionBySystemDate(systemDate: String): List<ApiQuestion>

    fun getApiQuestionList(): List<ApiQuestion>

    fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int): Int

    fun getNameQuizByIdQuiz(id: Int): String


    fun deleteQuestionById(id: Int)

    fun deleteChat(time: String)

    fun deleteQuizById(id: Int)

    fun deleteQuestionDetailById(id: Int)


    fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity)

    fun updateQuiz(quizEntity: QuizEntity)

    fun updateApiQuestion(apiQuestion: ApiQuestion)

    fun updateProfile(profile: ProfileEntity)

    fun updateQuestion(questionEntity: QuestionEntity)
}
