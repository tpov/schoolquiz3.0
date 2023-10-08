package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.*
import com.tpov.schoolquiz.domain.repository.RepositoryDB
import javax.inject.Inject

class LocalUseCase @Inject constructor(private val repositoryDB: RepositoryDB) {

    fun deleteChat(time: String) = repositoryDB.deleteChat(time)

    fun deleteQuestionByIdQuiz(id: Int) = repositoryDB.deleteQuestionById(id)

    fun deleteQuestionDetailByIdQuiz(id: Int) = repositoryDB.deleteQuestionDetailById(id)

    fun deleteQuestion(id: Int) = repositoryDB.deleteQuestionById(id)

    fun deleteQuiz(id: Int) = repositoryDB.deleteQuizById(id)

    fun getAllProfilesDB() = repositoryDB.getAllProfiles()

    fun getEventLiveData() = repositoryDB.getEventLiveData()

    fun getEventTranslate() = repositoryDB.getTranslateEvent()

    fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int) = repositoryDB.getIdQuizByNameQuiz(nameQuiz, tpovId)

    suspend fun getNameQuizByIdQuiz(id: Int) = repositoryDB.getNameQuizByIdQuiz(id)

    fun getPlayersDB(): List<PlayersEntity> = repositoryDB.getPlayersDB()

    fun getProfileFlow(tpovId: Int) = repositoryDB.getProfileFlow(tpovId)

    fun getProfile(tpovId: Int) = repositoryDB.getProfile(tpovId)

    fun getQuestionDetailList() = repositoryDB.getQuestionDetailList()

    fun getQuestionListByIdQuiz(id: Int) = repositoryDB.getQuestionListByIdQuiz(id)

    suspend fun getQuestionList() = repositoryDB.getQuestionList()

    fun getQuizById(idQuiz: Int) = repositoryDB.getQuizById(idQuiz)

    suspend fun getQuizEvent() = repositoryDB.getQuizEvent()

    suspend fun getQuizLiveData(tpovId: Int) = repositoryDB.getQuizLiveData(tpovId)

    suspend fun getQuizList(tpovId: Int) = repositoryDB.getQuizList(tpovId)

    fun getTpovIdByEmail(email: String) = repositoryDB.getTpovIdByEmail(email)

    fun insertInfoQuestion(questionDetailEntity: QuestionDetailEntity) =
        repositoryDB.insertQuizDetail(questionDetailEntity)

    fun insertProfile(profileEntity: ProfileEntity) =
        repositoryDB.insertProfile(profileEntity)

    suspend fun insertQuestion(questionEntity: QuestionEntity) =
        repositoryDB.insertQuestion(questionEntity)

    fun insertQuiz(quizEntity: QuizEntity) = repositoryDB.insertQuiz(quizEntity)

    fun updateProfile(profileEntity: ProfileEntity) =
        repositoryDB.updateProfile(profileEntity)

    fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) =
        repositoryDB.updateQuestionDetail(questionDetailEntity)

    fun updateQuestion(questionEntity: QuestionEntity) =
        repositoryDB.updateQuestion(questionEntity)

    fun updateQuiz(quizEntity: QuizEntity) = repositoryDB.updateQuiz(quizEntity)
}