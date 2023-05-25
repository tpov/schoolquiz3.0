package com.tpov.schoolquiz.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.tpov.schoolquiz.data.database.entities.*
import com.tpov.schoolquiz.presentation.custom.Logcat
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@Dao

interface QuizDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizDetailDB(note: QuestionDetailEntity)

    suspend fun insertQuizDetail(note: QuestionDetailEntity) {
        log("fun insertQuizDetail $note")
        insertQuizDetailDB(note)
    }


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizDetailListDB(note: List<QuestionDetailEntity>)

    suspend fun insertQuizDetailList(note: List<QuestionDetailEntity>) {
        log("fun insertQuizDetailList $note")
        insertQuizDetailListDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfileDB(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayersList(playersList: List<PlayersEntity>)

    @Query("DELETE FROM table_players")
    suspend fun deletePlayersList()

    suspend fun insertProfile(profile: ProfileEntity) {
        log("fun insertProfile $profile")
        insertProfileDB(profile)
    }

    suspend fun insertQuiz(note: QuizEntity) {
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
    suspend fun insertQuizNewId(note: QuizEntity)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionDB(name: QuestionEntity)

    suspend fun insertQuestion(note: QuestionEntity) {
        log("fun insertQuestion $note")
        insertQuestionDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizListDB(note: List<QuizEntity>)

    suspend fun insertQuizList(note: List<QuizEntity>) {
        log("fun insertQuizList $note")
        insertQuizListDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionListDB(name: List<QuestionEntity>)

    suspend fun insertQuestionList(note: List<QuestionEntity>) {
        log("fun insertQuestionList $note")
        insertQuestionListDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListApiQuestionDB(name: List<ApiQuestion>)

    suspend fun insertListApiQuestion(note: List<ApiQuestion>) {
        log("fun insertListApiQuestion $note")
        insertListApiQuestionDB(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatDB(chatEntity: ChatEntity)

    suspend fun insertChat(chatEntity: ChatEntity) {
        log("fun insertChat $chatEntity")
        insertChatDB(chatEntity)
    }

     fun getProfileFlow(tpovId: Int): Flow<ProfileEntity>  {
        log("fun getProfileFlow tpovId: $tpovId, return: ${getProfileByTpovIdDB(tpovId)}")
        return getProfileFlowDB(tpovId)
    }
    suspend fun getProfile(tpovId: Int): ProfileEntity {
        log("fun getProfile tpovId: $tpovId, return: ${getProfileDB(tpovId)}")
        return getProfileDB(tpovId)
    }
    suspend fun getTpovIdByEmail(email: String): Int {
        log("fun getTpovIdByEmail email: $email, return: ${getTpovIdByEmailDB(email)}")
        return getTpovIdByEmailDB(email)
    }
    suspend fun getProfileByTpovId(tpovId: Int): ProfileEntity {
        log("fun getProfileByTpovId tpovId: $tpovId, return: ${getProfileByTpovIdDB(tpovId)}")
        return getProfileByTpovIdDB(tpovId)
    }
    suspend fun getTpovIdByUid(uid: String?): ProfileEntity {
        log("fun getTpovIdByUid uid: $uid, return: ${getTpovIdByUidDB(uid)}")
        return getTpovIdByUidDB(uid)
    }
    suspend fun getQuizList(tpovId: Int): List<QuizEntity> {
        log("fun getQuizList tpovId: $tpovId, return: ${getQuizListDB(tpovId)}")
        return getQuizListDB(tpovId)
    }

    suspend fun getTranslateEvent(): List<QuestionEntity> {
        log("fun getTranslateEvent return: ${getTranslateEventDB()}")
        return getTranslateEventDB()
    }

    @Query("SELECT * FROM profiles WHERE tpovId LIKE :tpovId")
    fun getProfileFlowDB(tpovId: Int): Flow<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE tpovId LIKE :tpovId")
    suspend fun getProfileDB(tpovId: Int): ProfileEntity

    @Query("SELECT tpovId FROM profiles WHERE login LIKE :email")
    suspend fun getTpovIdByEmailDB(email: String): Int

    @Query("SELECT * FROM profiles WHERE tpovId LIKE :tpovId")
    fun getProfileByTpovIdDB(tpovId: Int): ProfileEntity

    @Query("SELECT * FROM profiles WHERE idFirebase LIKE :uid")
    suspend fun getTpovIdByUidDB(uid: String?): ProfileEntity

    @Query("SELECT * FROM front_list WHERE tpovId LIKE :tpovId")
    fun getQuizListDB(tpovId: Int): List<QuizEntity>

    @Query("""
    SELECT * FROM new_user_table WHERE idQuiz NOT IN (
        SELECT id
        FROM front_list
    )
""")
    suspend fun getTranslateEventDB(): List<QuestionEntity>

    suspend fun getQuizEvent(): List<QuizEntity> {
        log("fun getQuizEvent return: ${getQuizEventDB()}")
        return getQuizEventDB()
    }

    suspend fun getQuestionList(): List<QuestionEntity> {
        log("fun getQuestionList return: ${getQuestionListDB()}")
        return getQuestionListDB()
    }
    suspend fun getQuestionDetailList(): List<QuestionDetailEntity> {
        log("fun getQuestionDetailList return: ${getQuestionDetailListDB()}")
        return getQuestionDetailListDB()
    }
    fun getQuizLiveData(tpovId: Int): LiveData<List<QuizEntity>> {
        log("fun getQuizLiveData, tpovId: $tpovId, return: ${getQuizListDB(tpovId)}")
        return getQuizLiveDataDB(tpovId)
    }
    suspend fun getListApiQuestion(): List<ApiQuestion> {
        log("fun getListApiQuestion return: ${getListApiQuestionDB()}")
        return getListApiQuestionDB()
    }
    suspend fun getQuizById(id: Int): QuizEntity {
        log("fun getQuizById, id: $id return: ${getQuizByIdDB(id)}")
        return getQuizByIdDB(id)
    }
    suspend fun getQuizListIdByTpovId(tpovId: Int): Int {
        log("fun getQuizListIdByTpovId, tpovId: $tpovId, return: ${getQuizListIdByTpovIdDB(tpovId)}")
        return getQuizListIdByTpovIdDB(tpovId)
    }
    suspend fun getQuizTpovIdById(id: Int): Int {
        log("fun getQuizTpovIdById, id: $id, return: ${getQuizTpovIdByIdDB(id)}")
        return getQuizTpovIdByIdDB(id)
    }
    suspend fun getQuestionDetailListByNameQuiz(nameQuiz: String): List<QuestionDetailEntity> {
        log("fun getQuestionDetailListByNameQuiz, nameQuiz: $nameQuiz, return: ${getQuestionDetailListByNameQuizDB(nameQuiz)}")
        return getQuestionDetailListByNameQuizDB(nameQuiz)
    }
    suspend fun getQuestionByIdQuiz(nameQuiz: String): List<QuestionEntity> {
        log("fun getQuestionByIdQuiz, nameQuiz: $nameQuiz, return: ${getQuestionByIdQuizDB(nameQuiz)}")
        return getQuestionByIdQuizDB(nameQuiz)
    }
    suspend fun getListApiQuestionBySystemDate(systemDate: String): List<ApiQuestion> {
        log("fun getListApiQuestionBySystemDate, systemDate: $systemDate, return: ${getListApiQuestionBySystemDateDB(systemDate)}")
        return getListApiQuestionBySystemDateDB(systemDate)
    }
    suspend fun getAllProfilesList(): ProfileEntity {
        log("fun getAllProfilesList, return: ${getAllProfilesListDB()}")
        return getAllProfilesListDB()
    }
    suspend fun getProfileByFirebaseId(id: String): ProfileEntity {
        log("fun getProfileByFirebaseId, id: $id, return: ${getProfileByFirebaseIdDB(id)}")
        return getProfileByFirebaseIdDB(id)
    }
    suspend fun getChat(): Flow<List<ChatEntity>> {
        return getChatDB()
    }
    suspend fun getEventByIdQuiz(id: Int): Int? {
        log("fun getEventByIdQuiz, id: $id, return: ${getEventByIdQuizDB(id)}")
        return getEventByIdQuizDB(id)
    }
    suspend fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int): Int? {
        log("fun getIdQuizByNameQuiz, nameQuiz: $nameQuiz, tpovId: $tpovId, return: ${getIdQuizByNameQuizDB(nameQuiz, tpovId)}")
        return getIdQuizByNameQuizDB(nameQuiz, tpovId)
    }
    suspend fun getNameQuizByIdQuiz(id: Int): String? {
        log("fun getNameQuizByIdQuiz, id: $id, return: ${getNameQuizByIdQuizDB(id)}")
        return getNameQuizByIdQuizDB(id)
    }
    suspend fun getQuestionByIdQuiz(id: Int): List<QuestionEntity> {
        log("fun getQuestionByIdQuiz, id: $id, return: ${getQuestionByIdQuizDB(id)}")
        return getQuestionByIdQuizDB(id)
    }
    suspend fun getQuestionDetailByIdQuiz(id: Int): List<QuestionDetailEntity> {
        log("fun getQuestionDetailByIdQuiz, id: $id, return: ${getQuestionDetailByIdQuizDB(id)}")
        return getQuestionDetailByIdQuizDB(id)
    }

    @Query("SELECT * FROM front_list")
    suspend fun getQuizEventDB(): List<QuizEntity>

    @Query("SELECT * FROM new_user_table")
    suspend fun getQuestionListDB(): List<QuestionEntity>

    @Query("SELECT * FROM table_data")
    suspend fun getQuestionDetailListDB(): List<QuestionDetailEntity>

    @Query("SELECT * FROM table_players")
    suspend fun getPlayersDB(): List<PlayersEntity>

    @Query("SELECT * FROM table_players WHERE id LIKE :tpovId")
    suspend fun getPlayersDB(tpovId: Int): PlayersEntity

    @Query("SELECT * FROM front_list WHERE tpovId LIKE :tpovId")
    fun getQuizLiveDataDB(tpovId: Int): LiveData<List<QuizEntity>>
    @Query("SELECT * FROM front_list")
    fun getEventLiveDataDB(): LiveData<List<QuizEntity>>

    @Query("SELECT * FROM table_generate_question")
    suspend fun getListApiQuestionDB(): List<ApiQuestion>

    @Query("SELECT * FROM front_list WHERE id LIKE :id")
    suspend fun getQuizByIdDB(id: Int): QuizEntity

    @Query("SELECT id FROM front_list WHERE tpovId LIKE :tpovId")
    suspend fun getQuizListIdByTpovIdDB(tpovId: Int): Int

    @Query("SELECT tpovId FROM front_list WHERE id LIKE :id")
    suspend fun getQuizTpovIdByIdDB(id: Int): Int

    @Query("SELECT * FROM table_data WHERE idQuiz LIKE :nameQuiz")
    suspend fun getQuestionDetailListByNameQuizDB(nameQuiz: String): List<QuestionDetailEntity>

    @Query("SELECT * FROM new_user_table WHERE idQuiz LIKE :nameQuiz")
    suspend fun getQuestionByIdQuizDB(nameQuiz: String): List<QuestionEntity>

    @Query("SELECT * FROM table_generate_question WHERE date LIKE :systemDate")
    suspend fun getListApiQuestionBySystemDateDB(systemDate: String): List<ApiQuestion>

    @Query("SELECT * FROM profiles")
    suspend fun getAllProfilesListDB(): ProfileEntity

    @Query("SELECT * FROM profiles WHERE idFirebase = :id")
    suspend fun getProfileByFirebaseIdDB(id: String): ProfileEntity
    @Query("SELECT * FROM profiles")
    suspend fun getAllProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM chat_data ORDER BY id ASC")
    fun getChatDB(): Flow<List<ChatEntity>>

    @Query("SELECT event FROM front_list WHERE id = :id")
    suspend fun getEventByIdQuizDB(id: Int): Int?

    @Query("SELECT id FROM front_list WHERE nameQuiz = :nameQuiz AND tpovId = :tpovId")
    suspend fun getIdQuizByNameQuizDB(nameQuiz: String, tpovId: Int): Int?

    @Query("SELECT nameQuiz FROM front_list WHERE id = :id")
    suspend fun getNameQuizByIdQuizDB(id: Int): String?

    @Query("SELECT * FROM new_user_table WHERE idQuiz = :id")
    suspend fun getQuestionByIdQuizDB(id: Int): List<QuestionEntity>

    @Query("SELECT * FROM table_data WHERE idQuiz = :id")
    suspend fun getQuestionDetailByIdQuizDB(id: Int): List<QuestionDetailEntity>

    @Query("DELETE FROM new_user_table WHERE idQuiz IS :id")
    fun deleteQuestionByIdQuizDB(id: Int)

    fun deleteQuestionByIdQuiz(id: Int) {
        log("fun deleteQuestionByIdQuiz(), idQuiz: $id")
        deleteQuestionByIdQuizDB(id)
    }

    @Query("DELETE FROM table_data WHERE idQuiz IS :id")
    fun deleteQuestionDetailByIdQuiz(id: Int)


    @Query("DELETE FROM chat_data WHERE time LIKE :time")
    fun deleteChat(time: String)

    fun deleteQuizById(id: Int) {
        log("fun deleteQuizById: id: $id")
        deleteQuizByIdDB(id)
    }
    @Query("DELETE FROM front_list WHERE id LIKE :id")
    fun deleteQuizByIdDB(id: Int)

    @Update
    suspend fun updateQuizDetailDB(questionDetailEntity: QuestionDetailEntity)

    suspend fun updateQuizDetail(questionDetailEntity: QuestionDetailEntity) {
        log("updateQuizDetail questionDetailEntity: $questionDetailEntity")
        updateQuizDetailDB(questionDetailEntity)
    }

    @Update
    suspend fun updateQuiz(quizEntity: QuizEntity)

    @Update
    suspend fun updateApiQuestion(generateQuestion: ApiQuestion)

    @Update
    suspend fun updateProfilesDB(profileEntity: ProfileEntity)

    suspend fun updateProfiles(profileEntity: ProfileEntity) {
        log("fun update profile: $profileEntity")
        updateProfilesDB(profileEntity)
    }

    @Update
    suspend fun updateQuestion(questionEntity: QuestionEntity)

    @Query("SELECT COUNT(*) FROM table_data")
    fun getQuestionDetailCount(): Int

    @Query("SELECT COUNT(*) FROM new_user_table")
    fun getQuestionCount(): Int

    @Query("SELECT COUNT(*) FROM front_list")
    fun getQuizCount(): Int

    @Query("SELECT COUNT(*) FROM table_generate_question")
    fun getApiQuestionCount(): Int

    @Query("SELECT COUNT(*) FROM profiles")
    fun getProfileCount(): Int

    @Query("SELECT COUNT(*) FROM chat_data")
    fun getChatCount(): Int

}

@OptIn(InternalCoroutinesApi::class)
fun log(m: String) { Logcat.log(m, "QuizDao", Logcat.LOG_DATABASE)}