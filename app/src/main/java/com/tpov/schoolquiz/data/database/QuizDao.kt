package com.tpov.schoolquiz.data.database

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.room.*
import com.tpov.schoolquiz.data.database.entities.ApiQuestion
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@Dao

interface QuizDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuizDetailDB(note: QuestionDetailEntity)

    fun insertQuizDetail(note: QuestionDetailEntity) {
        log("fun insertQuizDetail $note")
        insertQuizDetailDB(note)
    }


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuizDetailListDB(note: List<QuestionDetailEntity>)

    fun insertQuizDetailList(note: List<QuestionDetailEntity>) {
        log("fun insertQuizDetailList $note")
        insertQuizDetailListDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProfileDB(profile: ProfileEntity)

    fun insertProfile(profile: ProfileEntity) {
        log("fun insertProfile $profile")
        insertProfileDB(profile)
    }

    fun insertQuiz(note: QuizEntity) {
        if (note.id == null) {
            var id = -1
            do {
                id++
                val quiz = getNameQuizByIdQuiz(id)
            } while (quiz != null)
            note.id = id
        }

        log("fun insertQuiz $note")
        this.insertQuizNewId(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuizNewId(note: QuizEntity)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuestionDB(name: QuestionEntity)

    fun insertQuestion(note: QuestionEntity) {
        log("fun insertQuestion $note")
        insertQuestionDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuizListDB(note: List<QuizEntity>)

    fun insertQuizList(note: List<QuizEntity>) {
        log("fun insertQuizList $note")
        insertQuizListDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuestionListDB(name: List<QuestionEntity>)

    fun insertQuestionList(note: List<QuestionEntity>) {
        log("fun insertQuestionList $note")
        insertQuestionListDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertListApiQuestionDB(name: List<ApiQuestion>)

    fun insertListApiQuestion(note: List<ApiQuestion>) {
        log("fun insertListApiQuestion $note")
        insertListApiQuestionDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChatDB(chatEntity: ChatEntity)

    fun insertChat(chatEntity: ChatEntity) {
        log("fun insertChat $chatEntity")
        insertChatDB(chatEntity)
    }

    fun getProfileFlow(tpovId: Int): Flow<ProfileEntity>  {
        log("fun getProfileFlow tpovId: $tpovId, return: ${getProfileByTpovIdDB(tpovId)}")
        return getProfileFlowDB(tpovId)
    }
    fun getProfile(tpovId: Int): ProfileEntity {
        log("fun getProfile tpovId: $tpovId, return: ${getProfileDB(tpovId)}")
        return getProfileDB(tpovId)
    }
    fun getTpovIdByEmail(email: String): Int {
        log("fun getTpovIdByEmail email: $email, return: ${getTpovIdByEmailDB(email)}")
        return getTpovIdByEmailDB(email)
    }
    fun getProfileByTpovId(tpovId: Int): ProfileEntity {
        log("fun getProfileByTpovId tpovId: $tpovId, return: ${getProfileByTpovIdDB(tpovId)}")
        return getProfileByTpovIdDB(tpovId)
    }
    fun getTpovIdByUid(uid: String?): ProfileEntity {
        log("fun getTpovIdByUid uid: $uid, return: ${getTpovIdByUidDB(uid)}")
        return getTpovIdByUidDB(uid)
    }
    fun getQuizList(tpovId: Int): List<QuizEntity> {
        log("fun getQuizList tpovId: $tpovId, return: ${getQuizListDB(tpovId)}")
        return getQuizListDB(tpovId)
    }

    fun getTranslateEvent(): List<QuestionEntity> {
        log("fun getTranslateEvent return: ${getTranslateEventDB()}")
        return getTranslateEventDB()
    }

    @Query("SELECT * FROM profiles WHERE tpovId LIKE :tpovId")
    fun getProfileFlowDB(tpovId: Int): Flow<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE tpovId LIKE :tpovId")
    fun getProfileDB(tpovId: Int): ProfileEntity

    @Query("SELECT tpovId FROM profiles WHERE login LIKE :email")
    fun getTpovIdByEmailDB(email: String): Int

    @Query("SELECT * FROM profiles WHERE tpovId LIKE :tpovId")
    fun getProfileByTpovIdDB(tpovId: Int): ProfileEntity

    @Query("SELECT * FROM profiles WHERE idFirebase LIKE :uid")
    fun getTpovIdByUidDB(uid: String?): ProfileEntity

    @Query("SELECT * FROM front_list WHERE tpovId LIKE :tpovId")
    fun getQuizListDB(tpovId: Int): List<QuizEntity>

    @Query("""
    SELECT * FROM new_user_table WHERE idQuiz NOT IN (
        SELECT id
        FROM front_list
    )
""")
    fun getTranslateEventDB(): List<QuestionEntity>

    fun getQuizEvent(): List<QuizEntity> {
        log("fun getQuizEvent return: ${getQuizEventDB()}")
        return getQuizEventDB()
    }

    fun getQuestionList(): List<QuestionEntity> {
        log("fun getQuestionList return: ${getQuestionListDB()}")
        return getQuestionListDB()
    }
    fun getQuestionDetailList(): List<QuestionDetailEntity> {
        log("fun getQuestionDetailList return: ${getQuestionDetailListDB()}")
        return getQuestionDetailListDB()
    }
    fun getQuizLiveData(tpovId: Int): LiveData<List<QuizEntity>> {
        log("fun getQuizLiveData, tpovId: $tpovId, return: ${getQuizListDB(tpovId)}")
        return getQuizLiveDataDB(tpovId)
    }
    fun getListApiQuestion(): List<ApiQuestion> {
        log("fun getListApiQuestion return: ${getListApiQuestionDB()}")
        return getListApiQuestionDB()
    }
    fun getQuizById(id: Int, tpovId: Int): QuizEntity {
        log("fun getQuizById, id: $id, tpovId: $tpovId, return: ${getQuizById(id, tpovId)}")
        return getQuizByIdDB(id, tpovId)
    }
    fun getQuizListIdByTpovId(tpovId: Int): Int {
        log("fun getQuizListIdByTpovId, tpovId: $tpovId, return: ${getQuizListIdByTpovIdDB(tpovId)}")
        return getQuizListIdByTpovIdDB(tpovId)
    }
    fun getQuizTpovIdById(id: Int): Int {
        log("fun getQuizTpovIdById, id: $id, return: ${getQuizTpovIdByIdDB(id)}")
        return getQuizTpovIdByIdDB(id)
    }
    fun getQuestionDetailListByNameQuiz(nameQuiz: String): List<QuestionDetailEntity> {
        log("fun getQuestionDetailListByNameQuiz, nameQuiz: $nameQuiz, return: ${getQuestionDetailListByNameQuizDB(nameQuiz)}")
        return getQuestionDetailListByNameQuizDB(nameQuiz)
    }
    fun getQuestionByIdQuiz(nameQuiz: String): List<QuestionEntity> {
        log("fun getQuestionByIdQuiz, nameQuiz: $nameQuiz, return: ${getQuestionByIdQuizDB(nameQuiz)}")
        return getQuestionByIdQuizDB(nameQuiz)
    }
    fun getListApiQuestionBySystemDate(systemDate: String): List<ApiQuestion> {
        log("fun getListApiQuestionBySystemDate, systemDate: $systemDate, return: ${getListApiQuestionBySystemDateDB(systemDate)}")
        return getListApiQuestionBySystemDateDB(systemDate)
    }
    fun getAllProfilesList(): ProfileEntity {
        log("fun getAllProfilesList, return: ${getAllProfilesListDB()}")
        return getAllProfilesListDB()
    }
    fun getProfileByFirebaseId(id: String): ProfileEntity {
        log("fun getProfileByFirebaseId, id: $id, return: ${getProfileByFirebaseIdDB(id)}")
        return getProfileByFirebaseIdDB(id)
    }
    fun getChat(): Flow<List<ChatEntity>> {
        log("fun getChat, return: ${getChatDB().asLiveData().value}")
        return getChatDB()
    }
    fun getEventByIdQuiz(id: Int): Int? {
        log("fun getEventByIdQuiz, id: $id, return: ${getEventByIdQuizDB(id)}")
        return getEventByIdQuizDB(id)
    }
    fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int): Int? {
        log("fun getIdQuizByNameQuiz, nameQuiz: $nameQuiz, tpovId: $tpovId, return: ${getIdQuizByNameQuizDB(nameQuiz, tpovId)}")
        return getIdQuizByNameQuizDB(nameQuiz, tpovId)
    }
    fun getNameQuizByIdQuiz(id: Int): String? {
        log("fun getNameQuizByIdQuiz, id: $id, return: ${getNameQuizByIdQuizDB(id)}")
        return getNameQuizByIdQuizDB(id)
    }
    fun getQuestionByIdQuiz(id: Int): List<QuestionEntity> {
        log("fun getQuestionByIdQuiz, id: $id, return: ${getQuestionByIdQuizDB(id)}")
        return getQuestionByIdQuizDB(id)
    }
    fun getQuestionDetailByIdQuiz(id: Int): List<QuestionDetailEntity> {
        log("fun getQuestionDetailByIdQuiz, id: $id, return: ${getQuestionDetailByIdQuizDB(id)}")
        return getQuestionDetailByIdQuizDB(id)
    }

    @Query("SELECT * FROM front_list")
    fun getQuizEventDB(): List<QuizEntity>

    @Query("SELECT * FROM new_user_table")
    fun getQuestionListDB(): List<QuestionEntity>

    @Query("SELECT * FROM table_data")
    fun getQuestionDetailListDB(): List<QuestionDetailEntity>

    @Query("SELECT * FROM front_list WHERE tpovId LIKE :tpovId")
    fun getQuizLiveDataDB(tpovId: Int): LiveData<List<QuizEntity>>

    @Query("SELECT * FROM table_generate_question")
    fun getListApiQuestionDB(): List<ApiQuestion>

    @Query("SELECT * FROM front_list WHERE id LIKE :id AND tpovId LIKE :tpovId") // 50/50
    fun getQuizByIdDB(id: Int, tpovId: Int): QuizEntity

    @Query("SELECT id FROM front_list WHERE tpovId LIKE :tpovId")
    fun getQuizListIdByTpovIdDB(tpovId: Int): Int

    @Query("SELECT tpovId FROM front_list WHERE id LIKE :id")
    fun getQuizTpovIdByIdDB(id: Int): Int

    @Query("SELECT * FROM table_data WHERE idQuiz LIKE :nameQuiz")
    fun getQuestionDetailListByNameQuizDB(nameQuiz: String): List<QuestionDetailEntity>

    @Query("SELECT * FROM new_user_table WHERE idQuiz LIKE :nameQuiz")
    fun getQuestionByIdQuizDB(nameQuiz: String): List<QuestionEntity>

    @Query("SELECT * FROM table_generate_question WHERE date LIKE :systemDate")
    fun getListApiQuestionBySystemDateDB(systemDate: String): List<ApiQuestion>

    @Query("SELECT * FROM profiles")
    fun getAllProfilesListDB(): ProfileEntity

    @Query("SELECT * FROM profiles WHERE idFirebase = :id")
    fun getProfileByFirebaseIdDB(id: String): ProfileEntity

    @Query("SELECT * FROM chat_data")
    fun getChatDB(): Flow<List<ChatEntity>>

    @Query("SELECT event FROM front_list WHERE id = :id")
    fun getEventByIdQuizDB(id: Int): Int?

    @Query("SELECT id FROM front_list WHERE nameQuiz = :nameQuiz AND tpovId = :tpovId")
    fun getIdQuizByNameQuizDB(nameQuiz: String, tpovId: Int): Int?

    @Query("SELECT nameQuiz FROM front_list WHERE id = :id")
    fun getNameQuizByIdQuizDB(id: Int): String?

    @Query("SELECT * FROM new_user_table WHERE idQuiz = :id")
    fun getQuestionByIdQuizDB(id: Int): List<QuestionEntity>

    @Query("SELECT * FROM table_data WHERE idQuiz = :id")
    fun getQuestionDetailByIdQuizDB(id: Int): List<QuestionDetailEntity>

    @Query("DELETE FROM new_user_table WHERE idQuiz IS :id")
    fun deleteQuestionByIdQuiz(id: Int)

    @Query("DELETE FROM table_data WHERE idQuiz IS :id")
    fun deleteQuestionDetailByIdQuiz(id: Int)


    @Query("DELETE FROM chat_data WHERE time LIKE :time")
    fun deleteChat(time: String)

    @Query("DELETE FROM front_list WHERE id LIKE :id")
    fun deleteQuizById(id: Int)

    @Update
    fun updateQuizDetail(questionDetailEntity: QuestionDetailEntity)

    @Update
    fun updateQuiz(quizEntity: QuizEntity)

    @Update
    fun updateApiQuestion(generateQuestion: ApiQuestion)

    @Update
    fun updateProfiles(profileEntity: ProfileEntity)

    @Update
    fun updateQuestion(questionEntity: QuestionEntity)

}

@OptIn(InternalCoroutinesApi::class)
fun log(m: String) { Logcat.log(m, "QuizDao", Logcat.LOG_DATABASE)}