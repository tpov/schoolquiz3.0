package com.tpov.schoolquiz.data

import androidx.lifecycle.LiveData
import com.tpov.schoolquiz.data.database.QuizDao
import com.tpov.schoolquiz.data.database.entities.ApiQuestion
import com.tpov.schoolquiz.data.database.entities.PlayersEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.data.database.log
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@InternalCoroutinesApi
class RepositoryDBImpl @Inject constructor(
    private val dao: QuizDao
) : RepositoryDB {
    override suspend fun insertQuizDetail(questionDetailEntity: QuestionDetailEntity) {
        dao.insertQuizDetail(questionDetailEntity)
    }

    override suspend fun insertProfile(profile: ProfileEntity) {
        dao.insertProfile(profile)
    }

    override suspend fun insertQuiz(quizEntity: QuizEntity) {
        dao.insertQuiz(quizEntity)
    }

    override suspend fun insertQuestion(questionEntity: QuestionEntity) {
        dao.insertQuestion(questionEntity)
    }

    override suspend fun insertListApiQuestion(apiQuestion: List<ApiQuestion>) {
        dao.insertListApiQuestion(apiQuestion)
    }

    override suspend fun getQuestionDetailListByNameQuiz(nameQuiz: String): List<QuestionDetailEntity> {
        return dao.getQuestionDetailListByNameQuiz(nameQuiz)
    }

    override fun getQuizLiveData(tpovId: Int): LiveData<List<QuizEntity>> {
        return dao.getQuizLiveData(tpovId)
    }

    override fun getEventLiveData(): LiveData<List<QuizEntity>> {
        return dao.getEventLiveDataDB()
    }

    override suspend fun getQuestionDetailList(): List<QuestionDetailEntity> {
        return dao.getQuestionDetailList()
    }

    override fun getProfileFlow(tpovId: Int): Flow<ProfileEntity> {

        return dao.getProfileFlow(tpovId)
    }

    override suspend fun getTpovIdByEmail(email: String): Int {
        return dao.getTpovIdByEmail(email)
    }

    override suspend fun getProfile(tpovId: Int): ProfileEntity {
        return dao.getProfile(tpovId)
    }

    override suspend fun getAllProfiles(): List<ProfileEntity> {
        return dao.getAllProfiles()
    }

    override suspend fun getQuizList(tpovId: Int): List<QuizEntity> {
        return dao.getQuizList(tpovId)
    }

    override suspend fun getQuizEvent(): List<QuizEntity> {
        return dao.getQuizEvent()
    }

    override suspend fun getTranslateEvent(): List<QuestionEntity> {
        return dao.getTranslateEvent()
    }

    override suspend fun getQuizById(id: Int): QuizEntity {
        return dao.getQuizById(id)
    }

    override suspend fun getQuestionList(): List<QuestionEntity> {
        return dao.getQuestionList()
    }

    override suspend fun getQuestionListByIdQuiz(id: Int): List<QuestionEntity> {
        return dao.getQuestionByIdQuiz(id)
    }

    override suspend fun getListApiQuestionBySystemDate(systemDate: String): List<ApiQuestion> {
        return dao.getListApiQuestionBySystemDate(systemDate)
    }

    override suspend fun getApiQuestionList(): List<ApiQuestion> {
        return dao.getListApiQuestion()
    }

    override suspend fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int): Int {
        return dao.getIdQuizByNameQuiz(nameQuiz, tpovId) ?: dao.getIdQuizByNameQuiz(nameQuiz, 0)
        ?: 0
    }

    override suspend fun getNameQuizByIdQuiz(id: Int): String {
        return dao.getNameQuizByIdQuiz(id)!!
    }

    override suspend fun getPlayersDB(): List<PlayersEntity> {
        return dao.getPlayersDB()
    }

    override suspend fun getPlayersDB(tpovId: Int): PlayersEntity {
        return dao.getPlayersDB(tpovId)
    }

    override fun deleteQuestionById(id: Int) {
        dao.deleteQuestionByIdQuiz(id)
    }

    override fun deleteChat(time: String) {
        dao.deleteChat(time)
    }

    override fun deleteQuizById(id: Int) {
        dao.deleteQuizById(id)
    }

    override fun deleteQuestionDetailById(id: Int) {
        dao.deleteQuestionDetailByIdQuiz(id)
    }

    override fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) {

        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateQuizDetail(questionDetailEntity)
            }
        }
    }

    override fun updateQuiz(quizEntity: QuizEntity) {
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateQuiz(quizEntity)
            }
        }
    }

    override fun updateApiQuestion(apiQuestion: ApiQuestion) {
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateApiQuestion(apiQuestion)
            }
        }
    }

    override fun updateProfile(profile: ProfileEntity) {

        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                log("updateProfileCount() updateProfile(): ${profile.pointsNolics}")
                dao.updateProfiles(profile)
            }
        }
    }

    override fun updateQuestion(questionEntity: QuestionEntity) {
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateQuestion(questionEntity)
            }
        }
    }
}