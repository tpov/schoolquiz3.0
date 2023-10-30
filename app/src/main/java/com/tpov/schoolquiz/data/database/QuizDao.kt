package com.tpov.schoolquiz.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.tpov.schoolquiz.data.database.entities.*
import com.tpov.schoolquiz.presentation.core.Logcat
import kotlinx.coroutines.InternalCoroutinesApi

@Dao
interface QuizDao {
    fun insertQuiz(note: QuizEntity) {
        if (note.id == null) {
            var id = -1
            do {
                id++
                val quiz = getNameQuizByIdQuizDB(id)
            } while (quiz != null)
            note.id = id
        }

        log("fun insertQuiz $note")
        this.insertQuizNewId(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuizNewId(note: QuizEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizListDB(note: List<QuizEntity>)

    suspend fun getQuizList(tpovId: Int): List<QuizEntity> {
        log("fun getQuizList tpovId: $tpovId, return: ${getQuizListDB(tpovId)}")
        return getQuizListDB(tpovId)
    }

    @Query("SELECT * FROM front_list WHERE tpovId LIKE :tpovId")
    suspend fun getQuizListDB(tpovId: Int): List<QuizEntity>

    @Query("SELECT * FROM front_list")
    suspend fun getQuizEvent(): List<QuizEntity>

    @Query("SELECT * FROM front_list WHERE tpovId LIKE :tpovId")
    fun getQuizLiveDataDB(tpovId: Int): LiveData<List<QuizEntity>>

    @Query("SELECT * FROM front_list")
    fun getEventLiveDataDB(): LiveData<List<QuizEntity>>

    @Query("SELECT * FROM front_list WHERE id LIKE :id")
    fun getQuizById(id: Int): QuizEntity

    @Query("SELECT id FROM front_list WHERE tpovId LIKE :tpovId")
    fun getQuizListIdByTpovIdDB(tpovId: Int): Int

    @Query("SELECT tpovId FROM front_list WHERE id LIKE :id")
    fun getQuizTpovIdByIdDB(id: Int): Int

    @Query("SELECT event FROM front_list WHERE id = :id")
    fun getEventByIdQuizDB(id: Int): Int?

    @Query("SELECT id FROM front_list WHERE nameQuiz = :nameQuiz AND tpovId = :tpovId")
    fun getIdQuizByNameQuizDB(nameQuiz: String, tpovId: Int): Int?

    @Query("SELECT nameQuiz FROM front_list WHERE id = :id")
    fun getNameQuizByIdQuizDB(id: Int): String?

    fun deleteQuizById(id: Int) {
        log("fun deleteQuizById: id: $id")
        deleteQuizByIdDB(id)
    }

    @Query("DELETE FROM front_list WHERE id LIKE :id")
    fun deleteQuizByIdDB(id: Int)

    @Update
    fun updateQuizDetailDB(questionDetailEntity: QuestionDetailEntity)


    @Update
    fun updateQuiz(quizEntity: QuizEntity)
}

@OptIn(InternalCoroutinesApi::class)
fun log(m: String) {
    Logcat.log(m, "QuizDao", Logcat.LOG_DATABASE)
}