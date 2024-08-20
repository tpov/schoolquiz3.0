package com.tpov.common.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.local.QuizEntity

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

        this.insertQuizNewId(note)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuizNewId(note: QuizEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizListDB(note: List<QuizEntity>)

    suspend fun getQuizList(tpovId: Int): List<QuizEntity> {
        return getQuizListDB(tpovId)
    }

    @Query("SELECT * FROM quiz_entity WHERE tpovId LIKE :tpovId")
    suspend fun getQuizListDB(tpovId: Int): List<QuizEntity>

    @Query("SELECT * FROM quiz_entity")
    suspend fun getQuizEvent(): List<QuizEntity>

    @Query("SELECT * FROM quiz_entity WHERE tpovId LIKE :tpovId")
    fun getQuizLiveDataDB(tpovId: Int): LiveData<List<QuizEntity>>

    @Query("SELECT * FROM quiz_entity")
    fun getEventLiveDataDB(): LiveData<List<QuizEntity>>

    @Query("SELECT * FROM quiz_entity")
    fun getQuiz(): List<QuizEntity>

    @Query("SELECT id FROM quiz_entity WHERE tpovId LIKE :tpovId")
    fun getQuizListIdByTpovIdDB(tpovId: Int): Int

    @Query("SELECT tpovId FROM quiz_entity WHERE id LIKE :id")
    fun getQuizTpovIdByIdDB(id: Int): Int

    @Query("SELECT event FROM quiz_entity WHERE id = :id")
    fun getEventByIdQuizDB(id: Int): Int?

    @Query("SELECT id FROM quiz_entity WHERE nameQuiz = :nameQuiz AND tpovId = :tpovId")
    fun getIdQuizByNameQuizDB(nameQuiz: String, tpovId: Int): Int?

    @Query("SELECT nameQuiz FROM quiz_entity WHERE id = :id")
    fun getNameQuizByIdQuizDB(id: Int): String?

    fun deleteQuizById(id: Int) {
        deleteQuizByIdDB(id)
    }

    @Query("DELETE FROM quiz_entity WHERE id LIKE :id")
    fun deleteQuizByIdDB(id: Int)

    @Update
    fun updateQuizDetailDB(questionDetailEntity: QuestionDetailEntity)


    @Update
    fun updateQuiz(quizEntity: QuizEntity)
}
