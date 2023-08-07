package com.tpov.schoolquiz.data

import androidx.lifecycle.LiveData
import com.tpov.schoolquiz.data.database.QuizDao
import com.tpov.schoolquiz.data.database.entities.*
import com.tpov.schoolquiz.data.database.log
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject


@InternalCoroutinesApi
class RepositoryDBImpl @Inject constructor(
    private val dao: QuizDao
) : RepositoryDB {
    override fun insertQuizDetail(questionDetailEntity: QuestionDetailEntity) {
        dao.insertQuizDetail(questionDetailEntity)
    }

    override fun insertProfile(profile: ProfileEntity) {
        dao.insertProfile(profile)
    }

    override fun insertQuiz(quizEntity: QuizEntity) {
        dao.insertQuiz(quizEntity)
    }

    override suspend fun insertQuestion(questionEntity: QuestionEntity) {
        dao.insertQuestion(questionEntity)
    }

    override suspend fun getQuestionDetailListByNameQuiz(nameQuiz: String): List<QuestionDetailEntity> {
        return dao.getQuestionDetailListByNameQuiz(nameQuiz)
    }

    override suspend fun getQuizLiveData(tpovId: Int): LiveData<List<QuizEntity>> {
        return dao.getQuizLiveData(tpovId)
    }

    override fun getEventLiveData(): LiveData<List<QuizEntity>> {
        return dao.getEventLiveDataDB()
    }

    override fun getQuestionDetailList(): List<QuestionDetailEntity> {
        return dao.getQuestionDetailList()
    }

    override fun getProfileFlow(tpovId: Int): Flow<ProfileEntity> {
        return dao.getProfileFlow(tpovId)
    }

    override fun getTpovIdByEmail(email: String): Int {
        return dao.getTpovIdByEmail(email)
    }

    override fun getProfile(tpovId: Int): ProfileEntity {
        return dao.getProfile(tpovId)
    }

    override fun getAllProfiles(): List<ProfileEntity> {
        return dao.getAllProfiles()
    }

    override suspend fun getQuizList(tpovId: Int): List<QuizEntity> {
        return dao.getQuizList(tpovId)
    }

    override suspend fun getQuizEvent(): List<QuizEntity> {
        return dao.getQuizEvent()
    }

    override fun getTranslateEvent(): List<QuestionEntity> {
        return dao.getTranslateEvent()
    }

    override fun getQuizById(id: Int): QuizEntity {
        return dao.getQuizById(id)
    }

    override suspend fun getQuestionList(): List<QuestionEntity> {
        return dao.getQuestionList()
    }

    override fun getQuestionListByIdQuiz(id: Int): List<QuestionEntity> {
        return dao.getQuestionByIdQuiz(id)
    }

    override fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int): Int {
        return dao.getIdQuizByNameQuiz(nameQuiz, tpovId) ?: dao.getIdQuizByNameQuiz(nameQuiz, 0)
        ?: 0
    }

    override fun getNameQuizByIdQuiz(id: Int): String {
        return dao.getNameQuizByIdQuiz(id)!!
    }

    override fun getPlayersDB(): List<PlayersEntity> {
        return dao.getPlayersDB()
    }

    override fun getPlayersDB(tpovId: Int): PlayersEntity {
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

    override fun deleteQuestion(id: Int) {
        dao.deleteQuestion(id)
    }

    override fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        dao.updateQuizDetail(questionDetailEntity)
    }

    override fun updateQuiz(quizEntity: QuizEntity) {
        dao.updateQuiz(quizEntity)
    }

    override fun updateProfile(profile: ProfileEntity): Int {

        log("updateProfileCount() updateProfile(): ${profile.pointsNolics}")
        return dao.updateProfiles(profile)
    }

    override fun updateQuestion(questionEntity: QuestionEntity) {
        dao.updateQuestion(questionEntity)
    }
}