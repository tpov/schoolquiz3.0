package com.tpov.common.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tpov.common.data.model.local.QuestionEntity

@Dao
interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuestionDB(name: QuestionEntity)

    suspend fun insertQuestion(question: QuestionEntity) {
        insertQuestionDB(question)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionListDB(name: List<QuestionEntity>)

    suspend fun insertQuestionList(note: List<QuestionEntity>) {
        insertQuestionListDB(note)
    }

    suspend fun getQuestionList(): List<QuestionEntity> {
        return getQuestionListDB()
    }

    suspend fun getQuestionByIdQuiz(nameQuiz: String): List<QuestionEntity> {
        return getQuestionByIdQuizDB(nameQuiz)
    }

    fun getQuestionByIdQuiz(id: Int): List<QuestionEntity> {
        return getQuestionByIdQuizDB(id)
    }

    @Query("SELECT * FROM question_entity")
    suspend fun getQuestionListDB(): List<QuestionEntity>

    @Query("SELECT * FROM question_entity WHERE idQuiz LIKE :nameQuiz")
    suspend fun getQuestionByIdQuizDB(nameQuiz: String): List<QuestionEntity>

    @Query("SELECT * FROM question_entity WHERE idQuiz = :id")
    fun getQuestionByIdQuizDB(id: Int): List<QuestionEntity>

    @Query("DELETE FROM question_entity WHERE idQuiz IS :id")
    fun deleteQuestionByIdQuizDB(id: Int)

    fun deleteQuestionByIdQuiz(id: Int) {
        deleteQuestionByIdQuizDB(id)
    }

    @Query("DELETE FROM question_entity WHERE id IS :id")
    fun deleteQuestion(id: Int)

    @Update
    fun updateQuestion(questionEntity: QuestionEntity)

    @Query("SELECT COUNT(*) FROM question_entity")
    fun getQuestionCount(): Int
}