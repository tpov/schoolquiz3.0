package com.tpov.schoolquiz.data.database

import androidx.room.*
import com.tpov.schoolquiz.data.database.entities.*

@Dao
interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuestionDB(name: QuestionEntity)

    suspend fun insertQuestion(note: QuestionEntity) {
        insertQuestionDB(note)
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

    @Query("SELECT * FROM new_user_table")
    suspend fun getQuestionListDB(): List<QuestionEntity>

    @Query("SELECT * FROM new_user_table WHERE idQuiz LIKE :nameQuiz")
    suspend fun getQuestionByIdQuizDB(nameQuiz: String): List<QuestionEntity>

    @Query("SELECT * FROM new_user_table WHERE idQuiz = :id")
    fun getQuestionByIdQuizDB(id: Int): List<QuestionEntity>

    @Query("DELETE FROM new_user_table WHERE idQuiz IS :id")
    fun deleteQuestionByIdQuizDB(id: Int)

    fun deleteQuestionByIdQuiz(id: Int) {
        deleteQuestionByIdQuizDB(id)
    }

    @Query("DELETE FROM table_data WHERE id IS :id")
    fun deleteQuestion(id: Int)

    @Update
    fun updateQuestion(questionEntity: QuestionEntity)

    @Query("SELECT COUNT(*) FROM new_user_table")
    fun getQuestionCount(): Int
    }