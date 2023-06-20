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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlayersList(playersList: List<PlayersEntity>)

    @Query("DELETE FROM table_players")
    fun deletePlayersList()

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
    fun insertChatDB(chatEntity: ChatEntity)

    fun insertChat(chatEntity: ChatEntity) {
        log("fun insertChat $chatEntity")
        insertChatDB(chatEntity)
    }

    fun getProfileFlow(tpovId: Int): Flow<ProfileEntity> {
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
    SELECT new_user_table.*
FROM new_user_table
JOIN front_list ON new_user_table.idQuiz = front_list.id
WHERE front_list.event IN (5, 6, 7, 8)
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

    fun getQuizById(id: Int): QuizEntity {
        log("fun getQuizById, id: $id return: ${getQuizByIdDB(id)}")
        return getQuizByIdDB(id)
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
        log(
            "fun getQuestionDetailListByNameQuiz, nameQuiz: $nameQuiz, return: ${
                getQuestionDetailListByNameQuizDB(
                    nameQuiz
                )
            }"
        )
        return getQuestionDetailListByNameQuizDB(nameQuiz)
    }

    fun getQuestionByIdQuiz(nameQuiz: String): List<QuestionEntity> {
        log("fun getQuestionByIdQuiz, nameQuiz: $nameQuiz, return: ${getQuestionByIdQuizDB(nameQuiz)}")
        return getQuestionByIdQuizDB(nameQuiz)
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
        return getChatDB()
    }

    fun getEventByIdQuiz(id: Int): Int? {
        log("fun getEventByIdQuiz, id: $id, return: ${getEventByIdQuizDB(id)}")
        return getEventByIdQuizDB(id)
    }

    fun getIdQuizByNameQuiz(nameQuiz: String, tpovId: Int): Int? {
        log(
            "fun getIdQuizByNameQuiz, nameQuiz: $nameQuiz, tpovId: $tpovId, return: ${
                getIdQuizByNameQuizDB(
                    nameQuiz,
                    tpovId
                )
            }"
        )
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

    @Query("SELECT * FROM table_players")
    fun getPlayersDB(): List<PlayersEntity>

    @Query("SELECT * FROM table_players WHERE id LIKE :tpovId")
    fun getPlayersDB(tpovId: Int): PlayersEntity

    @Query("SELECT * FROM front_list WHERE tpovId LIKE :tpovId")
    fun getQuizLiveDataDB(tpovId: Int): LiveData<List<QuizEntity>>

    @Query("SELECT * FROM front_list")
    fun getEventLiveDataDB(): LiveData<List<QuizEntity>>

    @Query("SELECT * FROM front_list WHERE id LIKE :id")
    fun getQuizByIdDB(id: Int): QuizEntity

    @Query("SELECT id FROM front_list WHERE tpovId LIKE :tpovId")
    fun getQuizListIdByTpovIdDB(tpovId: Int): Int

    @Query("SELECT tpovId FROM front_list WHERE id LIKE :id")
    fun getQuizTpovIdByIdDB(id: Int): Int

    @Query("SELECT * FROM table_data WHERE idQuiz LIKE :nameQuiz")
    fun getQuestionDetailListByNameQuizDB(nameQuiz: String): List<QuestionDetailEntity>

    @Query("SELECT * FROM new_user_table WHERE idQuiz LIKE :nameQuiz")
    fun getQuestionByIdQuizDB(nameQuiz: String): List<QuestionEntity>

    @Query("SELECT * FROM profiles")
    fun getAllProfilesListDB(): ProfileEntity

    @Query("SELECT * FROM profiles WHERE idFirebase = :id")
    fun getProfileByFirebaseIdDB(id: String): ProfileEntity

    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM chat_data ORDER BY id ASC")
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
    fun deleteQuestionByIdQuizDB(id: Int)

    fun deleteQuestionByIdQuiz(id: Int) {
        log("fun deleteQuestionByIdQuiz(), idQuiz: $id")
        deleteQuestionByIdQuizDB(id)
    }

    @Query("DELETE FROM table_data WHERE idQuiz IS :id")
    fun deleteQuestionDetailByIdQuiz(id: Int)

    @Query("DELETE FROM table_data WHERE id IS :id")
    fun deleteQuestion(id: Int)

    @Query("DELETE FROM chat_data WHERE time LIKE :time")
    fun deleteChat(time: String)

    fun deleteQuizById(id: Int) {
        log("fun deleteQuizById: id: $id")
        deleteQuizByIdDB(id)
    }

    @Query("DELETE FROM front_list WHERE id LIKE :id")
    fun deleteQuizByIdDB(id: Int)

    @Update
    fun updateQuizDetailDB(questionDetailEntity: QuestionDetailEntity)

    fun updateQuizDetail(questionDetailEntity: QuestionDetailEntity) {
        log("updateQuizDetail questionDetailEntity: $questionDetailEntity")
        updateQuizDetailDB(questionDetailEntity)
    }

    @Update
    fun updateQuiz(quizEntity: QuizEntity)

    @Update
    fun updateProfilesDB(profileEntity: ProfileEntity)

    fun updateProfiles(profileEntity: ProfileEntity) {
        log("fun update profile: $profileEntity")
        updateProfilesDB(profileEntity)
    }

    @Update
    fun updateQuestion(questionEntity: QuestionEntity)

    @Query("SELECT COUNT(*) FROM table_data")
    fun getQuestionDetailCount(): Int

    @Query("SELECT COUNT(*) FROM new_user_table")
    fun getQuestionCount(): Int

    @Query("SELECT COUNT(*) FROM front_list")
    fun getQuizCount(): Int

    @Query("SELECT COUNT(*) FROM profiles")
    fun getProfileCount(): Int

    @Query("SELECT COUNT(*) FROM chat_data")
    fun getChatCount(): Int


}

@OptIn(InternalCoroutinesApi::class)
fun log(m: String) {
    Logcat.log(m, "QuizDao", Logcat.LOG_DATABASE)
}