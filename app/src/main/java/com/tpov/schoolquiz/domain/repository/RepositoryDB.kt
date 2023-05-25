package com.tpov.schoolquiz.domain.repository

import androidx.lifecycle.LiveData
import com.tpov.schoolquiz.data.database.entities.ApiQuestion
import com.tpov.schoolquiz.data.database.entities.PlayersEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import kotlinx.coroutines.flow.Flow

// TODO: 25.07.2022 LiveData -> Flow
interface RepositoryDB {
    suspend fun insertQuizDetail(questionDetailEntity: QuestionDetailEntity)

    suspend fun insertProfile(profile: ProfileEntity)

    suspend fun insertQuiz(quizEntity: QuizEntity)

    suspend fun insertQuestion(questionEntity: QuestionEntity)

    suspend fun insertListApiQuestion(apiQuestion: List<ApiQuestion>)


    suspend fun getQuestionDetailListByNameQuiz(nameQuiz: String): List<QuestionDetailEntity>

    suspend fun getQuestionDetailList(): List<QuestionDetailEntity>

    fun getProfileFlow(tpovId: Int): Flow<ProfileEntity>

    suspend fun getTpovIdByEmail(email: String): Int

    suspend fun getProfile(tpovId: Int): ProfileEntity

    suspend fun getAllProfiles(): List<ProfileEntity>

    suspend fun getQuizList(tpovId: Int): List<QuizEntity>

    suspend fun getQuizById(id: Int): QuizEntity

    suspend fun getQuizEvent(): List<QuizEntity>

    suspend fun getTranslateEvent(): List<QuestionEntity>

    fun getQuizLiveData(tpovId: Int): LiveData<List<QuizEntity>>

    fun getEventLiveData(): LiveData<List<QuizEntity>>
    suspend fun getQuestionList(): List<QuestionEntity>

    suspend fun getQuestionListByIdQuiz(id: Int): List<QuestionEntity>

    suspend fun getListApiQuestionBySystemDate(systemDate: String): List<ApiQuestion>

    suspend fun getApiQuestionList(): List<ApiQuestion>

    suspend fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int): Int

    suspend fun getNameQuizByIdQuiz(id: Int): String

    suspend fun getPlayersDB(): List<PlayersEntity>

    suspend fun getPlayersDB(tpovId: Int): PlayersEntity


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
