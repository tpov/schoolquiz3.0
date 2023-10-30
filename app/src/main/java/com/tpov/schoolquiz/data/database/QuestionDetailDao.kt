package com.tpov.schoolquiz.data.database

import androidx.room.*
import com.tpov.schoolquiz.data.database.entities.*

@Dao
interface QuestionDetailDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertQuestionDetail(note: QuestionDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuestionDetailList(note: List<QuestionDetailEntity>)

    @Query("SELECT * FROM table_data")
    fun getQuestionDetailList(): List<QuestionDetailEntity>

    @Query("SELECT * FROM table_data WHERE idQuiz LIKE :nameQuiz")
    fun getQuestionDetailListByNameQuiz(nameQuiz: String): List<QuestionDetailEntity>

    @Query("SELECT * FROM table_data WHERE idQuiz = :id")
    suspend fun getQuestionDetailByIdQuiz(id: Int): List<QuestionDetailEntity>

    @Query("DELETE FROM new_user_table WHERE idQuiz IS :id")
    fun deleteQuestionByIdQuiz(id: Int)

    @Query("DELETE FROM table_data WHERE idQuiz IS :id")
    fun deleteQuestionDetailByIdQuiz(id: Int)

    @Query("DELETE FROM table_data WHERE idQuiz = :id AND synthFB = :isTrue")
    fun deleteQuestionDetailByIdQuizAndSynth(id: Int, isTrue: Boolean = true)

    @Query("DELETE FROM table_data WHERE id IS :id")
    fun deleteQuestionDetail(id: Int)

    @Update
    fun updateQuizDetail(questionDetailEntity: QuestionDetailEntity)

    @Query("SELECT COUNT(*) FROM table_data")
    fun getQuestionDetailCount(): Int
}